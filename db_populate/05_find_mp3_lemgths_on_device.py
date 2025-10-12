#!/usr/bin/env python3
import os
import subprocess
import tempfile
import argparse
from typing import Tuple, Optional, Any
from mutagen.mp3 import MP3
from mutagen.mp3 import HeaderNotFoundError
from models import Book, Chapter

# Peewee imports
from peewee import (
    SqliteDatabase, Model, AutoField, ForeignKeyField, CharField, IntegerField,
    Proxy, IntegrityError
)

from settings import DEVICE_BASE_AUDIO_PATH, DATABASE_PATH, global_db_proxy
from utils import check_adb_connection, is_valid_sqlite_db, get_mp3_duration_in_ms



# --- Utility Functions ---

def _pull_and_get_duration_in_ms(device_path: str) -> Tuple[Optional[int], Optional[str]]:
    """
    Pulls an audio file from an Android device via ADB to a temporary
    location, gets its duration, and then cleans up.

    Args:
        device_path: The full path to the file on the Android device.

    Returns:
        A tuple: (duration_in_seconds, error_message). duration_in_seconds is
        None if an error occurred.
    """
    # Create a unique temporary file path
    # We use mkstemp to ensure the filename is unique and secure, then close the handle.
    fd, temp_mp3_path = tempfile.mkstemp(suffix=".mp3")
    os.close(fd)  # Close the file descriptor, as we only need the path

    try:
        # Step 1: Use 'adb pull' to copy the file
        print(f"  -> Pulling '{device_path}'...")
        pull_command = ['adb', 'pull', device_path, temp_mp3_path]

        # Run the adb pull command
        subprocess.run(
            pull_command,
            check=True,
            capture_output=True,
            text=True,
            timeout=30  # Add a timeout for safety
        )

        # Step 2: Use mutagen to get the duration from the local temp file
        duration_in_ms = get_mp3_duration_in_ms(temp_mp3_path)

        return duration_in_ms, None  # Success

    except subprocess.CalledProcessError as e:
        # This error often means the file was not found on the device
        error_message = e.stderr.strip() or "ADB command failed."
        if "does not exist" in error_message:
            return None, f"File not found on device at '{device_path}'."
        return None, f"ADB Error: {error_message}"
    except HeaderNotFoundError:
        return None, "File is not a valid MP3 file or metadata is corrupt."
    except Exception as e:
        return None, f"An unexpected error occurred during processing: {e}"
    finally:
        # Step 3: Clean up by deleting the temporary file
        if os.path.exists(temp_mp3_path):
            os.remove(temp_mp3_path)
            print(f"  -> Cleaned up temporary file: {os.path.basename(temp_mp3_path)}")


def process_chapter(chapter: Chapter, is_dry_run: bool) -> None:
    """
    Constructs the device path, gets the MP3 duration via ADB, and updates
    the chapter record in the database.

    Args:
        chapter: The Chapter model instance to process.
        is_dry_run: If True, only reports actions without modifying the database.
    """
    book_title = chapter.book.title
    book_dir_name = book_title.replace(' ', '')

    # Construct the full device path
    device_path = os.path.join(
        DEVICE_BASE_AUDIO_PATH,
        book_dir_name,
        chapter.fileName
    ).replace('\\', '/')  # Ensure forward slashes for ADB path on all OS

    print(f"\nProcessing Chapter ID {chapter.id}: '{chapter.title}' ({chapter.fileName})")

    if is_dry_run:
        print(f"  [DRY RUN] Would process this file:")
        print(f"    - Device Path: {device_path}")
        print(f"    - Current DB Row: playTime={chapter.playTime}, "
            f"lastPlayedPosition={chapter.lastPlayedPosition}, "
            f"finishedPlaying={chapter.finishedPlaying}")
        return

    # Call the helper to pull and measure the duration
    duration_in_ms, error = _pull_and_get_duration_in_ms(device_path)

    if error:
        print(f"  [ERROR] Failed to get duration: {error}")
        return

    if duration_in_ms is None:
        print("  [ERROR] Duration could not be calculated.")
        return


    # Calculate finishedPlaying status
    finished = 0
    if chapter.lastPlayedPosition > 0 and duration_in_ms > 0 and \
            chapter.lastPlayedPosition >= (duration_in_ms * 0.95):
        finished = 1

    # Update the database record
    Chapter.update(
        playTime=duration_in_ms,
        finishedPlaying=finished
    ).where(Chapter.id == chapter.id).execute()

    minutes = duration_in_ms // 60000
    seconds = duration_in_ms % 60000

    print(f"  [SUCCESS] Duration found: {minutes:02d}:{seconds:02d} ({duration_in_ms}ms)")
    print(f"  [UPDATE] Updated playTime to {duration_in_ms}s "
        f"and finishedPlaying to {finished} "
        f"(last played: {chapter.lastPlayedPosition}s).")



def main() -> None:
    """
    Main entry point of the script. Handles argument parsing, database setup,
    ADB connectivity check, and chapter processing.
    """
    parser = argparse.ArgumentParser(
        description="Reads MP3 durations from an Android device via ADB and updates a Peewee SQLite database."
    )
    parser.add_argument(
        '--dry-run',
        action='store_true',
        help="Report which files would be processed and the current database data, but do not pull files or update the database."
    )
    args = parser.parse_args()

    print("--- ADB Audiobook Duration Updater ---")

    # 1. ADB Connection Check
    adb_error = check_adb_connection()
    if adb_error:
        print(f"\n[FATAL] {adb_error}")
        return
    print("\nADB connection OK.")

    # 2. Database Setup
    if not is_valid_sqlite_db(DATABASE_PATH, verify_by_connection= True):
        print(f"Error: Something is wrong with the sqlite3 database '{DATABASE_PATH}' (is this the full path?).  Exiting.")
        exit(1)
    print(f"Database '{DATABASE_PATH}' ok.")

    db = SqliteDatabase(DATABASE_PATH)
    global_db_proxy.initialize(db)


    # 3. Main Processing Loop
    try:
        db.connect()

        # Iterate over all chapters and pre-fetch the related book to avoid N+1 queries
        # We also need to get the records where playTime is 0 to simulate the initial run
        chapters_to_process = Chapter.select().join(Book)
        print(f"\nFound {len(chapters_to_process)} chapters to check/process.")

        for chapter in chapters_to_process:
            process_chapter(chapter, args.dry_run)

    except Exception as e:
        print(f"\n[CRITICAL ERROR] An error occurred during database operation: {e}")
    finally:
        if not db.is_closed():
            db.close()

    print("\n--- Processing Complete ---")


if __name__ == '__main__':
    main()
