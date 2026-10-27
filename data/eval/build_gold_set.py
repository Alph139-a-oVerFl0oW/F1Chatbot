#!/usr/bin/env python3
"""
Generate a gold-standard eval set directly from Postgres.

The legacy corpus only yields ~1,190 answerable questions, all of one shape
("who won race X in year Y"). That tests one tool. This script builds a set
that covers what the agent actually claims to do — career stats, championships,
pole positions, multi-step lookups, and cases where the correct answer is
"that doesn't exist".

Because the questions are generated from the same database the agent queries,
the expected answers are correct by construction. That measures whether the
agent retrieves and reports correctly, which is the thing under test — not
whether the underlying data is right.

Usage:
  python3 build_gold_set.py gold_set.json --per-category 12
"""

import argparse
import json
import random

import psycopg2
import psycopg2.extras

DSN = "host=localhost port=5432 dbname=f1app user=f1app password=f1app"


def rows(conn, sql, args=()):
    with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cur:
        cur.execute(sql, args)
        return cur.fetchall()


def build(conn, per_category, seed):
    random.seed(seed)
    items = []

    def add(category, question, expected, kind, note=""):
        items.append({
            "id": f"gold-{len(items):04d}",
            "source": "generated",
            "category": category,
            "question": question,
            "expected": expected,
            "match": kind,          # name | number | phrase
            "note": note,
        })

    # --- race winners -----------------------------------------------------
    for r in rows(conn, """
            SELECT ra.season, ra.name, d.family_name
            FROM results r
            JOIN races ra ON ra.id = r.race_id
            JOIN drivers d ON d.id = r.driver_id
            WHERE r.position = 1 AND ra.season BETWEEN 1960 AND 2024
            ORDER BY random() LIMIT %s""", (per_category,)):
        add("race_winner",
            f"Who won the {r['name']} in {r['season']}?",
            r["family_name"], "name")

    # --- drivers' champions ----------------------------------------------
    for r in rows(conn, """
            SELECT ra.season, d.family_name
            FROM driver_standings s
            JOIN races ra ON ra.id = s.race_id
            JOIN drivers d ON d.id = s.driver_id
            WHERE s.position = 1
              AND ra.round = (SELECT max(r2.round) FROM races r2 WHERE r2.season = ra.season)
              AND ra.season BETWEEN 1950 AND 2024
            ORDER BY random() LIMIT %s""", (per_category,)):
        add("drivers_champion",
            f"Who won the {r['season']} drivers' championship?",
            r["family_name"], "name")

    # --- constructors' champions -----------------------------------------
    for r in rows(conn, """
            SELECT ra.season, c.name
            FROM constructor_standings s
            JOIN races ra ON ra.id = s.race_id
            JOIN constructors c ON c.id = s.constructor_id
            WHERE s.position = 1
              AND ra.round = (SELECT max(r2.round) FROM races r2 WHERE r2.season = ra.season)
              AND ra.season BETWEEN 1958 AND 2024
            ORDER BY random() LIMIT %s""", (per_category,)):
        add("constructors_champion",
            f"Which team won the {r['season']} constructors' championship?",
            r["name"], "name")

    # --- career wins: needs findDriver then driverCareer (multi-step) -----
    for r in rows(conn, """
            SELECT d.given_name, d.family_name, count(*) AS wins
            FROM results r JOIN drivers d ON d.id = r.driver_id
            WHERE r.position = 1
            GROUP BY d.id, d.given_name, d.family_name
            HAVING count(*) >= 10
            ORDER BY random() LIMIT %s""", (per_category,)):
        add("career_wins",
            f"How many career race wins does {r['given_name']} {r['family_name']} have?",
            str(r["wins"]), "number", "requires findDriver then driverCareer")

    # --- pole positions ---------------------------------------------------
    for r in rows(conn, """
            SELECT ra.season, ra.name, d.family_name
            FROM qualifying q
            JOIN races ra ON ra.id = q.race_id
            JOIN drivers d ON d.id = q.driver_id
            WHERE q.position = 1 AND ra.season BETWEEN 2000 AND 2024
            ORDER BY random() LIMIT %s""", (per_category,)):
        add("pole_position",
            f"Who took pole position for the {r['season']} {r['name']}?",
            r["family_name"], "name",
            "qualifying data is only exposed via raceResults detail")

    # --- season length ----------------------------------------------------
    for r in rows(conn, """
            SELECT season, count(*) AS races
            FROM races WHERE season BETWEEN 1950 AND 2024
            GROUP BY season ORDER BY random() LIMIT %s""", (per_category,)):
        add("season_length",
            f"How many races were there in the {r['season']} Formula 1 season?",
            str(r["races"]), "number")

    # --- circuit location -------------------------------------------------
    for r in rows(conn, """
            SELECT c.name, c.country
            FROM circuits c JOIN races ra ON ra.circuit_id = c.id
            WHERE c.country IS NOT NULL
            GROUP BY c.name, c.country
            ORDER BY random() LIMIT %s""", (per_category,)):
        add("circuit_country",
            f"In which country is the {r['name']} located?",
            r["country"], "name")

    # --- negatives: the honest answer is "no data" ------------------------
    # A model answering from parametric memory will invent something here.
    for season in random.sample(range(1950, 1958), min(per_category, 8)):
        add("negative_constructors",
            f"Who won the {season} constructors' championship?",
            "did not exist", "phrase",
            "constructors' championship began in 1958")

    add("negative_future",
        "Who won the 2035 drivers' championship?",
        "no data", "phrase", "season not in database")

    return items


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("dest")
    parser.add_argument("--per-category", type=int, default=12)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--dsn", default=DSN)
    args = parser.parse_args()

    with psycopg2.connect(args.dsn) as conn:
        items = build(conn, args.per_category, args.seed)

    with open(args.dest, "w", encoding="utf-8") as handle:
        json.dump(items, handle, indent=2, ensure_ascii=False)

    counts = {}
    for item in items:
        counts[item["category"]] = counts.get(item["category"], 0) + 1
    for key, value in sorted(counts.items()):
        print(f"  {key:<24} {value}")
    print(f"\n{len(items)} questions -> {args.dest}")


if __name__ == "__main__":
    main()
