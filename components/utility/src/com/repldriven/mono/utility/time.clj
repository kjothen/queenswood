(ns com.repldriven.mono.utility.time
  (:import
    (java.time Instant)))

(defn now
  "Current wall-clock time as epoch millis. Use for persisted
  `created-at` / `updated-at` values that callers expect as
  `int64` (the protobuf / FDB representation we use everywhere)."
  ^long []
  (System/currentTimeMillis))

(defn now-rfc3339
  "Current wall-clock time as an ISO-8601 / RFC-3339 string
  (`2026-04-24T12:34:56.789Z`). Use for persisted timestamps
  stored as strings."
  ^String []
  (str (Instant/now)))
