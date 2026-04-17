(ns urdb.price
  "Price resolution: (rate, timestamp) -> applicable energy price.
   Returns coerced PriceInterval entities with namespaced keywords."
  (:require [urdb.rate :as rate]
            [urdb.schedule :as schedule])
  (:import [java.time Instant ZonedDateTime ZoneId DayOfWeek]))

(defn- weekend?
  "True if the given ZonedDateTime falls on Saturday or Sunday."
  [^ZonedDateTime zdt]
  (let [dow (.getDayOfWeek zdt)]
    (or (= dow DayOfWeek/SATURDAY)
        (= dow DayOfWeek/SUNDAY))))

(defn- resolve-tier
  "Given a period's tiers, return the first-tier rate.
   For price signaling, the marginal rate at tier 1 is the base signal."
  [tiers]
  (when (seq tiers)
    {:urdb.tier/rate (:urdb.tier/rate (first tiers))
     :urdb.tier/index 0}))

(defn resolve-price
  "Resolve the applicable energy price for a rate at a given instant.

   Arguments:
     rate     — coerced rate entity
     instant  — java.time.Instant
     zone-id  — java.time.ZoneId (customer's timezone for TOU resolution)

   Returns a map:
     {:urdb.interval/price       double   — $/kWh
      :urdb.interval/period      int      — period index
      :urdb.interval/period-label string  — human-readable period name
      :urdb.interval/tier        int      — tier index (0-based)
      :urdb.interval/unit        keyword  — :urdb.unit/dollar-per-kwh}"
  [rate-entity ^Instant instant ^ZoneId zone-id]
  (let [zdt    (.atZone instant zone-id)
        month  (dec (.getMonthValue zdt))   ; 0-based
        hour   (.getHour zdt)
        wkend? (weekend? zdt)
        period (schedule/period-at rate-entity month hour wkend?)
        structure (:urdb.rate/energy-rate-structure rate-entity)
        tiers  (when structure (get structure period))
        tier   (resolve-tier tiers)
        base-rate (or (:urdb.tier/rate tier) 0.0)
        fuel-adj  (when-let [adjs (:urdb.rate/fuel-adjustments rate-entity)]
                    (get adjs month 0.0))
        total  (+ base-rate (or fuel-adj 0.0))]
    {:urdb.interval/price       total
     :urdb.interval/period      period
     :urdb.interval/period-label (rate/period-label period)
     :urdb.interval/tier        (or (:urdb.tier/index tier) 0)
     :urdb.interval/unit        :urdb.unit/dollar-per-kwh}))
