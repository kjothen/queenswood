(ns com.repldriven.mono.utility.collections
  (:require
   [clojure.walk :refer [postwalk]]))

(defn yaml-collections->edn-collections
  "Convert YAML-specific collection types to standard Clojure collections.
  Converts OrderedMaps to hash-maps and seqs to vectors."
  [form]
  (postwalk #(cond (= "class flatland.ordered.map.OrderedMap" (str (type %)))
                   (into (hash-map) %)
                   (seq? %) (into (vector) %)
                   :else %)
            form))

(defn keys->strs
  "Convert all map keys to strings recursively."
  [form]
  (postwalk #(if (map? %)
               (into (hash-map) (map (fn [[k v]] [(name k) v]) %))
               %)
            form))
