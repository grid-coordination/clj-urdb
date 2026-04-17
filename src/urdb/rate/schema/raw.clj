(ns urdb.rate.schema.raw
  "Raw Malli schemas mirroring the OpenEI URDB JSON response shape.
   Used for boundary validation — confirming the API returned what we expected."
  (:require [malli.core :as m]))

(def RawTier
  "A single pricing tier within a rate period."
  [:map
   [:rate {:optional true} number?]
   [:adj {:optional true} number?]
   [:max {:optional true} number?]
   [:unit {:optional true} :string]
   [:sell {:optional true} number?]])

(def RawRateStructure
  "Array of periods, each an array of tiers."
  [:vector [:vector RawTier]])

(def RawScheduleMatrix
  "12x24 int matrix (month x hour -> period index)."
  [:vector {:min 12 :max 12}
   [:vector {:min 24 :max 24} :int]])

(def RawFuelAdjustments
  "12-element monthly fuel adjustment array ($/kWh)."
  [:vector {:min 12 :max 12} number?])

(def RawRate
  "A URDB tariff record as returned by the OpenEI API."
  [:map
   ;; Identity
   [:label :string]
   [:utility {:optional true} :string]
   [:name {:optional true} :string]
   [:sector {:optional true} :string]
   [:source {:optional true} :string]
   [:uri {:optional true} :string]
   [:description {:optional true} :string]

   ;; Dates
   [:startdate {:optional true} [:or :int :nil]]
   [:enddate {:optional true} [:or :int :nil]]

   ;; Metadata
   [:approved {:optional true} :boolean]
   [:is_default {:optional true} :boolean]
   [:servicetype {:optional true} :string]

   ;; Energy rates (TOU)
   [:energyratestructure {:optional true} RawRateStructure]
   [:energyweekdayschedule {:optional true} RawScheduleMatrix]
   [:energyweekendschedule {:optional true} RawScheduleMatrix]

   ;; Demand rates
   [:demandratestructure {:optional true} RawRateStructure]
   [:demandweekdayschedule {:optional true} RawScheduleMatrix]
   [:demandweekendschedule {:optional true} RawScheduleMatrix]

   ;; Flat demand
   [:flatdemandstructure {:optional true} RawRateStructure]
   [:flatdemandmonths {:optional true} [:vector :int]]

   ;; Fixed charges
   [:fixedchargefirstmeter {:optional true} number?]
   [:fixedchargeeaaddl {:optional true} number?]
   [:mincharge {:optional true} number?]
   [:annualmincharge {:optional true} number?]

   ;; Fuel adjustments
   [:fueladjustmentsmonthly {:optional true} RawFuelAdjustments]])

(defn validate
  "Validate a raw rate record. Returns nil if valid, explanation if not."
  [raw]
  (m/explain RawRate raw))
