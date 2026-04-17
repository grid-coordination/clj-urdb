(ns urdb.generate
  "Generate contiguous price schedules for time windows.
   Produces sequences of PriceInterval entities with :tick/beginning and :tick/end."
  (:require [urdb.price :as price])
  (:import [java.time Instant ZoneId ZonedDateTime Duration]
           [java.time.temporal ChronoUnit]))

(defn- hour-start
  "Truncate a ZonedDateTime to the start of its hour."
  [^ZonedDateTime zdt]
  (.truncatedTo zdt ChronoUnit/HOURS))

(defn- hour-instants
  "Generate a seq of Instants at the start of each hour from start to end."
  [^Instant start ^Instant end ^ZoneId zone-id]
  (let [first-hour (hour-start (.atZone start zone-id))]
    (->> (iterate #(.plusHours ^ZonedDateTime % 1) first-hour)
         (map #(.toInstant ^ZonedDateTime %))
         (take-while #(.isBefore ^Instant % end)))))

(defn- merge-intervals
  "Merge adjacent intervals with the same price and period into single spans."
  [intervals]
  (when (seq intervals)
    (reduce
     (fn [acc interval]
       (let [prev (peek acc)]
         (if (and prev
                  (= (:urdb.interval/price prev) (:urdb.interval/price interval))
                  (= (:urdb.interval/period prev) (:urdb.interval/period interval)))
           (conj (pop acc) (assoc prev :tick/end (:tick/end interval)))
           (conj acc interval))))
     []
     intervals)))

(defn price-schedule
  "Generate a price schedule for a rate over a time window.

   Arguments:
     rate     — coerced rate entity
     start    — java.time.Instant, start of window
     end      — java.time.Instant, end of window
     zone-id  — java.time.ZoneId, customer's local timezone

   Returns a vector of PriceInterval maps, each with:
     :tick/beginning, :tick/end           — Instants bounding the interval
     :urdb.interval/price, /period, etc. — resolved price data

   Adjacent hours with the same price and period are merged into
   single contiguous intervals."
  [rate-entity ^Instant start ^Instant end ^ZoneId zone-id]
  (let [hours (hour-instants start end zone-id)
        one-hour (Duration/ofHours 1)
        hourly-intervals
        (mapv (fn [^Instant hour-instant]
                (let [resolved (price/resolve-price rate-entity hour-instant zone-id)
                      hour-end (.plus hour-instant one-hour)]
                  (-> resolved
                      (assoc :tick/beginning hour-instant
                             :tick/end hour-end)
                      (with-meta {:urdb/raw (meta rate-entity)}))))
              hours)]
    (merge-intervals hourly-intervals)))

(defn price-schedule-days
  "Convenience: generate a price schedule for N days starting from an instant."
  [rate-entity ^Instant start days ^ZoneId zone-id]
  (let [end (.plus start (Duration/ofDays days))]
    (price-schedule rate-entity start end zone-id)))
