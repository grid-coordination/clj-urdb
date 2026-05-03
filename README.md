# clj-urdb

[![Clojars Project](https://img.shields.io/clojars/v/energy.grid-coordination/clj-urdb.svg)](https://clojars.org/energy.grid-coordination/clj-urdb)
[![md-docs](https://img.shields.io/badge/md--docs-included-green)](https://github.com/dcj/codox-md)
[![build-provenance](https://img.shields.io/badge/build--provenance-included-blue)](https://github.com/dcj/build-provenance)

Clojure client for the [OpenEI Utility Rate Database (URDB)](https://openei.org/wiki/Utility_Rate_Database).

Fetches utility tariff data from the URDB v7 API, resolves timestamps to applicable energy prices using TOU schedule matrices, and generates contiguous price schedules suitable for publishing as [OpenADR 3](https://www.openadr.org/) events.

## Features

- **API client** — search and fetch tariff records from the OpenEI URDB v7 REST API
- **Bulk loading** — parse the gzipped URDB bulk download (`usurdb.json.gz`)
- **Price resolution** — resolve `(tariff, timestamp, timezone)` to an energy price in $/kWh
- **Schedule generation** — produce contiguous price intervals for arbitrary time windows
- **TOU support** — 12x24 weekday/weekend schedule matrix lookup with automatic period merging
- **Tiered rates** — first-tier marginal rate used as the price signal
- **Fuel adjustments** — monthly $/kWh adders applied automatically
- **Coerced entities** — namespaced keywords, raw metadata preserved, [tick](https://github.com/juxt/tick) interval compatibility

## Installation

```clojure
;; deps.edn
energy.grid-coordination/clj-urdb {:mvn/version "0.2.0"}
```

## Prerequisites

An OpenEI API key (free): https://openei.org/services/api/signup

Set the `OPENEI_API_KEY` environment variable, or pass `:api-key` explicitly to client functions:

```bash
export OPENEI_API_KEY="your-key-here"
```

## Usage

### Fetch tariffs

```clojure
(require '[urdb.client :as client])

;; Search for tariffs (uses OPENEI_API_KEY env var)
(def rates (client/search-rates {:utility "Pacific Gas & Electric"
                                 :sector "Residential"}))

;; Or pass the key explicitly
(def rates (client/search-rates {:api-key "your-key"
                                 :utility "Pacific Gas & Electric"
                                 :sector "Residential"}))

;; Fetch a specific tariff by its label ID
(def rate (client/get-rate "539f6a22ec4f024411ec8bf9" {}))
```

Both `search-rates` and `get-rate` return coerced entities with namespaced keywords. Use `search-rates-raw` / `get-rate-raw` for the uncoerced API response.

### Load bulk data

```clojure
(require '[urdb.bulk :as bulk])

;; Load all ~3,700 tariffs from the bulk download
(def all-rates (bulk/load-bulk "/path/to/usurdb.json.gz"))

;; Filter in-memory
(def pge-residential (bulk/filter-rates all-rates
                       {:utility "Pacific Gas & Electric"
                        :sector :urdb.sector/residential
                        :approved true}))
```

### Resolve prices

```clojure
(require '[urdb.price :as price])
(import '[java.time Instant ZoneId])

;; What does electricity cost right now under this tariff?
(price/resolve-price rate
                     (Instant/now)
                     (ZoneId/of "America/Los_Angeles"))
;; => {:urdb.interval/price       0.25
;;     :urdb.interval/period      2
;;     :urdb.interval/period-label "On-Peak"
;;     :urdb.interval/tier        0
;;     :urdb.interval/unit        :urdb.unit/dollar-per-kwh}
```

### Generate price schedules

```clojure
(require '[urdb.generate :as gen])
(import '[java.time Instant ZoneId Duration])

(def pacific (ZoneId/of "America/Los_Angeles"))
(def now (Instant/now))
(def tomorrow (.plus now (Duration/ofHours 24)))

;; Generate a 24-hour price schedule
(gen/price-schedule rate now tomorrow pacific)
;; => [{:tick/beginning #time/zoned-date-time "2026-04-14T00:00-07:00[America/Los_Angeles]"
;;      :tick/end       #time/zoned-date-time "2026-04-14T08:00-07:00[America/Los_Angeles]"
;;      :urdb.interval/price 0.08, :urdb.interval/period-label "Off-Peak", ...}
;;     {:tick/beginning #time/zoned-date-time "2026-04-14T08:00-07:00[America/Los_Angeles]"
;;      :tick/end       #time/zoned-date-time "2026-04-14T12:00-07:00[America/Los_Angeles]"
;;      :urdb.interval/price 0.15, :urdb.interval/period-label "Mid-Peak", ...}
;;     ...]

;; Convenience: N days from a start time
(gen/price-schedule-days rate now 7 pacific)
```

Adjacent hours with the same price and period are merged into single contiguous intervals. Each interval has `:tick/beginning` and `:tick/end` keys (as `java.time.ZonedDateTime` in the supplied zone), making them compatible with [tick](https://github.com/juxt/tick) interval algebra.

### Inspect rate structure

```clojure
(require '[urdb.rate :as rate]
         '[urdb.schedule :as schedule])

;; Rate type predicates
(rate/tou-rate? rate)     ;=> true
(rate/flat-rate? rate)    ;=> false
(rate/tiered-rate? rate)  ;=> true

;; Examine TOU schedule transitions for January
(schedule/schedule-transitions
  (:urdb.rate/energy-weekday-schedule rate) 0)
;; => ({:hour 8, :from-period 0, :to-period 1}
;;     {:hour 12, :from-period 1, :to-period 2}
;;     {:hour 18, :from-period 2, :to-period 1}
;;     {:hour 22, :from-period 1, :to-period 0})

;; Access the original API response via metadata
(:urdb/raw (meta rate))
;; => {:label "...", :energyratestructure [...], ...}
```

## Time and timezones

clj-urdb is **`ZonedDateTime`-at-the-boundary**: every interval emitted by `urdb.generate` carries `:tick/beginning` and `:tick/end` as `java.time.ZonedDateTime` in the zone you pass in. Internally, hour stepping uses `ZonedDateTime#plusHours`, so DST transitions are handled correctly:

- **Spring-forward** day produces 23 hourly buckets (the local 02:xx hour is skipped).
- **Fall-back** day produces 25 hourly buckets (the local 01:xx hour repeats).

The library does not synthesize, drop, or repeat hours — it lets `ZonedDateTime` arithmetic do the right thing for the supplied zone.

**Zone source: caller-supplied `ZoneId` arg.** `urdb.price/resolve-price`, `urdb.generate/price-schedule`, and `urdb.generate/price-schedule-days` all take a `java.time.ZoneId` parameter that determines (a) which local wall-clock hour the URDB TOU schedule is indexed against and (b) the zone of the `ZonedDateTime` values on the returned intervals. clj-urdb does not infer a zone from the tariff record — the URDB record does not carry one.

**Standing rule:** pass an IANA `ZoneId` (e.g. `(ZoneId/of "America/Los_Angeles")`), not a fixed offset. TOU schedules and DST behavior are only correct under a real zone.

```clojure
(require '[tick.core :as t])

;; ZonedDateTime intervals work directly with tick's interval algebra
(let [sched (gen/price-schedule rate start end pacific)]
  (t/relation (nth sched 0) (nth sched 1)))   ;=> :meets

;; Reach the wall-clock pieces directly
(let [{:tick/keys [beginning end]} (first sched)]
  [(.getHour ^java.time.ZonedDateTime beginning)
   (.getZone ^java.time.ZonedDateTime end)])
```

If a downstream consumer wants `Instant`s, call `.toInstant` at that boundary; clj-urdb does not pre-convert.

## Namespaces

| Namespace | Purpose |
|---|---|
| `urdb.client` | HTTP client for the OpenEI URDB v7 REST API |
| `urdb.bulk` | Bulk download parsing (gzipped JSON) |
| `urdb.rate` | Rate record coercion and normalization |
| `urdb.rate.schema` | Malli schemas for coerced entities |
| `urdb.rate.schema.raw` | Malli schemas for raw API responses |
| `urdb.schedule` | TOU 12x24 schedule matrix operations |
| `urdb.price` | Price resolution (rate + timestamp -> price) |
| `urdb.generate` | Price schedule generation for time windows |

## Entity Model

Raw URDB JSON is coerced into entities with namespaced keywords following the [Clojure API Entity Pattern](https://github.com/grid-coordination/clj-gridx/blob/main/doc/clojure-api-entity-pattern.md):

```
HTTP JSON ──> Raw EDN (camelCase) ──> Coerced Entities (namespaced, typed)
                                           |
                                           └── metadata: {:urdb/raw <original>}
```

Key namespaces used in entity keys:

| Prefix | Example | Description |
|---|---|---|
| `urdb.rate/` | `:urdb.rate/label`, `:urdb.rate/utility` | Rate entity fields |
| `urdb.tier/` | `:urdb.tier/rate`, `:urdb.tier/max` | Pricing tier fields |
| `urdb.interval/` | `:urdb.interval/price`, `:urdb.interval/period` | Price interval fields |
| `urdb.sector/` | `:urdb.sector/residential` | Sector enum values |
| `urdb.unit/` | `:urdb.unit/dollar-per-kwh` | Unit enum values |

## Development

```bash
# Run tests
clojure -M:test

# Start nREPL
clojure -M:nrepl

# Lint
clj-kondo --lint src test

# Build JAR
clojure -T:build ci

# Install locally
clojure -T:build install
```

## Contributing

Issues, Discussions, and pull requests are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) for the workflow (and the dev commands: tests, lint, nREPL, build). In short:

- **Questions, API/design discussion, URDB modeling gaps** → [Discussions](https://github.com/grid-coordination/clj-urdb/discussions)
- **Confirmed bugs, coercion/schema fixes, doc errors** → [Issues](https://github.com/grid-coordination/clj-urdb/issues)
- **Patches** → pull requests; please open a Discussion or Issue first for non-trivial changes (new endpoints, new schema fields, new coercion behavior, contract changes to price resolution or schedule generation)

## License

Copyright (c) 2026 Clark Communications Corporation

[MIT](LICENSE)
