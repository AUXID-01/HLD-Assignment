"""
build_queries_aggregated.py

Converts a raw AOL Search Query Log file (e.g. user-ct-test-collection-02.txt,
sourced from the AOL Search Query Log dataset on Kaggle) into the
queries_aggregated.csv file used by the Search Typeahead backend.

Input format (tab-separated):
    AnonID  Query   QueryTime   ItemRank    ClickURL

Output format (queries_aggregated.csv):
    query,count,last_searched_at

Logic:
    1. Read the raw log.
    2. Drop rows with an empty/blank Query field.
    3. Group by Query (case preserved as typed by the user).
    4. count = number of times each unique query string appears.
    5. last_searched_at = the most recent QueryTime for that query.
    6. Write the result as queries_aggregated.csv with the schema the
       backend's DataLoader expects.

Usage:
    python build_queries_aggregated.py <input_raw_log.txt> <output_queries_aggregated.csv>
"""

import sys
import csv
from datetime import datetime


def main(input_path: str, output_path: str) -> None:
    counts = {}
    last_seen = {}

    with open(input_path, "r", encoding="utf-8", errors="replace") as f:
        reader = csv.reader(f, delimiter="\t")
        header = next(reader, None)  # skip header row: AnonID, Query, QueryTime, ItemRank, ClickURL

        for row in reader:
            if len(row) < 3:
                continue  # malformed line, skip

            query = row[1].strip()
            query_time = row[2].strip()

            if not query:
                continue  # skip blank queries

            counts[query] = counts.get(query, 0) + 1

            if query_time:
                try:
                    ts = datetime.strptime(query_time, "%Y-%m-%d %H:%M:%S")
                except ValueError:
                    continue
                if query not in last_seen or ts > last_seen[query]:
                    last_seen[query] = ts

    with open(output_path, "w", newline="", encoding="utf-8") as out:
        writer = csv.writer(out)
        writer.writerow(["query", "count", "last_searched_at"])
        for query, count in counts.items():
            ts = last_seen.get(query)
            ts_str = ts.strftime("%Y-%m-%d %H:%M:%S") if ts else ""
            writer.writerow([query, count, ts_str])

    print(f"Done. {len(counts)} unique queries written to {output_path}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python build_queries_aggregated.py <input_raw_log.txt> <output_queries_aggregated.csv>")
        sys.exit(1)
    main(sys.argv[1], sys.argv[2])