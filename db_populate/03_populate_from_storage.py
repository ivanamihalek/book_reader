#! /usr/bin/env python3
import argparse
import hashlib
import os
import sqlite3
import subprocess
from typing import List, Tuple
from mutagen.mp3 import MP3
from mutagen import MutagenError

from peewee import SqliteDatabase, Model, AutoField, CharField, IntegerField, ForeignKeyField, DatabaseError, Proxy

SQLITE_MAGIC = b"SQLite format 3\0"
DB_NAME = 'bookreader.db'
# Setup global  Peewee database proxy - Peewee is so lame
# I don't want to instantiate the db here before checking and
# informing the user that something may be wrong
global_db_proxy = Proxy()



class Book(Model):
    id = AutoField(primary_key=True)  # Autoincremented integer primary key
    title = CharField(unique=True)  # Ensuring title is unique for lookups
    author = CharField()

    class Meta:
        database = global_db_proxy
        table_name = 'books'


class Chapter(Model):
    id = AutoField(primary_key=True)  # Autoincremented integer primary key
    book = ForeignKeyField(Book, backref='chapters', on_delete='CASCADE', column_name='bookId')
    title = CharField()
    fileName = CharField()
    playTime = IntegerField(default=0)
    lastPlayedPosition = IntegerField(default=0)
    lastPlayedTimestamp = IntegerField(default=0)

    class Meta:
        database = global_db_proxy
        table_name = 'chapters'

def is_valid_sqlite_db(path: str, verify_by_connection: bool = True) -> bool:
    """
    Check whether a file exists at *path* and is a valid SQLite3 database.

    The function performs two checks (in order):

    1. **Filesystem check** – the path must exist and point to a regular file.
    2. **SQLite validation** – either:
           * a quick header check using the SQLite file signature, or
           * an actual connection attempt (default).

    Parameters
    ----------
    path: str
        Path to the file that should be inspected.
    verify_by_connection: bool, optional (default=True)
        If ``True`` the function will try to open the file with
        ``sqlite3.connect()`` (using the ``uri`` parameter).  This is the most
        reliable way to verify that the file can actually be used as a SQLite
        database.  If ``False`` only the 16‑byte header check is performed,
        which is faster but does not guarantee that the file can be opened
        by SQLite (e.g., it could be corrupted beyond the header).

    Returns
    -------
    bool
        ``True`` if the file exists and appears to be a valid SQLite3
        database, ``False`` otherwise.

    Raises
    ------
    TypeError
        If *path* is not a string.

    Example
    -------
    >>> is_valid_sqlite_db('my_data.db')
    True
    >>> is_valid_sqlite_db('not_a_db.txt')
    False
    """
    # ------------------------------------------------------------------
    # 1️⃣  Basic type validation
    # ------------------------------------------------------------------
    if not isinstance(path, str):
        raise TypeError("path must be a string")

    # ------------------------------------------------------------------
    # 2️⃣  Does the file exist and is it a regular file?
    # ------------------------------------------------------------------
    if not os.path.exists(path):
        print(f"File '{path}' does not exist")
        return False
    if not os.path.isfile(path):
        print(f"File '{path}' is not a regular file")
        return False

    # ------------------------------------------------------------------
    # 3️⃣  Quick header validation (SQLite files start with this byte
    #     sequence).  This is optional but cheap and catches many obvious
    #     non‑SQLite files.
    # ------------------------------------------------------------------

    try:
        with open(path, "rb") as f:
            header = f.read(16)          # first 16 bytes
    except OSError as e:
        # Could not read the file (permissions, I/O error, etc.)
        print(f"File '{path}' could not be opened: {e}")
        return False

    if header != SQLITE_MAGIC:
        print(f"File '{path}' does not appear to be an SQLite3 database")
        return False

    # ------------------------------------------------------------------
    # 4️⃣  Full validation via a connection attempt?
    # ------------------------------------------------------------------
    if verify_by_connection:
        try:
            # The `uri=True` flag allows us to open the file even if the
            # path contains characters that would otherwise be interpreted
            # as SQLite URI parameters.
            conn = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
            # If we got here, SQLite accepted the file.  Close it promptly.
            conn.close()
        except (sqlite3.DatabaseError, OSError):
            print(f"Connection with '{path}' could not be established")
            return False

    # If we reach this point, everything looks good.
    return True


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


def validate_mp3_files(directory_path: str) -> List[str]:
    """Validate and return list of mp3 files in the directory, checking for true MP3 format."""
    mp3_files = []
    for file in os.listdir(directory_path):
        if not file.endswith('.mp3'):
            print(f"Warning: Non-MP3 file found: {file}")
            continue
        # if not re.match(r'^\d+\.mp3$', file):
        #     print(f"Warning: MP3 file with invalid naming format: {file}")
        #     continue

        # Check if it's a true MP3 file by reading the header
        file_path = os.path.join(directory_path, file)
        try:
            with open(file_path, 'rb') as f:
                header = f.read(3)
                if header.startswith(b'ID3'):
                    mp3_files.append(file)
                else:
                    f.seek(0)
                    first_two_bytes = f.read(2)
                    if len(first_two_bytes) == 2 and first_two_bytes[0] == 0xFF and (first_two_bytes[1] & 0xE0) == 0xE0:
                        mp3_files.append(file)
                    else:
                        print(f"Warning: File {file} does not appear to be a valid MP3 (invalid header)")
        except Exception as e:
            print(f"Warning: Could not read file {file} to validate MP3 format: {e}")
            continue

    mp3_files.sort()
    return mp3_files


def check_adb_device(device_name) -> bool:
    """Check if an ADB device is available."""
    try:
        result = subprocess.run(['adb', 'devices'], capture_output=True, text=True)
        result_lines = result.stdout.splitlines()
        if len(result_lines) == 0:
            print("No ADB devices found")
            return False
        elif len([l for l in result_lines if device_name in l]) == 0:
            print(f"Device '{device_name}' not found among ADB devices")
            return False
    except Exception as e:
        print(f"Error checking ADB devices: {e}")
        return False

    return True

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


def calculate_md5(file_path: str) -> str:
    """Calculate MD5 checksum of a local file."""
    hash_md5 = hashlib.md5()
    try:
        with open(file_path, 'rb') as f:
            for chunk in iter(lambda: f.read(4096), b""):
                hash_md5.update(chunk)
        return hash_md5.hexdigest()
    except Exception as e:
        print(f"Error calculating MD5 for local file {file_path}: {e}")
        return ""



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

def get_mp3_duration_in_ms(mp3_path: str) -> int:
    """
    Returns the playback duration of an MP3 file in milliseconds.

    This function reads the MP3 file header and extracts duration information
    using the `mutagen` library. It raises descriptive exceptions for common
    failure cases such as missing files, incorrect file types, or unreadable files.

    Parameters:
        mp3_path (str): The absolute or relative path to the MP3 file.

    Returns:
        int: The total duration of the MP3 file, in milliseconds.

    Raises:
        FileNotFoundError: If the file does not exist.
        ValueError: If the file extension is not `.mp3`.
        RuntimeError: If the file cannot be read or parsed for duration.
    """
    if not os.path.exists(mp3_path):
        raise FileNotFoundError(f"The file '{mp3_path}' does not exist.")

    if not mp3_path.lower().endswith(".mp3"):
        raise ValueError(f"The file '{mp3_path}' is not an MP3 file.")

    try:
        audio = MP3(mp3_path)
        duration_seconds = audio.info.length
        duration_ms = int(duration_seconds * 1000)
        return duration_ms
    except MutagenError as e:
        raise RuntimeError(f"Failed to read MP3 metadata for '{mp3_path}': {e}")
    except Exception as e:
        raise RuntimeError(f"An unexpected error occurred while processing '{mp3_path}': {e}")



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