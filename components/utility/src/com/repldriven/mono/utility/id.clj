(ns com.repldriven.mono.utility.id
  (:require
    [com.repldriven.mono.utility.ulid :as ulid]))

(defn generate
  "Prefixed ULID id, e.g. `pmt.01jsx6k7h0abfdv8qpm2ytn3we`.

  `prefix` is an entity marker (`pmt`, `org`, ...). The ULID
  portion is time-sortable and safe for use in URL paths without
  escaping."
  [prefix]
  (str prefix "." (ulid/monotonic)))
