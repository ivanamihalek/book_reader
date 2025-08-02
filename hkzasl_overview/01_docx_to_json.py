#! /usr/bin/env python
from docx import Document
import re
import json
from unicodedata import normalize

def parse_authors_books_from_docx(docx_path):

    doc = Document(docx_path)
    authors = []
    current_author = None
    current_books = []

    # State tracking
    in_book_list = False
    book_pattern = re.compile(r'^\s*(.+?)\s*[-–—]\s*(.+)$')

    for para in doc.paragraphs:
        # Normalize and clean the text
        line = normalize('NFKC', para.text.strip())
        line = re.sub(r'\s+', ' ', line)

        # Detect when we enter the book list section
        if line == "LIJEPA KNJIŽEVNOST" or line.startswith("POGLAVLJA"):
            in_book_list = True
            continue

        # Skip everything until we're in the book list section
        if not in_book_list:
            continue

        # Skip section headers (single letters) and empty lines
        if len(line) <= 1 or not line:
            continue

        # Try to match author - book pattern
        match = book_pattern.match(line)
        if match:
            author = match.group(1).strip()
            book = match.group(2).strip()

            # Handle author change
            if current_author and author != current_author:
                authors.append({"name": current_author, "books": current_books})
                current_books = []

            current_author = author
            current_books.append(book)

    # Add the last author
    if current_author and current_books:
        authors.append({"name": current_author, "books": current_books})

    return {"authors": authors}

def main():
    input_docx = "sources/POPIS_ZVUČNIH_KNJIGA.docx"   # Path to your file
    output_json = "authors_books.json"

    data = parse_authors_books_from_docx(input_docx)
    with open(output_json, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    print(f"Parsed {len(data['authors'])} authors. Saved to {output_json}.")


#######################
if __name__ == "__main__":
    main()
