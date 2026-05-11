(ns com.repldriven.mono.bank-test-api-scenarios.refs
  "Symbolic value resolution inside scenario step data.

  Steps may embed `[:ref :alias :k1 :k2 ...]` markers that the
  runner expands against a captures map. The captures map is
  populated by steps that include `:as <alias>`: the alias key in
  the map points to a value (typically the parsed response body)
  that later steps can read into."
  (:require
    [clojure.walk :as walk]))

(defn ref?
  [x]
  (and (vector? x) (= :ref (first x))))

(defn resolve-ref
  [captures [_ alias & path]]
  (let [bound (get captures alias)]
    (if (seq path) (get-in bound path) bound)))

(defn resolve-all
  "Walk `form`, replacing every `[:ref ...]` marker with its
  captured value. Non-ref data passes through unchanged."
  [captures form]
  (walk/postwalk
   (fn [x] (if (ref? x) (resolve-ref captures x) x))
   form))
