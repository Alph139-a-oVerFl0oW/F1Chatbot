#!/usr/bin/env python3
"""
Run an eval set against the running chatbot and score the answers.

Scoring is deliberately lenient about prose and strict about facts. The agent
answers in sentences, so we check whether the expected fact appears in the
reply rather than demanding string equality:

  name    - surname token must appear (case/accent-insensitive). "Button",
            "Jenson Button" and "Button won in Melbourne" all pass.
  number  - the exact integer must appear as a standalone token, so "71 wins"
            passes but "710" does not.
  phrase  - any of a set of acceptable phrasings must appear, used for the
            negative cases where the right answer is "there wasn't one".

Every question gets a fresh sessionId so the conversation memory of one
question cannot leak into the next.

Usage:
  python3 run_eval.py gold_set.json --limit 40 --delay 3
  python3 run_eval.py legacy_set.json --limit 60 --out report_legacy
"""

import argparse
import json
import random
import statistics
import sys
import time
import unicodedata
import uuid

import requests

# Country names the database and the model spell differently. Checked in both
# directions so "UK" in the gold set matches "United Kingdom" in the reply.
COUNTRY_ALIASES = [
    {"uk", "united kingdom", "great britain", "britain", "england"},
    {"usa", "united states", "united states of america", "america"},
    {"uae", "united arab emirates"},
    {"korea", "south korea", "republic of korea"},
    {"netherlands", "holland"},
]

NEGATIVE_PHRASES = {
    "did not exist": [
        "did not exist", "didn't exist", "wasn't introduced", "was not introduced",
        "began in 1958", "started in 1958", "not until 1958", "no constructors",
        "introduced in 1958", "did not have",
    ],
    "no data": [
        "no data", "not available", "don't have", "do not have", "no information",
        "hasn't happened", "has not happened", "not in the database", "cannot find",
        "no results", "unable to find", "future",
        "does not contain", "doesn't contain", "only covers", "outside the range",
        "contains no", "no drivers' championship standings", "no standings",
    ],
}


def normalise(text: str) -> str:
    """Lowercase and strip accents so Raikkonen matches Räikkönen."""
    text = unicodedata.normalize("NFKD", text)
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    return text.lower()


def score(item: dict, reply: str) -> bool:
    expected = item["expected"]
    kind = item.get("match")
    if kind is None:
        kind = "number" if expected.isdigit() else "name"

    reply_norm = normalise(reply)

    if kind == "phrase":
        return any(p in reply_norm for p in NEGATIVE_PHRASES.get(expected, [expected]))

    if kind == "number":
        tokens = set(
            "".join(ch if ch.isalnum() else " " for ch in reply_norm).split()
        )
        return expected in tokens

    # name: an alias group counts as a hit if ANY of its spellings appears
    expected_norm = normalise(expected)
    for group in COUNTRY_ALIASES:
        if expected_norm in group:
            return any(alias in reply_norm for alias in group)

    # otherwise require the most distinctive token of the expected answer
    parts = [p for p in expected_norm.replace(",", " ").split() if len(p) > 2]
    if not parts:
        return expected_norm in reply_norm
    key = max(parts, key=len)
    return key in reply_norm


def ask(base_url: str, question: str, timeout: int) -> tuple[str, float, str | None]:
    started = time.time()
    try:
        response = requests.post(
            f"{base_url}/api/chat",
            json={"message": question, "sessionId": str(uuid.uuid4())},
            timeout=timeout,
        )
        elapsed = time.time() - started
        if response.status_code != 200:
            return "", elapsed, f"HTTP {response.status_code}"
        return response.json().get("reply", ""), elapsed, None
    except requests.RequestException as exc:
        return "", time.time() - started, type(exc).__name__


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("dataset")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--limit", type=int, default=40)
    parser.add_argument("--delay", type=float, default=3.0,
                        help="seconds between questions; Groq free tier is 30 req/min "
                             "and each question costs 2+ model calls")
    parser.add_argument("--timeout", type=int, default=90)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--out", default="report")
    parser.add_argument("--skip-passed", metavar="REPORT.JSON",
                        help="load a previous run and only re-ask questions that "
                             "failed; free-tier token budgets are daily, so "
                             "re-running known passes wastes them")
    args = parser.parse_args()

    with open(args.dataset, encoding="utf-8") as handle:
        items = json.load(handle)

    carried = []
    if args.skip_passed:
        try:
            with open(args.skip_passed, encoding="utf-8") as handle:
                previous = json.load(handle)
        except FileNotFoundError:
            print(f"No previous run at {args.skip_passed}, running everything.\n")
            previous = []
        # Keep passes so the final report still covers the whole set, and only
        # re-ask what failed. A 503 is a rate limit, not a wrong answer, so those
        # count as unanswered and get retried.
        passed_ids = {r["id"] for r in previous if r.get("passed")}
        carried = [r for r in previous if r.get("passed")]
        items = [i for i in items if i["id"] not in passed_ids]
        print(f"Carrying {len(carried)} passes from {args.skip_passed}, "
              f"{len(items)} left to ask.\n")

    random.seed(args.seed)
    if args.limit and args.limit < len(items):
        # stratify so every category is represented, not just the biggest one
        by_category: dict[str, list] = {}
        for item in items:
            by_category.setdefault(item["category"], []).append(item)
        per = max(1, args.limit // len(by_category))
        sampled = []
        for group in by_category.values():
            sampled.extend(random.sample(group, min(per, len(group))))
        items = sampled[: args.limit]

    if not items:
        print("Nothing left to ask - every question passed in the previous run.")
        report(carried, [], args.out)
        return 0

    print(f"Running {len(items)} questions against {args.base_url}\n")

    results, failures = list(carried), []
    for index, item in enumerate(items, start=1):
        reply, elapsed, error = ask(args.base_url, item["question"], args.timeout)
        passed = False if error else score(item, reply)

        results.append({**item, "reply": reply, "elapsed": elapsed,
                        "error": error, "passed": passed})
        if not passed:
            failures.append(results[-1])

        mark = "PASS" if passed else "FAIL"
        print(f"[{index:>3}/{len(items)}] {mark}  {elapsed:>5.1f}s  {item['question'][:62]}")
        if not passed:
            print(f"          expected: {item['expected']}")
            print(f"          got:      {(error or reply)[:110]}")

        if index < len(items):
            time.sleep(args.delay)

    report(results, failures, args.out)


def report(results, failures, out_prefix):
    total = len(results)
    passed = sum(1 for r in results if r["passed"])
    latencies = [r["elapsed"] for r in results if not r["error"]]

    by_category: dict[str, list] = {}
    for r in results:
        by_category.setdefault(r["category"], []).append(r)

    lines = []
    lines.append(f"# Eval report\n")
    lines.append(f"**{passed}/{total} passed ({passed / total * 100:.1f}%)**\n")
    if latencies:
        lines.append(f"Latency: median {statistics.median(latencies):.1f}s, "
                     f"max {max(latencies):.1f}s\n")

    lines.append("| Category | Passed | Total | Rate |")
    lines.append("|---|---|---|---|")
    for category, group in sorted(by_category.items()):
        ok = sum(1 for r in group if r["passed"])
        lines.append(f"| {category} | {ok} | {len(group)} | {ok / len(group) * 100:.0f}% |")

    if failures:
        lines.append("\n## Failures\n")
        for f in failures:
            lines.append(f"**{f['question']}**")
            lines.append(f"- expected: `{f['expected']}`")
            lines.append(f"- got: {(f['error'] or f['reply'])[:300]}")
            if f.get("note"):
                lines.append(f"- note: {f['note']}")
            lines.append("")

    markdown = "\n".join(lines)
    with open(f"{out_prefix}.md", "w", encoding="utf-8") as handle:
        handle.write(markdown)
    with open(f"{out_prefix}.json", "w", encoding="utf-8") as handle:
        json.dump(results, handle, indent=2, ensure_ascii=False)

    print("\n" + "=" * 60)
    print(f"{passed}/{total} passed ({passed / total * 100:.1f}%)")
    for category, group in sorted(by_category.items()):
        ok = sum(1 for r in group if r["passed"])
        print(f"  {category:<26} {ok}/{len(group)}")
    if latencies:
        print(f"\nmedian latency {statistics.median(latencies):.1f}s")
    print(f"\nwrote {out_prefix}.md and {out_prefix}.json")

    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main() or 0)
