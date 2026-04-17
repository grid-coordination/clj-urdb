(ns urdb.rate
  "Parse and normalize OpenEI URDB tariff records.
   Coerces raw JSON maps into idiomatic entities with namespaced keywords
   and attaches the original data as :urdb/raw metadata.")

;;; --- Coercion helpers ---

(defn- coerce-tier
  "Normalize a single tier map from URDB JSON."
  [tier]
  (let [rate (double (or (:rate tier) 0.0))
        adj  (double (or (:adj tier) 0.0))]
    (cond-> {:urdb.tier/rate (+ rate adj)}
      (:max tier) (assoc :urdb.tier/max (double (:max tier))))))

(defn- coerce-rate-structure
  "Parse an array-of-arrays rate structure into normalized periods."
  [structure]
  (when (seq structure)
    (mapv (fn [period-tiers]
            (mapv coerce-tier period-tiers))
          structure)))

(defn- coerce-schedule-matrix
  "Parse a 12x24 schedule matrix. Returns nil if input is nil/empty."
  [matrix]
  (when (and matrix (= 12 (count matrix)))
    (mapv vec matrix)))

(defn- coerce-fuel-adjustments
  "Parse monthly fuel adjustment array (12 elements, $/kWh)."
  [adjustments]
  (when (and adjustments (= 12 (count adjustments)))
    (mapv double adjustments)))

(def ^:private sector-kw
  {"Residential" :urdb.sector/residential
   "Commercial"  :urdb.sector/commercial
   "Industrial"  :urdb.sector/industrial
   "Lighting"    :urdb.sector/lighting})

;;; --- Public API ---

(defn coerce
  "Coerce a raw URDB tariff record (parsed JSON map with keyword keys)
   into a normalized entity with namespaced keywords.
   Attaches the original raw data as :urdb/raw metadata."
  [raw]
  (-> (cond-> {:urdb.rate/label      (:label raw)
               :urdb.rate/utility    (:utility raw)
               :urdb.rate/name       (:name raw)
               :urdb.rate/approved   (boolean (:approved raw))
               :urdb.rate/is-default (boolean (:is_default raw))}

        (:sector raw)
        (assoc :urdb.rate/sector (sector-kw (:sector raw) (:sector raw)))

        (:startdate raw)
        (assoc :urdb.rate/start-date (:startdate raw))

        (:enddate raw)
        (assoc :urdb.rate/end-date (:enddate raw))

        (:description raw)
        (assoc :urdb.rate/description (:description raw))

        (:source raw)
        (assoc :urdb.rate/source (:source raw))

        (:uri raw)
        (assoc :urdb.rate/uri (:uri raw))

        (:servicetype raw)
        (assoc :urdb.rate/service-type (:servicetype raw))

        ;; Energy rates
        (:energyratestructure raw)
        (assoc :urdb.rate/energy-rate-structure
               (coerce-rate-structure (:energyratestructure raw)))

        (:energyweekdayschedule raw)
        (assoc :urdb.rate/energy-weekday-schedule
               (coerce-schedule-matrix (:energyweekdayschedule raw)))

        (:energyweekendschedule raw)
        (assoc :urdb.rate/energy-weekend-schedule
               (coerce-schedule-matrix (:energyweekendschedule raw)))

        ;; Demand rates
        (:demandratestructure raw)
        (assoc :urdb.rate/demand-rate-structure
               (coerce-rate-structure (:demandratestructure raw)))

        (:demandweekdayschedule raw)
        (assoc :urdb.rate/demand-weekday-schedule
               (coerce-schedule-matrix (:demandweekdayschedule raw)))

        (:demandweekendschedule raw)
        (assoc :urdb.rate/demand-weekend-schedule
               (coerce-schedule-matrix (:demandweekendschedule raw)))

        ;; Flat demand
        (:flatdemandstructure raw)
        (assoc :urdb.rate/flat-demand-structure
               (coerce-rate-structure (:flatdemandstructure raw)))

        (:flatdemandmonths raw)
        (assoc :urdb.rate/flat-demand-months (vec (:flatdemandmonths raw)))

        ;; Fixed charges
        (:fixedchargefirstmeter raw)
        (assoc :urdb.rate/fixed-charge-first-meter
               (double (:fixedchargefirstmeter raw)))

        (:fixedchargeeaaddl raw)
        (assoc :urdb.rate/fixed-charge-additional
               (double (:fixedchargeeaaddl raw)))

        (:mincharge raw)
        (assoc :urdb.rate/min-charge (double (:mincharge raw)))

        (:annualmincharge raw)
        (assoc :urdb.rate/annual-min-charge (double (:annualmincharge raw)))

        ;; Fuel adjustments
        (:fueladjustmentsmonthly raw)
        (assoc :urdb.rate/fuel-adjustments
               (coerce-fuel-adjustments (:fueladjustmentsmonthly raw))))
      (with-meta {:urdb/raw raw})))

;;; --- Predicates ---

(defn flat-rate?
  "True if this rate has no TOU schedule (single period)."
  [rate]
  (and (:urdb.rate/energy-rate-structure rate)
       (nil? (:urdb.rate/energy-weekday-schedule rate))))

(defn tou-rate?
  "True if this rate has TOU schedule matrices."
  [rate]
  (some? (:urdb.rate/energy-weekday-schedule rate)))

(defn tiered-rate?
  "True if any energy period has more than one tier."
  [rate]
  (when-let [structure (:urdb.rate/energy-rate-structure rate)]
    (boolean (some #(> (count %) 1) structure))))

;;; --- Period labels ---

(def ^:private default-period-labels
  {0 "Off-Peak"
   1 "Mid-Peak"
   2 "On-Peak"
   3 "Super-Off-Peak"})

(defn period-label
  "Generate a human-readable label for a period index."
  [period-index]
  (get default-period-labels (int period-index)
       (str "Period-" period-index)))
