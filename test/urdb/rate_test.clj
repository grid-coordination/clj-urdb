(ns urdb.rate-test
  (:require [clojure.test :refer [deftest is testing]]
            [urdb.rate :as rate]))

(def sample-raw-flat
  {:label "flat-001"
   :utility "Test Electric"
   :name "Basic Flat Rate"
   :sector "Residential"
   :approved true
   :is_default false
   :energyratestructure [[{:rate 0.12}]]})

(def sample-raw-tou
  {:label "tou-001"
   :utility "Test Electric"
   :name "TOU Rate"
   :sector "Commercial"
   :approved true
   :is_default true
   :energyratestructure [[{:rate 0.08}]          ; period 0: off-peak
                         [{:rate 0.15}]          ; period 1: mid-peak
                         [{:rate 0.25}]]         ; period 2: on-peak
   :energyweekdayschedule (vec (repeat 12 (vec (concat (repeat 8 0)    ; 00-07: off-peak
                                                       (repeat 4 1)    ; 08-11: mid-peak
                                                       (repeat 6 2)    ; 12-17: on-peak
                                                       (repeat 4 1)    ; 18-21: mid-peak
                                                       (repeat 2 0))))) ; 22-23: off-peak
   :energyweekendschedule (vec (repeat 12 (vec (repeat 24 0))))})

(def sample-raw-tiered
  {:label "tiered-001"
   :utility "Test Electric"
   :name "Tiered Rate"
   :sector "Residential"
   :approved true
   :energyratestructure [[{:rate 0.10 :max 500}
                          {:rate 0.15 :max 1000}
                          {:rate 0.20}]]})

(def sample-raw-with-adj
  {:label "adj-001"
   :utility "Test Electric"
   :name "Rate with Adjustment"
   :sector "Residential"
   :approved true
   :energyratestructure [[{:rate 0.10 :adj 0.02}]]
   :fueladjustmentsmonthly [0.01 0.01 0.01 0.02 0.02 0.03
                            0.03 0.03 0.02 0.02 0.01 0.01]})

(deftest coerce-flat-rate
  (let [rate (rate/coerce sample-raw-flat)]
    (testing "identity fields"
      (is (= "flat-001" (:urdb.rate/label rate)))
      (is (= "Test Electric" (:urdb.rate/utility rate)))
      (is (= :urdb.sector/residential (:urdb.rate/sector rate)))
      (is (true? (:urdb.rate/approved rate)))
      (is (false? (:urdb.rate/is-default rate))))

    (testing "energy rate structure"
      (is (= [[{:urdb.tier/rate 0.12}]]
             (:urdb.rate/energy-rate-structure rate))))

    (testing "no schedule matrices for flat rate"
      (is (nil? (:urdb.rate/energy-weekday-schedule rate))))

    (testing "raw metadata preserved"
      (is (= sample-raw-flat (:urdb/raw (meta rate)))))))

(deftest coerce-tou-rate
  (let [rate (rate/coerce sample-raw-tou)]
    (testing "schedule matrices present"
      (is (= 12 (count (:urdb.rate/energy-weekday-schedule rate))))
      (is (= 24 (count (first (:urdb.rate/energy-weekday-schedule rate))))))

    (testing "three periods in structure"
      (is (= 3 (count (:urdb.rate/energy-rate-structure rate)))))))

(deftest coerce-tiered-rate
  (let [rate (rate/coerce sample-raw-tiered)]
    (testing "tier max values"
      (let [tiers (first (:urdb.rate/energy-rate-structure rate))]
        (is (= 3 (count tiers)))
        (is (= 500.0 (:urdb.tier/max (first tiers))))
        (is (= 1000.0 (:urdb.tier/max (second tiers))))
        (is (nil? (:urdb.tier/max (nth tiers 2))))))))

(deftest coerce-adjustment
  (let [rate (rate/coerce sample-raw-with-adj)]
    (testing "rate includes adj"
      (is (< (abs (- 0.12 (:urdb.tier/rate (ffirst (:urdb.rate/energy-rate-structure rate))))) 1e-10)))

    (testing "fuel adjustments parsed"
      (is (= 12 (count (:urdb.rate/fuel-adjustments rate))))
      (is (= 0.03 (nth (:urdb.rate/fuel-adjustments rate) 5))))))

(deftest predicates
  (let [flat   (rate/coerce sample-raw-flat)
        tou    (rate/coerce sample-raw-tou)
        tiered (rate/coerce sample-raw-tiered)]
    (testing "flat-rate?"
      (is (true? (rate/flat-rate? flat)))
      (is (false? (rate/flat-rate? tou))))

    (testing "tou-rate?"
      (is (true? (rate/tou-rate? tou)))
      (is (false? (rate/tou-rate? flat))))

    (testing "tiered-rate?"
      (is (true? (rate/tiered-rate? tiered)))
      (is (false? (rate/tiered-rate? flat))))))

(deftest period-labels
  (is (= "Off-Peak" (rate/period-label 0)))
  (is (= "On-Peak" (rate/period-label 2)))
  (is (= "Period-5" (rate/period-label 5))))
