#!/usr/bin/env python3
"""
DAISY Book Organizer
Extracts metadata from DAISY book zip files and organizes them into named directories.
"""

import argparse
import re
import shutil
import sys
import zipfile
from pathlib import Path
from typing import Optional, Tuple


def parse_arguments() -> argparse.Namespace:
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(
        description="Organize DAISY book zip files by extracting metadata and creating named directories"
    )
    parser.add_argument(
        "zip_files",
        nargs="+",
        type=Path,
        help="Paths to zip files to process"
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show what would be done without actually doing it"
    )
    return parser.parse_args()


def is_valid_zipfile(path: Path) -> bool:
    """Check if the file is a valid zip file."""
    try:
        return zipfile.is_zipfile(path)
    except Exception:
        return False


def extract_ncc_content(zip_path: Path) -> Optional[str]:
    """Extract NCC.HTML content from the zip file."""
    try:
        with zipfile.ZipFile(zip_path, 'r') as zf:
            # Look for exact filename 'NCC.HTML'
            for name in zf.namelist():
                if name.endswith('NCC.HTML'):
                    return zf.read(name).decode('utf-8', errors='ignore')
        # If not found, warn
        print(f"Warning: NCC.HTML not found in {zip_path}", file=sys.stderr)
        return None
    except Exception as e:
        print(f"Warning: Failed to extract NCC.HTML from {zip_path}: {e}", file=sys.stderr)
        return None


def parse_meta_tag(content: str, meta_name: str) -> Optional[str]:
    """Parse a meta tag and extract its content attribute."""
    # Match meta tag with the specified name and extract content
    pattern = rf'<meta\s+name=["\']?{re.escape(meta_name)}["\']?\s+content=["\']([^"\']+)["\']'
    match = re.search(pattern, content, re.IGNORECASE)
    if match:
        return match.group(1).strip()
    return None


def strip_punctuation(text: str) -> str:
    """Strip punctuation marks from text."""
    return re.sub(r'[^\w\s-]', '', text)


def truncate_tokens(tokens: list[str], max_tokens: int, max_chars: int) -> list[str]:
    """
    Truncate tokens to maximum number and character limit.
    Drops tokens that would be truncated by the character rule.
    """
    result = []
    total_chars = 0

    for token in tokens[:max_tokens]:
        # Calculate length including dash separator (except for first token)
        token_contribution = len(token) + (1 if result else 0)

        if total_chars + token_contribution <= max_chars:
            result.append(token)
            total_chars += token_contribution
        else:
            # Stop adding tokens if this one would exceed the limit
            break

    return result


def create_directory_name(creator: str, title: str) -> str:
    """Create directory name from creator and title metadata."""
    # Strip punctuation
    creator = strip_punctuation(creator)
    title = strip_punctuation(title)

    # Split into tokens
    creator_tokens = creator.split()
    title_tokens = title.split()

    # Take first 2 words of creator
    creator_tokens = creator_tokens[:2]

    # Truncate title to max 3 tokens, dropping entire words that would exceed 15 chars
    title_tokens = truncate_tokens(title_tokens, max_tokens=3, max_chars=15)

    # Join tokens with dashes and lowercase
    creator_part = '-'.join(creator_tokens).lower()
    title_part = '-'.join(title_tokens).lower()

    # Combine with underscore
    return f"{title_part}_{creator_part}"


def process_zip_file(zip_path: Path, dry_run: bool) -> None:
    """Process a single zip file."""
    print(f"\nProcessing: {zip_path}")

    # Check if it's a valid zip file
    if not is_valid_zipfile(zip_path):
        print(f"Warning: {zip_path} is not a valid zip file. Skipping.", file=sys.stderr)
        return

    # Extract NCC.HTML content
    ncc_content = extract_ncc_content(zip_path)
    if not ncc_content:
        print(f"Warning: Could not extract NCC.HTML from {zip_path}. Skipping.", file=sys.stderr)
        return

    # Parse metadata
    creator = parse_meta_tag(ncc_content, "dc:creator")
    title = parse_meta_tag(ncc_content, "dc:title")

    if not creator or not title:
        print(f"Warning: Missing metadata in {zip_path} (creator: {creator}, title: {title}). Skipping.",
              file=sys.stderr)
        return

    print(f"  Creator: {creator}")
    print(f"  Title: {title}")

    # Create directory name
    dir_name = create_directory_name(creator, title)
    target_dir = zip_path.parent / dir_name

    print(f"  Target directory: {dir_name}")

    if dry_run:
        print(f"  [DRY RUN] Would create directory: {target_dir}")
        print(f"  [DRY RUN] Would move {zip_path.name} to {target_dir}")
        print(f"  [DRY RUN] Would unzip {zip_path.name} in {target_dir}")
    else:
        try:
            # Create target directory
            target_dir.mkdir(exist_ok=True)
            print(f"  Created directory: {target_dir}")

            # Move zip file to target directory
            new_zip_path = target_dir / zip_path.name
            shutil.move(str(zip_path), str(new_zip_path))
            print(f"  Moved {zip_path.name} to {target_dir}")

            # Unzip the file
            with zipfile.ZipFile(new_zip_path, 'r') as zf:
                zf.extractall(target_dir)
            print(f"  Unzipped {zip_path.name} in {target_dir}")

        except Exception as e:
            print(f"Warning: Failed to process {zip_path}: {e}", file=sys.stderr)


def main() -> None:
    """Main entry point."""
    args = parse_arguments()

    if args.dry_run:
        print("=== DRY RUN MODE ===\n")

    for zip_path in args.zip_files:
        if not zip_path.exists():
            print(f"Warning: File not found: {zip_path}. Skipping.", file=sys.stderr)
            continue

        process_zip_file(zip_path, args.dry_run)

    print("\nDone!")


if __name__ == "__main__":
    main()