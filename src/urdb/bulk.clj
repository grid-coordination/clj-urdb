(ns urdb.bulk
  "Load URDB tariff records from the gzipped JSON bulk download."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [urdb.rate :as rate])
  (:import [java.util.zip GZIPInputStream]))

(defn load-bulk-raw
  "Load all tariff records from a gzipped JSON file (usurdb.json.gz).
   Returns a lazy seq of raw (uncoerced) rate maps."
  [path]
  (with-open [in (-> (io/input-stream path)
                     (GZIPInputStream.)
                     (io/reader))]
    ;; The bulk file is a JSON array of rate objects
    (let [items (json/read in :key-fn keyword)]
      ;; Force realization within with-open scope
      (vec items))))

(defn load-bulk
  "Load all tariff records from a gzipped JSON file, returning coerced entities."
  [path]
  (mapv rate/coerce (load-bulk-raw path)))

(defn filter-rates
  "Filter a collection of coerced rate entities by predicate map.
   Supported keys: :utility, :sector, :state, :approved, :is-default."
  [rates {:keys [utility sector approved is-default]}]
  (cond->> rates
    utility    (filter #(= utility (:urdb.rate/utility %)))
    sector     (filter #(= sector (:urdb.rate/sector %)))
    approved   (filter :urdb.rate/approved)
    is-default (filter :urdb.rate/is-default)))
