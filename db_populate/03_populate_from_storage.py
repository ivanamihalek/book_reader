#! /usr/bin/env python3
import os
import sys
import argparse
import re
import hashlib
from peewee import SqliteDatabase, Model, AutoField, CharField, IntegerField, ForeignKeyField, DatabaseError
from pathlib import Path
import subprocess
from typing import List, Tuple

# Setup Peewee database connection
db = SqliteDatabase('bookreader.db')


class Book(Model):
    id = AutoField(primary_key=True)  # Autoincremented integer primary key
    title = CharField(unique=True)  # Ensuring title is unique for lookups
    author = CharField()

    class Meta:
        database = db
        table_name = 'books'


class Chapter(Model):
    id = AutoField(primary_key=True)  # Autoincremented integer primary key
    book = ForeignKeyField(Book, backref='chapters', on_delete='CASCADE', column_name='bookId')
    title = CharField()
    fileName = CharField()
    lastPlayedPosition = IntegerField(default=0)
    lastPlayedTimestamp = IntegerField(default=0)

    class Meta:
        database = db
        table_name = 'chapters'


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


def check_adb_device() -> bool:
    """Check if an ADB device is available."""
    try:
        result = subprocess.run(['adb', 'devices'], capture_output=True, text=True)
        return len(result.stdout.strip().splitlines()) > 1
    except Exception as e:
        print(f"Error checking ADB devices: {e}")
        return False


def create_device_directory(book_title: str, dry_run: bool = False) -> str:
    """Create a directory on the device for the book."""
    book_dir_name = book_title.replace(' ', '')
    device_path = f"/sdcard/Audiobooks/BookReader/audio/{book_dir_name}"
    if not dry_run:
        try:
            subprocess.run(['adb', 'shell', 'mkdir', '-p', device_path], check=True)
            print(f"Created directory on device: {device_path}")
        except subprocess.CalledProcessError as e:
            print(f"Failed to create directory on device: {e}")
            sys.exit(1)
    else:
        print(f"[Dry Run] Would create directory on device: {device_path}")
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


def get_device_md5(device_path: str) -> str:
    """Get MD5 checksum of a file on the device using ADB."""
    try:
        result = subprocess.run(['adb', 'shell', 'md5sum', device_path], capture_output=True, text=True)
        if result.returncode == 0 and result.stdout:
            # md5sum output format: <hash>  <path>
            parts = result.stdout.strip().split()
            if len(parts) > 0:
                return parts[0]
        return ""
    except Exception as e:
        print(f"Error calculating MD5 for device file {device_path}: {e}")
        return ""


def table_exists(table_name: str) -> bool:
    """Check if a table exists in the database."""
    try:
        query = db.execute_sql("SELECT name FROM sqlite_master WHERE type='table' AND name=?;", (table_name,))
        return bool(query.fetchone())
    except DatabaseError as e:
        print(f"Error checking if table {table_name} exists: {e}")
        return False


def store_book_in_db(title: str, author: str, dry_run: bool = False) -> int:
    """Store book details in the database and return the book ID."""
    if not dry_run:
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
    else:
        print(f"[Dry Run] Would store book in database: {title} by {author}")
        return -1  # Dummy ID for dry run


def store_chapter_in_db(book: Book, title: str, file_name: str, dry_run: bool = False) -> None:
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
                        'lastPlayedPosition': 0,
                        'lastPlayedTimestamp': 0
                    }
                )
                if created:
                    print(f"Stored chapter in database: {title} ({file_name}) with ID {chapter.id}")
                else:
                    print(f"Chapter already exists in database: {title} ({file_name}) with ID {chapter.id}")
        except Exception as e:
            print(f"Error storing chapter in database: {e}")
            raise
    else:
        print(f"[Dry Run] Would store chapter in database: {title} ({file_name})")


def copy_file_to_device(local_path: str, device_path: str, dry_run: bool = False) -> None:
    """Copy a file to the device using ADB if MD5 checksums differ."""
    if not dry_run:
        # Calculate local MD5
        local_md5 = calculate_md5(local_path)
        if not local_md5:
            print(f"Failed to calculate local MD5 for {local_path}, proceeding with upload.")
        else:
            # Get device MD5 (if file exists)
            device_md5 = get_device_md5(device_path)
            if device_md5 and local_md5 == device_md5:
                print(f"Skipped upload: File {local_path} already exists on device with matching MD5 ({local_md5})")
                return
            elif device_md5:
                print(f"MD5 mismatch: Local {local_md5} vs Device {device_md5}, uploading {local_path}")
            else:
                print(f"No file or MD5 on device for {device_path}, uploading {local_path}")

        # Proceed with upload if MD5 differs or file doesn't exist on device
        try:
            subprocess.run(['adb', 'push', local_path, device_path], check=True)
            print(f"Copied file to device: {local_path} -> {device_path}")
        except subprocess.CalledProcessError as e:
            print(f"Failed to copy file to device: {e}")
            sys.exit(1)
    else:
        print(f"[Dry Run] Would copy file to device: {local_path} -> {device_path}")


def main():
    parser = argparse.ArgumentParser(description="Process audiobook files and store them on an Android device.")
    parser.add_argument('directory', type=str, help="Path to the directory containing audiobook MP3 files")
    parser.add_argument('--dry-run', action='store_true', help="Simulate actions without executing them")
    args = parser.parse_args()

    directory_path = os.path.abspath(args.directory)
    dry_run = args.dry_run

    if not os.path.isdir(directory_path):
        print(f"Error: Directory does not exist: {directory_path}")
        sys.exit(1)

    try:
        # Parse book title and author from directory name
        book_title, author_name = parse_directory_name(directory_path)

        # Validate MP3 files
        mp3_files = validate_mp3_files(directory_path)
        if not mp3_files:
            print("Error: No valid MP3 files found in directory.")
            sys.exit(1)

        # Check for ADB device
        if not check_adb_device():
            print("Warning: No ADB device found. Exiting.")
            sys.exit(1)

        # Create directory on device
        device_book_dir = create_device_directory(book_title, dry_run)

        # Connect to database and check tables
        print("Connecting to database...")
        db.connect()

        # Only create tables if they don't exist, avoiding index creation if already present
        if not table_exists('books') or not table_exists('chapters'):
            print("One or more tables missing. Creating tables...")
            db.create_tables([Book, Chapter], safe=True)
            print("Database tables created.")
        else:
            print("Database tables already exist. Skipping table creation to avoid new index creation.")

        # Store book in database and get its ID
        book_id = store_book_in_db(book_title, author_name, dry_run)
        book = Book.get(Book.id == book_id) if not dry_run and book_id != -1 else None

        # Process each MP3 file
        for mp3_file in mp3_files:
            local_file_path = os.path.join(directory_path, mp3_file)
            device_file_path = os.path.join(device_book_dir, mp3_file)
            chapter_title = os.path.splitext(mp3_file)[0]

            # Copy file to device (with MD5 check)
            copy_file_to_device(local_file_path, device_file_path, dry_run)

            # Store chapter in database
            if book:
                store_chapter_in_db(book, chapter_title, mp3_file, dry_run)

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
        sys.exit(1)
    finally:
        if not db.is_closed():
            db.close()
            print("Database connection closed.")


if __name__ == "__main__":
    main()