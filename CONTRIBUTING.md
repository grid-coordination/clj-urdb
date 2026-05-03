# Contributing to clj-urdb

Thanks for your interest in contributing! This repo is a Clojure client library for the [OpenEI Utility Rate Database (URDB)](https://openei.org/wiki/Utility_Rate_Database). It fetches utility tariff data from the URDB v7 REST API and the gzipped bulk download, resolves `(tariff, timestamp, ZoneId)` to an applicable energy price, and generates contiguous price schedules suitable for publishing as [OpenADR 3](https://www.openadr.org/) events. It exposes a two-layer raw/coerced data model with malli schemas and DST-aware time handling via `ZonedDateTime` and tick intervals.

## How to contribute

### Discussions

Use [Discussions](https://github.com/grid-coordination/clj-urdb/discussions) for:

- Questions about how to use the library — clients, bulk loading, coercion, schemas, price resolution, schedule generation, time handling
- API and design judgment calls — "should clj-urdb model X?" / "is this the right shape for Y?"
- URDB API behavior gaps that affect clj-urdb — when the API exposes something that doesn't fit the current entity shape and you want to scope what the library should do about it
- Tariff modeling questions that go beyond the URDB schema (e.g. how to represent a rate the URDB doesn't capture cleanly)
- Sharing what you're building on top of clj-urdb

Discussions are open-ended — a good place to think out loud or scope something before it becomes a concrete change. Aligned outcomes from a Discussion often turn into one or more Issues.

### Issues

Use [Issues](https://github.com/grid-coordination/clj-urdb/issues) for actionable changes:

- Bugs in client construction, request building, response parsing, coercion, price resolution, or schedule generation
- Coercion or schema gaps surfaced by real URDB records (a field the library doesn't handle, or a value that breaks the coerced shape)
- New URDB endpoints or new request parameters when OpenEI exposes them
- Test failures or unexpected behavior with concrete repro steps (a tariff label is the easiest repro)
- Documentation errors, unclear explanations, or stale prose in `README.md` or namespace docstrings
- Discussion outcomes that have alignment and a clear scope

If you're not sure whether something is an Issue or a Discussion, start with a Discussion — we can convert it later.

### Pull requests

Pull requests are welcome.

- For small fixes (typos, broken links, single-test corrections, single-coercion bug fixes), open a PR directly.
- For substantive changes (new endpoints, new schema fields, new coercion behavior, new namespaces, changes to the price resolution or schedule generation contract), open a Discussion or Issue first so we can align on scope before you invest the effort.
- All changes pass `clojure -M:test` (Kaocha) and `clj-kondo --lint src test` cleanly.
- Match the existing tone and structure. The library composes HTTP/bulk → raw response → coerced entities → price resolution → schedule generation as roughly orthogonal layers; patches that fit cleanly into one layer without leaking concerns across them are the easiest to land.
- One commit per logical change is fine; we don't require squash or any particular branch naming.

## Development

```bash
clojure -M:test                 # run the Kaocha unit test suite
clojure -M:nrepl                # nREPL on the port written to .nrepl-port
clj-kondo --lint src test       # lint
clojure -T:build ci             # build the JAR (tests + docs + provenance)
```

An OpenEI API key is required for live URDB requests but not for the unit test suite. See the README for setup.

## Code of conduct

Be respectful and constructive. We're a small project and appreciate everyone who takes the time to file an issue or send a PR.

## Important notice

This library is provided on an "as-is" basis. Updates and maintenance, including responses to issues filed on GitHub, will take place on an "as time and resources permit" basis. Library output (raw URDB records, coerced rate entities, resolved prices, generated schedules) is best-effort against the OpenEI URDB v7 API and the published bulk download. The URDB itself is a community-maintained dataset and may not reflect the most recent tariff filings; this library is not authoritative for billing — independent verification against the source utility tariff is recommended for any consumer using these results for billing-correctness purposes.
