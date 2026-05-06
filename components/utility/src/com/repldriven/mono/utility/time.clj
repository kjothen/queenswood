(ns com.repldriven.mono.utility.time
  (:import
    (java.time Instant)))

(defn now ^long [] (System/currentTimeMillis))

(defn now-rfc3339 ^String [] (str (Instant/now)))
