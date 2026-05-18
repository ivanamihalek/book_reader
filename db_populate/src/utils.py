import hashlib
import os
import sqlite3
import subprocess
from typing import List, Optional

from mutagen import MutagenError
from mutagen.mp3 import MP3


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


def check_adb_connection() -> Optional[str]:
    """
    Checks if ADB is installed and an authorized device is connected.

    Returns:
        An error message string if a problem is found, None otherwise.
    """
    try:
        # Check if ADB command is found
        subprocess.run(['adb', 'version'], capture_output=True, check=True, timeout=5)

        # Check if a device is connected and authorized
        result = subprocess.run(
            ['adb', 'devices'],
            capture_output=True,
            text=True,
            check=True,
            timeout=5
        )
        if "device" not in result.stdout or "unauthorized" in result.stdout:
            return "Error: No authorized Android device found. Check your connection."

    except FileNotFoundError:
        return "Error: ADB command not found. Is ADB installed and in your system's PATH?"
    except subprocess.CalledProcessError:
        return "Error: ADB execution failed unexpectedly."
    except subprocess.TimeoutExpired:
        return "Error: ADB command timed out."
    except Exception as e:
        return f"An unexpected error occurred during ADB check: {e}"

    return None


SQLITE_MAGIC = b"SQLite format 3\0"


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
