(ns urdb.price-test
  (:require [clojure.test :refer [deftest is testing]]
            [urdb.rate :as rate]
            [urdb.price :as price])
  (:import [java.time Instant ZoneId]))

(def pacific (ZoneId/of "America/Los_Angeles"))

(def tou-rate
  (rate/coerce
   {:label "tou-price-test"
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
   {:label "flat-price-test"
    :utility "Test"
    :name "Flat"
    :sector "Residential"
    :approved true
    :energyratestructure [[{:rate 0.12}]]}))

(def rate-with-fuel-adj
  (rate/coerce
   {:label "fuel-adj-test"
    :utility "Test"
    :name "With Fuel"
    :sector "Residential"
    :approved true
    :energyratestructure [[{:rate 0.10}]]
    :fueladjustmentsmonthly [0.01 0.01 0.01 0.02 0.02 0.03
                             0.03 0.03 0.02 0.02 0.01 0.01]}))

(deftest resolve-price-tou-weekday
  (testing "on-peak (2pm Pacific on a Tuesday)"
    ;; 2026-04-14 is a Tuesday. 2pm Pacific = 21:00 UTC
    (let [instant (Instant/parse "2026-04-14T21:00:00Z")
          result  (price/resolve-price tou-rate instant pacific)]
      (is (= 0.25 (:urdb.interval/price result)))
      (is (= 2 (:urdb.interval/period result)))
      (is (= "On-Peak" (:urdb.interval/period-label result)))
      (is (= :urdb.unit/dollar-per-kwh (:urdb.interval/unit result)))))

  (testing "off-peak (3am Pacific on a Tuesday)"
    ;; 3am Pacific = 10:00 UTC
    (let [instant (Instant/parse "2026-04-14T10:00:00Z")
          result  (price/resolve-price tou-rate instant pacific)]
      (is (= 0.08 (:urdb.interval/price result)))
      (is (= 0 (:urdb.interval/period result))))))

(deftest resolve-price-tou-weekend
  (testing "weekend always off-peak"
    ;; 2026-04-18 is a Saturday. 2pm Pacific = 21:00 UTC
    (let [instant (Instant/parse "2026-04-18T21:00:00Z")
          result  (price/resolve-price tou-rate instant pacific)]
      (is (= 0.08 (:urdb.interval/price result)))
      (is (= 0 (:urdb.interval/period result))))))

(deftest resolve-price-flat
  (testing "flat rate returns same price regardless of time"
    (let [r1 (price/resolve-price flat-rate
                                  (Instant/parse "2026-04-14T21:00:00Z") pacific)
          r2 (price/resolve-price flat-rate
                                  (Instant/parse "2026-04-14T10:00:00Z") pacific)]
      (is (= 0.12 (:urdb.interval/price r1)))
      (is (= 0.12 (:urdb.interval/price r2))))))

(deftest resolve-price-with-fuel-adjustment
  (testing "fuel adjustment added to base rate"
    ;; June (month index 5) has 0.03 fuel adj
    ;; June 15 2026 is a Monday. Noon Pacific = 19:00 UTC
    (let [instant (Instant/parse "2026-06-15T19:00:00Z")
          result  (price/resolve-price rate-with-fuel-adj instant pacific)]
      (is (= 0.13 (:urdb.interval/price result))))))
