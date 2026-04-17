(ns urdb.schedule
  "TOU schedule matrix operations.
   Schedule matrices are 12x24 (month x hour) arrays of period indices.
   Works with coerced rate entities using :urdb.rate/* keys.")

(defn lookup
  "Look up the period index for a given month (0-11) and hour (0-23)
   in a 12x24 schedule matrix."
  [schedule month hour]
  (get-in schedule [month hour]))

(defn period-at
  "Determine the energy rate period index for a coerced rate entity
   given month (0-11), hour (0-23), and weekday/weekend flag.
   Returns the period index (int), or 0 for flat rates."
  [rate month hour weekend?]
  (let [schedule (if weekend?
                   (:urdb.rate/energy-weekend-schedule rate)
                   (:urdb.rate/energy-weekday-schedule rate))]
    (if schedule
      (lookup schedule month hour)
      0)))

(defn distinct-periods
  "Return the set of distinct period indices used in a schedule matrix."
  [schedule]
  (when schedule
    (into #{} (mapcat identity) schedule)))

(defn period-hours
  "Return the [month hour] pairs assigned to a given period index."
  [schedule period-index]
  (when schedule
    (for [month (range 12)
          hour  (range 24)
          :when (= period-index (get-in schedule [month hour]))]
      [month hour])))

(defn schedule-transitions
  "Find all hour boundaries where the period changes within a given month.
   Returns a seq of {:hour h :from-period p1 :to-period p2}."
  [schedule month]
  (when schedule
    (let [row (get schedule month)]
      (for [hour (range 1 24)
            :let [prev (get row (dec hour))
                  curr (get row hour)]
            :when (not= prev curr)]
        {:hour hour
         :from-period prev
         :to-period curr}))))
