#! /usr/bin/env python
from docx import Document
import json
from collections import defaultdict

def main():

    # Load the document
    doc_path = "sources/POPIS_ZVUČNIH_KNJIGA.docx"
    doc = Document(doc_path)

    # Initialize structures
    books_by_author = defaultdict(list)
    section_counts = defaultdict(int)
    current_section = None
    total_books = 0

    # Parse the document
    for para in doc.paragraphs:
        text = para.text.strip()

        # Detect section headers
        if text.isupper() and len(text.split()) < 5:
            current_section = text
            continue

        # Match lines of the form "Author - Book"
        if " - " in text and current_section:
            parts = text.split(" - ", 1)
            author = parts[0].strip()
            book = parts[1].strip()

            books_by_author[author].append({
                "title": book,
                "poglavlje": current_section
            })
            section_counts[current_section] += 1
            total_books += 1

    # Save the grouped books
    books_output_path = "sources/books_grouped_by_author.json"
    with open(books_output_path, "w", encoding="utf-8") as f:
        json.dump(books_by_author, f, ensure_ascii=False, indent=2)

    # Prepare and save the section summary
    summary = {
        "books_per_section": dict(section_counts),
        "total_books": total_books
    }

    summary_output_path = "sources/book_section_summary.json"
    with open(summary_output_path, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)



#######################
if __name__ == "__main__":
    main()
