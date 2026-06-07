(ns com.repldriven.mono.utility.time
  (:import
    (java.time Instant LocalDate ZoneOffset)))

(defn now ^long [] (System/currentTimeMillis))

(defn now-rfc3339 ^String [] (str (Instant/now)))

(defn today
  "Current UTC calendar day as an epoch-day (long), derived from
  `now` so it tracks the same clock."
  ^long []
  (.toEpochDay (LocalDate/ofInstant (Instant/ofEpochMilli (now))
                                    ZoneOffset/UTC)))
