#! /usr/bin/env python3
import argparse
import os
import subprocess
from typing import Tuple

from peewee import SqliteDatabase, DatabaseError, Proxy

from models import Book, Chapter
from utils import check_adb_device, is_valid_sqlite_db, validate_mp3_files, calculate_md5, get_mp3_duration_in_ms

DB_NAME = 'bookreader.db'
# Setup global  Peewee database proxy - Peewee is so lame
# I don't want to instantiate the db here before checking and
# informing the user that something may be wrong
global_db_proxy = Proxy()


def parse_directory_name(directory_path: str) -> Tuple[str, str]:
    """Parse the last segment of the directory path into book title and author."""
    last_segment = os.path.basename(directory_path)
    parts = last_segment.split('_')
    if len(parts) != 2:
        raise ValueError(f"Invalid directory name format: {last_segment}. Expected format: book-title_author-name")

    def capitalize_words(text: str) -> str:
        return ' '.join(word.capitalize() for word in text.split('-'))

    book_title = capitalize_words(parts[0])
    author_name = capitalize_words(parts[1])
    return book_title, author_name



def create_device_directory(device_name: str,  book_title: str, dry_run: bool = False) -> str:
    """Create a directory on the device for the book."""
    book_dir_name = book_title.replace(' ', '')
    device_path = f"/sdcard/Audiobooks/BookReader/audio/{book_dir_name}"
    if not dry_run:
        try:
            subprocess.run(['adb','-s', device_name,  'shell', 'mkdir', '-p', device_path], check=True)
            print(f"Created directory on device: {device_path}")
        except subprocess.CalledProcessError as e:
            print(f"Failed to create directory on device {device_name}: {e}")
            exit(1)
    else:
        print(f"[Dry Run] Would create directory on device {device_name}: {device_path}")
    return device_path




def get_device_md5(device_name: str,  device_path: str) -> str:
    """Get MD5 checksum of a file on the device using ADB."""
    try:
        result = subprocess.run(['adb', '-s', device_name, 'shell', 'md5sum', device_path], capture_output=True, text=True)
        if result.returncode == 0 and result.stdout:
            # md5sum output format: <hash>  <path>
            parts = result.stdout.strip().split()
            if len(parts) > 0:
                return parts[0]
        return ""
    except Exception as e:
        print(f"Error calculating MD5 for device file {device_path}: {e}")
        return ""


def table_exists(db: SqliteDatabase, table_name: str) -> bool:
    """Check if a table exists in the database."""
    try:
        query = db.execute_sql("SELECT name FROM sqlite_master WHERE type='table' AND name=?;", (table_name,))
        return bool(query.fetchone())
    except DatabaseError as e:
        print(f"Error checking if table {table_name} exists: {e}")
        return False


def store_book_in_db(db: SqliteDatabase, title: str, author: str, dry_run: bool = False) -> int:
    """Store book details in the database and return the book ID."""
    if dry_run:
        print(f"[Dry Run] Would store book in database: {title} by {author}")
        return -1  # Dummy ID for dry run
    else:
        try:
            with db.atomic():
                book, created = Book.get_or_create(title=title, defaults={'author': author})
                if created:
                    print(f"Stored book in database: {title} by {author} with ID {book.id}")
                else:
                    print(f"Book already exists in database: {title} by {author} with ID {book.id}")
                return book.id
        except Exception as e:
            print(f"Error storing book in database: {e}")
            raise


def store_chapter_in_db(db: SqliteDatabase, book: Book, title: str, file_name: str, play_time: int,
                        dry_run: bool = False) -> None:
    """Store chapter details in the database."""
    if not dry_run:
        try:
            with db.atomic():
                # Check if chapter already exists for this book with the same fileName
                chapter, created = Chapter.get_or_create(
                    book=book,
                    fileName=file_name,
                    defaults={
                        'title': title,
                        'playTime': play_time,
                        'lastPlayedPosition': 0,
                        'lastPlayedTimestamp': 0
                    }
                )
                if created:
                    print(f"Stored chapter in database: {title} ({file_name}) with ID {chapter.id}")
                else:
                    # Update playTime if chapter already exists
                    if chapter.playTime != play_time:
                        print(f"Updated playTime for existing chapter: {title} ({file_name}) with ID {chapter.id} "
                              f"- old: {chapter.playTime}ms, new: {play_time}ms")
                        chapter.playTime = play_time
                        chapter.save()
                    else:
                        print(f"Chapter already exists with same playTime ({play_time}ms): {title} ({file_name}) with ID {chapter.id}")
        except Exception as e:
            print(f"Error storing chapter in database: {e}")
            raise
    else:
        print(f"[Dry Run] Would store chapter in database: {title} ({file_name}; "
              f"play time {play_time}ms ({play_time/60000:.2f}min))")


def copy_file_to_device(device_name: str, local_path: str, device_path: str, dry_run: bool = False) -> None:
    """Copy a file to the device using ADB if MD5 checksums differ."""
    if not dry_run:
        # Calculate local MD5
        local_md5 = calculate_md5(local_path)
        if not local_md5:
            print(f"Failed to calculate local MD5 for {local_path}, proceeding with upload.")
        else:
            # Get device MD5 (if file exists)
            device_md5 = get_device_md5(device_name, device_path)
            if device_md5 and local_md5 == device_md5:
                print(f"Skipped upload: File {local_path} already exists on device {device_name} with matching MD5 ({local_md5})")
                return
            elif device_md5:
                print(f"MD5 mismatch: Local {local_md5} vs Device {device_md5}, uploading {local_path}")
            else:
                print(f"No file or MD5 on device {device_name} for {device_path}, uploading {local_path}")

        # Proceed with upload if MD5 differs or file doesn't exist on device
        try:
            subprocess.run(['adb', '-s', device_name, 'push', local_path, device_path], check=True)
            print(f"Copied file to device: {local_path} -> {device_path}")
        except subprocess.CalledProcessError as e:
            print(f"Failed to copy file to device {device_name}: {e}")
            exit(1)
    else:
        print(f"[Dry Run] Would copy file to device: {local_path} -> {device_path}")


def main():

    parser = argparse.ArgumentParser(description="Process audiobook files and store them on an Android device.")
    parser.add_argument('directory', type=str, help="Path to the directory containing audiobook MP3 files")
    helpstr = "The name of the device on which to operate. See 'shell> adb devices' if unsure."
    parser.add_argument('device', type=str, help=helpstr)
    parser.add_argument('--dry-run', action='store_true', help="Simulate actions without executing them")
    args = parser.parse_args()

    directory_path = os.path.abspath(args.directory)
    dry_run = args.dry_run

    if not os.path.isdir(directory_path):
        print(f"Error: Directory does not exist: {directory_path}")
        exit(1)

    device_name = args.device
    # Check for ADB device
    if not check_adb_device(device_name):
        print(f"Error: Device '{device_name}' not found. Exiting.")
        exit(1)
    print(f"Found device '{device_name}' running.")

    if not is_valid_sqlite_db(DB_NAME, verify_by_connection= True):
        print(f"Error: Something is wrong with the sqlite3 database '{DB_NAME}' (is this the full path?).  Exiting.")
        exit(1)
    print(f"Database '{DB_NAME}' ok.")

    db = None
    try:
        # Parse book title and author from directory name
        book_title, author_name = parse_directory_name(directory_path)

        # Validate MP3 files
        mp3_files = validate_mp3_files(directory_path)
        if not mp3_files:
            print("Error: No valid MP3 files found in directory.")
            exit(1)

        print(f"found {len(mp3_files)} MP3 files in directory: {directory_path}")

        # Create directory on device
        print(book_title)
        device_book_dir = create_device_directory(device_name, book_title, dry_run)

        # Connect to database and check tables
        print("Connecting to database...")
        db = SqliteDatabase(DB_NAME)
        global_db_proxy.initialize(db)
        db.connect()

        # Only create tables if they don't exist, avoiding index creation if already present
        if not table_exists(db, 'books') or not table_exists(db, 'chapters'):
            print("One or more tables missing. Creating tables...")
            db.create_tables([Book, Chapter], safe=True)
            print("Database tables created.")
        else:
            print("Database tables already exist. Skipping table creation to avoid new index creation.")

        # Store book in database and get its ID
        book_id = store_book_in_db(db, book_title, author_name, dry_run)
        book = Book.get(Book.id == book_id) if not dry_run and book_id != -1 else None

        # Process each MP3 file
        for mp3_file in mp3_files:
            local_file_path = os.path.join(directory_path, mp3_file)
            device_file_path = os.path.join(device_book_dir, mp3_file)
            chapter_title = os.path.splitext(mp3_file)[0]
            play_time = get_mp3_duration_in_ms(local_file_path)

            # Copy file to device (with MD5 check)
            copy_file_to_device(device_name, local_file_path, device_file_path, dry_run)
            store_chapter_in_db(db, book, chapter_title, mp3_file, play_time, dry_run)

        # Verify database contents (for debugging)
        if not dry_run:
            print("\nVerifying database contents:")
            print(f"Books in database: {Book.select().count()}")
            for book in Book.select():
                print(f" - Book: {book.title} by {book.author} (ID: {book.id})")
            print(f"Chapters in database: {Chapter.select().count()}")
            for chapter in Chapter.select():
                print(f" - Chapter: {chapter.title} ({chapter.fileName}) for Book ID: {chapter.book.id}")

    except Exception as e:
        print(f"Error: {e}")

    finally:
        if db is not None and not db.is_closed():
            db.close()
            print("Database connection closed.")


if __name__ == "__main__":
    main()