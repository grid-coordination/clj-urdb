(ns urdb.generate-test
  (:require [clojure.test :refer [deftest is testing]]
            [urdb.rate :as rate]
            [urdb.generate :as generate])
  (:import [java.time Instant ZoneId Duration]))

(def pacific (ZoneId/of "America/Los_Angeles"))

(def tou-rate
  (rate/coerce
   {:label "tou-gen-test"
    :utility "Test"
    :name "TOU"
    :sector "Residential"
    :approved true
    :energyratestructure [[{:rate 0.08}] [{:rate 0.15}] [{:rate 0.25}]]
    :energyweekdayschedule (vec (repeat 12 (vec (concat (repeat 8 0)
                                                        (repeat 4 1)
                                                        (repeat 6 2)
                                                        (repeat 4 1)
                                                        (repeat 2 0)))))
    :energyweekendschedule (vec (repeat 12 (vec (repeat 24 0))))}))

(def flat-rate
  (rate/coerce
   {:label "flat-gen-test"
    :utility "Test"
    :name "Flat"
    :sector "Residential"
    :approved true
    :energyratestructure [[{:rate 0.12}]]}))

(deftest price-schedule-tou-one-day
  (testing "24-hour schedule for a weekday produces merged intervals"
    ;; 2026-04-14 Tuesday, midnight to midnight Pacific
    ;; midnight Pacific (PDT) = 07:00 UTC
    (let [start (Instant/parse "2026-04-14T07:00:00Z")
          end   (.plus start (Duration/ofHours 24))
          sched (generate/price-schedule tou-rate start end pacific)]
      ;; Should have 5 intervals: off(0-8), mid(8-12), on(12-18), mid(18-22), off(22-24)
      (is (= 5 (count sched)))

      (testing "first interval is off-peak 0:00-8:00"
        (let [first-int (first sched)]
          (is (= 0.08 (:urdb.interval/price first-int)))
          (is (= 0 (:urdb.interval/period first-int)))
          (is (some? (:tick/beginning first-int)))
          (is (some? (:tick/end first-int)))))

      (testing "third interval is on-peak"
        (is (= 0.25 (:urdb.interval/price (nth sched 2))))))))

(deftest price-schedule-flat-one-day
  (testing "flat rate produces single merged interval for 24h"
    (let [start (Instant/parse "2026-04-14T07:00:00Z")
          end   (.plus start (Duration/ofHours 24))
          sched (generate/price-schedule flat-rate start end pacific)]
      (is (= 1 (count sched)))
      (is (= 0.12 (:urdb.interval/price (first sched)))))))

(deftest price-schedule-days-convenience
  (testing "price-schedule-days generates multi-day schedule"
    (let [start (Instant/parse "2026-04-14T07:00:00Z")
          sched (generate/price-schedule-days tou-rate start 2 pacific)]
      ;; 2 days should have multiple intervals
      (is (> (count sched) 1))
      ;; All intervals should have tick keys
      (is (every? :tick/beginning sched))
      (is (every? :tick/end sched)))))

(deftest contiguous-intervals
  (testing "intervals are contiguous (end of one = beginning of next)"
    (let [start (Instant/parse "2026-04-14T07:00:00Z")
          end   (.plus start (Duration/ofHours 24))
          sched (generate/price-schedule tou-rate start end pacific)]
      (doseq [[a b] (partition 2 1 sched)]
        (is (= (:tick/end a) (:tick/beginning b)))))))
