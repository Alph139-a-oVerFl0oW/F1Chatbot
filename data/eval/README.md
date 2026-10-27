# Eval harness

Two datasets, one runner.

## Why two datasets

The legacy corpus (`data/race_history.jsonl`, 643,778 pairs) turns out to be
almost entirely unusable as a regression set:

| Category | Share | Answerable |
|---|---|---|
| fastest lap | 89.3% | no — needs per-lap data, and no question carries a year |
| result / status | 8.3% | no — no year, so the question is ambiguous |
| pit stops | 1.7% | no — needs pit stop data, no year |
| race winner | 0.2% | **yes** — all 1,113 carry a year |
| standings | 0.4% | no — "after round 18" of which season? |
| circuit info | 0.2% | partly |

The corpus was templated over a flat table without carrying the season through,
so "the fastest lap by Vettel at the Australian Grand Prix" has 13 possible
answers. `build_legacy_set.py` extracts the 1,190 questions (0.18%) that are
actually well posed.

That subset only exercises one tool, so `build_gold_set.py` generates a second
set from Postgres covering championships, career stats, poles, multi-step
lookups, and negatives where the correct answer is "that didn't exist".
Questions generated from the same database the agent queries have expected
answers that are correct by construction — this measures retrieval and
reporting, not data quality.

## Running

```bash
pip install -r requirements.txt

# one-off: extract the answerable legacy subset
python3 build_legacy_set.py ../race_history.jsonl legacy_set.json

# generate the gold set (needs Postgres up: docker compose up -d postgres)
python3 build_gold_set.py gold_set.json --per-category 12

# run (backend must be running)
python3 run_eval.py gold_set.json --limit 40 --out report_gold
python3 run_eval.py legacy_set.json --limit 40 --out report_legacy
```

### Working within a daily token budget

Groq's free tier caps *tokens per day* (100k), not just per minute. At roughly
1,900 tokens per question (two model calls: pick the tool, then answer with its
result) that is about 50 questions a day across all runs including manual
testing. Two things follow:

- keep `--limit` small and `--delay` at 15s or more
- use `--skip-passed` to avoid re-spending budget on questions that already
  passed:

```bash
python3 run_eval.py gold_set.json --limit 10 --delay 15 \
    --skip-passed report_gold.json --out report_gold
```

Passes are carried into the new report, so the totals still cover the whole
set. A `HTTP 503` is a rate limit rather than a wrong answer, so those count as
unanswered and get retried.

`--delay` defaults to 3s. Groq's free tier allows 30 requests/minute and each
question costs 2+ model calls, so a shorter delay will start returning 429s.

Each run writes `<out>.md` (readable summary with per-category rates and every
failure) and `<out>.json` (raw results).

## Scoring

Lenient about prose, strict about facts — the agent answers in sentences, so
the check is whether the expected fact appears, not string equality.

- `name` — the most distinctive token must appear, accent-insensitive, so
  Räikkönen matches Raikkonen. Country names go through an alias table first,
  so a gold answer of `UK` matches a reply saying "United Kingdom"

- `number` — must appear as a standalone token, so 71 does not match 710
- `phrase` — any acceptable phrasing of a negative answer

Every question uses a fresh `sessionId` so conversation memory cannot leak
between questions.

## Interpreting failures

A failure is one of three things, and the report's `note` field usually tells
you which:

1. **Retrieval failure** — the agent picked the wrong tool or no tool. Check
   the backend logs for `tool:` lines.
2. **Reporting failure** — right data, wrong summary. Rare, and usually means
   the tool returned something ambiguous.
3. **Scorer artefact** — the answer is correct but phrased so the matcher
   missed it. Fix the matcher, not the agent.
4. **Model change** — eval numbers are only meaningful alongside the model
   that produced them. Name reports after the model (`report_gptoss120b.md`)
   rather than overwriting a single file. Providers retire models without
   notice: `llama-3.3-70b-versatile` worked one week and returned 404
   model_not_found the next, and swapping models also changed output
   formatting (gpt-oss emits markdown bold, Llama did not).

## Known limitation: scoring negatives

The negative categories match against a list of refusal phrasings, which is
brittle — there are unlimited ways to say "I don't have that", and the model
rephrases between runs. Two of the three negative failures during development
were correct answers the matcher didn't recognise. A more robust check would
assert the reply contains *no* driver name or year (wrong answers are
specific; refusals are open-ended), or use a second model call as judge.