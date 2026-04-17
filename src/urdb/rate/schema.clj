(ns urdb.rate.schema
  "Coerced entity Malli schemas — the public contract for rate entities.")

(def Tier
  "A normalized pricing tier."
  [:map
   [:urdb.tier/rate :double]
   [:urdb.tier/max {:optional true} :double]])

(def RateStructure
  "Array of periods, each an array of tiers."
  [:vector [:vector Tier]])

(def ScheduleMatrix
  "12x24 int matrix (month x hour -> period index)."
  [:vector {:min 12 :max 12}
   [:vector {:min 24 :max 24} :int]])

(def Sector
  [:enum
   :urdb.sector/residential
   :urdb.sector/commercial
   :urdb.sector/industrial
   :urdb.sector/lighting])

(def Rate
  "A coerced URDB tariff entity."
  [:map
   [:urdb.rate/label :string]
   [:urdb.rate/utility {:optional true} :string]
   [:urdb.rate/name {:optional true} :string]
   [:urdb.rate/sector {:optional true} Sector]
   [:urdb.rate/approved :boolean]
   [:urdb.rate/is-default :boolean]

   ;; Dates (epoch seconds)
   [:urdb.rate/start-date {:optional true} :int]
   [:urdb.rate/end-date {:optional true} :int]

   ;; Energy rates
   [:urdb.rate/energy-rate-structure {:optional true} RateStructure]
   [:urdb.rate/energy-weekday-schedule {:optional true} ScheduleMatrix]
   [:urdb.rate/energy-weekend-schedule {:optional true} ScheduleMatrix]

   ;; Demand rates
   [:urdb.rate/demand-rate-structure {:optional true} RateStructure]
   [:urdb.rate/demand-weekday-schedule {:optional true} ScheduleMatrix]
   [:urdb.rate/demand-weekend-schedule {:optional true} ScheduleMatrix]

   ;; Flat demand
   [:urdb.rate/flat-demand-structure {:optional true} RateStructure]
   [:urdb.rate/flat-demand-months {:optional true} [:vector :int]]

   ;; Fixed charges
   [:urdb.rate/fixed-charge-first-meter {:optional true} :double]
   [:urdb.rate/fixed-charge-additional {:optional true} :double]
   [:urdb.rate/min-charge {:optional true} :double]
   [:urdb.rate/annual-min-charge {:optional true} :double]

   ;; Fuel adjustments
   [:urdb.rate/fuel-adjustments {:optional true} [:vector :double]]])

(def PriceInterval
  "A resolved price for a contiguous time span.
   Also a tick interval (has :tick/beginning and :tick/end)."
  [:map
   [:tick/beginning inst?]
   [:tick/end inst?]
   [:urdb.interval/price :double]
   [:urdb.interval/period :int]
   [:urdb.interval/period-label :string]
   [:urdb.interval/tier :int]
   [:urdb.interval/unit [:enum :urdb.unit/dollar-per-kwh]]])
