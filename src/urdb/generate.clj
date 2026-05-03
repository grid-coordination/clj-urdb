(ns urdb.generate
  "Generate contiguous price schedules for time windows.
   Produces sequences of PriceInterval entities with :tick/beginning and
   :tick/end as java.time.ZonedDateTime in the caller-supplied zone."
  (:require [urdb.price :as price])
  (:import [java.time Instant ZoneId ZonedDateTime Duration]
           [java.time.temporal ChronoUnit]))

(defn- hour-start
  "Truncate a ZonedDateTime to the start of its hour."
  [^ZonedDateTime zdt]
  (.truncatedTo zdt ChronoUnit/HOURS))

(defn- hour-zdts
  "Generate a seq of ZonedDateTimes at the start of each hour from start to end.
   Both bounds are Instants; the returned ZonedDateTimes are in zone-id, with
   DST-correct hour arithmetic via ZonedDateTime#plusHours."
  [^Instant start ^Instant end ^ZoneId zone-id]
  (let [first-hour (hour-start (.atZone start zone-id))]
    (->> (iterate #(.plusHours ^ZonedDateTime % 1) first-hour)
         (take-while #(.isBefore (.toInstant ^ZonedDateTime %) end)))))

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
     :tick/beginning, :tick/end           — java.time.ZonedDateTime in zone-id
                                            bounding the interval
     :urdb.interval/price, /period, etc. — resolved price data

   Adjacent hours with the same price and period are merged into
   single contiguous intervals. Hour stepping uses ZonedDateTime arithmetic,
   so spring-forward / fall-back days produce 23 / 25 hourly buckets."
  [rate-entity ^Instant start ^Instant end ^ZoneId zone-id]
  (let [hours (hour-zdts start end zone-id)
        hourly-intervals
        (mapv (fn [^ZonedDateTime hour-zdt]
                (let [resolved (price/resolve-price rate-entity
                                                    (.toInstant hour-zdt)
                                                    zone-id)
                      hour-end (.plusHours hour-zdt 1)]
                  (-> resolved
                      (assoc :tick/beginning hour-zdt
                             :tick/end hour-end)
                      (with-meta {:urdb/raw (meta rate-entity)}))))
              hours)]
    (merge-intervals hourly-intervals)))

(defn price-schedule-days
  "Convenience: generate a price schedule for N days starting from an instant.
   See `price-schedule` for the returned shape — :tick/beginning and :tick/end
   are java.time.ZonedDateTime in the supplied zone-id."
  [rate-entity ^Instant start days ^ZoneId zone-id]
  (let [end (.plus start (Duration/ofDays days))]
    (price-schedule rate-entity start end zone-id)))
