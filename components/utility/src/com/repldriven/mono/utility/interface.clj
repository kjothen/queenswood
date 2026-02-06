(ns com.repldriven.mono.utility.interface)

(defn deep-merge
  "Recursively merges maps. If all values are maps, merges them recursively.
  Otherwise returns the last value."
  [& values]
  (if (every? map? values)
    (apply merge-with deep-merge values)
    (last values)))
