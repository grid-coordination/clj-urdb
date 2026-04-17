(ns urdb.schedule-test
  (:require [clojure.test :refer [deftest is testing]]
            [urdb.rate :as rate]
            [urdb.schedule :as schedule]))

(def tou-rate
  (rate/coerce
   {:label "tou-test"
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

(deftest lookup-test
  (let [schedule (:urdb.rate/energy-weekday-schedule tou-rate)]
    (is (= 0 (schedule/lookup schedule 0 3)))   ; 3am = off-peak
    (is (= 1 (schedule/lookup schedule 0 9)))   ; 9am = mid-peak
    (is (= 2 (schedule/lookup schedule 0 14)))  ; 2pm = on-peak
    (is (= 0 (schedule/lookup schedule 0 23))))) ; 11pm = off-peak

(deftest period-at-test
  (testing "weekday periods"
    (is (= 0 (schedule/period-at tou-rate 5 3 false)))   ; June 3am
    (is (= 2 (schedule/period-at tou-rate 5 14 false)))) ; June 2pm

  (testing "weekend always off-peak"
    (is (= 0 (schedule/period-at tou-rate 5 14 true)))))

(deftest period-at-flat-rate
  (let [flat (rate/coerce {:label "flat" :utility "T" :name "F"
                           :approved true
                           :energyratestructure [[{:rate 0.12}]]})]
    (is (= 0 (schedule/period-at flat 6 14 false)))))

(deftest distinct-periods-test
  (let [schedule (:urdb.rate/energy-weekday-schedule tou-rate)]
    (is (= #{0 1 2} (schedule/distinct-periods schedule)))))

(deftest schedule-transitions-test
  (let [schedule (:urdb.rate/energy-weekday-schedule tou-rate)
        transitions (schedule/schedule-transitions schedule 0)]
    (is (= 4 (count transitions)))
    (is (= {:hour 8 :from-period 0 :to-period 1} (first transitions)))
    (is (= {:hour 12 :from-period 1 :to-period 2} (second transitions)))))
