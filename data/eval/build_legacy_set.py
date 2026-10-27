#!/usr/bin/env python3
"""
Extract the answerable subset of the legacy JSONL corpus.

The corpus has 643,778 prompt/completion pairs, but ~99.8% cannot be scored:

  89.3%  fastest lap    - needs per-lap data (not ingested), and no question
                          carries a year, so "fastest lap by Vettel at the
                          Australian GP" has 13 possible answers
   8.3%  result/status  - no year, same ambiguity
   1.7%  pit stops      - needs pit stop data, no year
   0.4%  standings      - "after 18" gives no season

Only the race-winner questions (all year-qualified) and some circuit-location
questions are well posed. This script pulls those out.

Usage:  python3 build_legacy_set.py ../race_history.jsonl legacy_set.json
"""

import json
import re
import sys
from collections import Counter


def classify(prompt: str) -> str:
    if prompt.startswith("Who won") and re.search(r"\b(19|20)\d{2}\b", prompt):
        return "race_winner"
    if "location of" in prompt:
        return "circuit_location"
    return "unusable"


def extract_expected(category: str, completion: str) -> str | None:
    """Pull the graded span out of the reference answer."""
    if category == "race_winner":
        # "Button won the Australian Grand Prix in 2009." -> "Button"
        m = re.match(r"^(.+?) won the ", completion)
        return m.group(1).strip() if m else None
    if category == "circuit_location":
        # "... is located in Melbourne, Australia." -> "Melbourne, Australia"
        m = re.search(r"is located in (.+?)\.?$", completion)
        return m.group(1).strip() if m else None
    return None


def main(src: str, dest: str) -> None:
    kept, seen, stats = [], set(), Counter()

    with open(src, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                record = json.loads(line)
            except json.JSONDecodeError:
                stats["malformed"] += 1
                continue

            prompt = record.get("prompt", "").strip()
            completion = record.get("completion", "").strip()
            category = classify(prompt)
            stats[category] += 1

            if category == "unusable" or prompt in seen:
                continue

            expected = extract_expected(category, completion)
            if not expected:
                stats["no_expected_span"] += 1
                continue

            seen.add(prompt)
            kept.append({
                "id": f"legacy-{len(kept):04d}",
                "source": "legacy_jsonl",
                "category": category,
                "question": prompt,
                "expected": expected,
                "reference": completion,
            })

    with open(dest, "w", encoding="utf-8") as handle:
        json.dump(kept, handle, indent=2, ensure_ascii=False)

    total = sum(v for k, v in stats.items() if k in ("race_winner", "circuit_location", "unusable"))
    print(f"scanned      {total}")
    for key, count in stats.most_common():
        print(f"  {key:<20} {count}")
    print(f"\nkept {len(kept)} answerable questions -> {dest}")
    print(f"that is {len(kept) / total * 100:.2f}% of the corpus")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    main(sys.argv[1], sys.argv[2])
