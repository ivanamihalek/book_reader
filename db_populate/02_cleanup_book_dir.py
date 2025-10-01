#! /usr/bin/env python3
import argparse
import os
import re
from sys import stderr
from typing import List, Tuple


def rename_audio_files(directory_path: str, dry_run: bool) -> None:
    """
    Rename audio files in a directory to a standardized format with zero-padded integers.

    Takes files matching the pattern '<something><zero_padded_integer>.mp3' (case insensitive)
    and renames them to '<zero_padded_integer>.mp3' (lowercase extension).

    The largest integer found in the filenames will not be padded. Only integers that are
    smaller by at least one order of magnitude will be zero-padded to match the number
    of digits in the largest integer.

    Args:
        directory_path (str): Path to the directory containing the audio files
        dry_run (bool): If True, only print what would be renamed without actually renaming

    Raises:
        FileNotFoundError: If the directory doesn't exist
        ValueError: If no files matching the expected pattern are found
        OSError: If there are permission issues or other file system errors

    Example:
        If files are: song001.mp3, track050.mp3, music5.mp3, audio100.mp3
        They become: 001.mp3, 050.mp3, 005.mp3, 100.mp3
        (100 is not padded, 5 gets padded to 005 since 5 < 100/10)
    """

    # Validate directory exists
    if not os.path.exists(directory_path):
        raise FileNotFoundError(f"Directory not found: {directory_path}")

    if not os.path.isdir(directory_path):
        raise ValueError(f"Path is not a directory: {directory_path}")

    # Pattern to match files: <something><digits>.mp3 (case insensitive)
    pattern = re.compile(r'^(.*?)(\d+)\.mp3$', re.IGNORECASE)

    # Find all matching files and extract their integers
    matching_files: List[Tuple[str, int, str]] = []  # (filename, integer, full_path)

    try:
        for filename in os.listdir(directory_path):
            full_path = os.path.join(directory_path, filename)
            if os.path.isfile(full_path):
                match = pattern.match(filename)
                if match:
                    integer_part = int(match.group(2))
                    matching_files.append((filename, integer_part, full_path))
    except OSError as e:
        raise OSError(f"Error reading directory {directory_path}: {e}")

    # Raise exception if no matching files found
    if not matching_files:
        raise ValueError(f"No files matching pattern '<something><digits>.mp3' found in {directory_path}")

    # Find the maximum integer to determine padding
    max_integer = max(file_info[1] for file_info in matching_files)
    max_digits = len(str(max_integer))

    # Process each file
    for old_filename, integer_part, full_path in matching_files:
        # **FIXED LOGIC: Pad all numbers except the maximum, but only if there are numbers
        # smaller by at least one order of magnitude**

        new_name =  f"{integer_part:0{max_digits}d}.mp3"
        # Skip if the name wouldn't change
        if old_filename == new_name:
            print(f"The name '{old_filename}' is ok. Moving on.")
            continue

        new_full_path = os.path.join(directory_path, new_name)

        if dry_run:
            print(f"Will rename {old_filename} {new_name}")
        else:
            try:
                # Check if target file already exists
                if os.path.exists(new_full_path) and new_full_path != full_path:
                    raise OSError(f"Target file already exists: {new_name}")

                os.rename(full_path, new_full_path)
            except OSError as e:
                raise OSError(f"Failed to rename {old_filename} to {new_name}: {e}")


def main():
    """
    Main function to parse command line arguments and call rename_audio_files.
    """
    parser = argparse.ArgumentParser(
        description='Rename audio files to standardized format with zero-padded integers.',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s /path/to/audio/files                    # Dry run (default)
  %(prog)s /path/to/audio/files --dry-run          # Explicit dry run
  %(prog)s /path/to/audio/files --no-dry-run       # Actually rename files
  %(prog)s /path/to/audio/files --execute          # Actually rename files

The script looks for files matching pattern '<something><digits>.mp3' and renames 
them to '<zero_padded_digits>.mp3'. Only numbers smaller by at least one order 
of magnitude than the largest number get zero-padded.
        """)

    parser.add_argument(
        'directory',
        type=str,
        help='Directory path containing the audio files to rename'
    )

    # Create mutually exclusive group for dry run options
    dry_run_group = parser.add_mutually_exclusive_group()
    dry_run_group.add_argument(
        '--dry-run',
        action='store_true',
        default=True,
        help='Show what would be renamed without actually renaming (default)'
    )
    dry_run_group.add_argument(
        '--no-dry-run', '--execute',
        action='store_false',
        dest='dry_run',
        help='Actually rename the files (disable dry run)'
    )

    args = parser.parse_args()

    try:
        # Call the rename function
        rename_audio_files(args.directory, args.dry_run)

        if args.dry_run:
            print(f"\nDry run completed. Use --no-dry-run or --execute to actually rename files.")
        else:
            print(f"\nFile renaming completed successfully.")

    except (FileNotFoundError, ValueError, OSError) as e:
        print(f"Error: {e}", file=stderr)
        exit(1)
    except KeyboardInterrupt:
        print("\nOperation cancelled by user.", file=stderr)
        exit(1)
    except Exception as e:
        print(f"Unexpected error: {e}", file=stderr)
        exit(1)




if __name__ == "__main__":
    main()

