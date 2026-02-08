(ns com.repldriven.mono.utility.interface
  (:require
   [com.repldriven.mono.utility.collections :as util.collections]
   [com.repldriven.mono.utility.string :as util.string]))

(defn deep-merge
  "Recursively merges maps. If all values are maps, merges them recursively.
  Otherwise returns the last value."
  [& values]
  (if (every? map? values)
    (apply merge-with deep-merge values)
    (last values)))

;; String utilities
(def string->stream util.string/string->stream)
(def resolve-source util.string/resolve-source)

;; Collection utilities
(def yaml-collections->edn-collections util.collections/yaml-collections->edn-collections)
(def keys->strs util.collections/keys->strs)
