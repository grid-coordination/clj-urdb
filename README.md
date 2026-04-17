# clj-urdb

Clojure client for the [OpenEI Utility Rate Database (URDB)](https://openei.org/wiki/Utility_Rate_Database).

Fetches utility tariff data from the URDB, resolves timestamps to applicable energy prices, and generates price schedules suitable for publishing as OpenADR 3 events.

## Status

Early development. See design issue for planned scope.

## Usage

```clojure
;; deps.edn
energy.grid-coordination/clj-urdb {:mvn/version "0.1.0"}
```

```clojure
(require '[urdb.client :as client]
         '[urdb.price :as price])

;; Search for tariffs
(def rates (client/search-rates {:utility "Pacific Gas & Electric"
                                 :sector "Residential"
                                 :api-key "your-key"}))

;; Resolve price at a specific time
(price/resolve-price rate (java.time.Instant/now))
;; => {:rate 0.45, :period "Peak", :tier 1, :unit "$/kWh"}

;; Generate a price schedule for a time window
(price/schedule rate start-time end-time)
;; => [{:start t1, :end t2, :rate 0.45, :period "Peak"} ...]
```

## License

MIT
