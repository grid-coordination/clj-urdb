(ns urdb.client
  "HTTP client for the OpenEI URDB v7 REST API."
  (:require [clojure.data.json :as json]
            [hato.client :as hc]
            [urdb.rate :as rate]))

(def ^:private api-url "https://api.openei.org/utility_rates")
(def ^:private max-limit 500)

(defn- resolve-api-key
  "Resolve the API key from opts or the OPENEI_API_KEY environment variable."
  [api-key]
  (or api-key
      (System/getenv "OPENEI_API_KEY")
      (throw (ex-info "No OpenEI API key provided. Set :api-key or OPENEI_API_KEY env var."
                      {}))))

(defn- build-params
  "Build query parameters for the URDB API from an options map."
  [{:keys [api-key utility sector state zipcode
           lat lon radius effective-on is-default
           limit offset]}]
  (cond-> {:version 7
           :format "json"
           :detail "full"
           :api_key (resolve-api-key api-key)}
    utility      (assoc :ratesforutility utility)
    sector       (assoc :sector sector)
    state        (assoc :state state)
    zipcode      (assoc :address zipcode)
    lat          (assoc :lat lat)
    lon          (assoc :lon lon)
    radius       (assoc :radius radius)
    effective-on (assoc :effective_on_date effective-on)
    is-default   (assoc :is_default is-default)
    limit        (assoc :limit (min limit max-limit))
    offset       (assoc :offset offset)))

(defn- do-request
  "Execute an HTTP GET to the URDB API and return the parsed JSON body."
  [params]
  (let [response (hc/get api-url
                         {:query-params params
                          :as :string})]
    (json/read-str (:body response) :key-fn keyword)))

(defn search-rates-raw
  "Search for tariff records from OpenEI. Returns raw (uncoerced) rate maps.
   Options:
     :api-key      — OpenEI API key (falls back to OPENEI_API_KEY env var)
     :utility      — utility name filter
     :sector       — Residential, Commercial, Industrial
     :state        — two-letter state code
     :zipcode      — ZIP code
     :lat/:lon     — latitude/longitude
     :radius       — search radius (miles) for lat/lon
     :effective-on — date string (YYYY-MM-DD)
     :is-default   — boolean, default tariffs only
     :limit        — max results (capped at 500)
     :offset       — pagination offset"
  [opts]
  (let [params (build-params opts)
        body   (do-request params)]
    (:items body)))

(defn search-rates
  "Search for tariff records, returning coerced rate entities.
   See `search-rates-raw` for options."
  [opts]
  (mapv rate/coerce (search-rates-raw opts)))

(defn get-rate-raw
  "Fetch a single tariff record by its label (unique ID). Returns raw map.
   Options:
     :api-key — OpenEI API key (falls back to OPENEI_API_KEY env var)"
  [label {:keys [api-key]}]
  (let [params {:version 7
                :format "json"
                :detail "full"
                :api_key (resolve-api-key api-key)
                :getpage label}
        body   (do-request params)]
    (first (:items body))))

(defn get-rate
  "Fetch a single tariff record by label, returning a coerced rate entity."
  [label opts]
  (some-> (get-rate-raw label opts) rate/coerce))
