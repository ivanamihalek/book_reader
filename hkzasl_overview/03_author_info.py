#! /usr/bin/env python
import json
import time
from olclient import OpenLibrary

def get_author_info(ol: OpenLibrary, name: str):
    """
    Use OpenLibrary client to retrieve author metadata.
    Returns a dictionary with birth_year, death_year, country, genres.
    """
    result = {
        "birth_year": None,
        "death_year": None,
        "country": None,
        "genres": []
    }

    try:
        authors = ol.Author.search(name)
        if not authors:
            print(f"No results for author: {name}")
            return result

        author = authors[0]
        result["birth_year"] = getattr(author, "birth_date", None)
        result["death_year"] = getattr(author, "death_date", None)

        # Try to extract country from location or bio
        location = getattr(author, "location", None)
        if isinstance(location, list):
            result["country"] = location[0]
        elif isinstance(location, str):
            result["country"] = location

        bio = getattr(author, "bio", None)
        if isinstance(bio, dict):
            bio = bio.get("value", "")
        if isinstance(bio, str) and not result["country"]:
            for country in ["USA", "United Kingdom", "Italy", "France", "Germany", "Spain", "Serbia", "Croatia", "Bosnia"]:
                if country.lower() in bio.lower():
                    result["country"] = country
                    break

        # Genres/subjects aren't well structured in openlibrary-client for authors, so this may stay empty
        if hasattr(author, "subject"):
            result["genres"] = author.subject[:5]

    except Exception as e:
        print(f"Error fetching data for {name}: {e}")

    return result


def main():
    input_file = "test.json"
    output_file = "authors.json"

    try:
        with open(input_file, "r", encoding="utf-8") as f:
            authors_data = json.load(f)
    except Exception as e:
        print(f"Error loading input: {e}")
        return

    ol = OpenLibrary()
    enriched_authors = []

    for author in authors_data:
        name = author["name"]
        print(f"Processing: {name}")
        info = get_author_info(ol, name)
        enriched_authors.append({
            "name": name,
            "books": author["books"],
            **info
        })
        time.sleep(1)  # Optional rate limit

    try:
        with open(output_file, "w", encoding="utf-8") as f:
            json.dump(enriched_authors, f, ensure_ascii=False, indent=2)
        print(f"Finished. Output written to {output_file}")
    except Exception as e:
        print(f"Error writing output: {e}")


if __name__ == "__main__":
    main()
