(ns com.repldriven.queenswood.policy.match
  (:require
    [clojure.string :as str]))

(defn none?
  [v]
  (or (nil? v)
      (and (keyword? v)
           (str/ends-with? (name v) "-unknown"))))

(defn filter-match?
  [filter request]
  (every? (fn [[k v]]
            (or (none? v)
                (= v (get request k))))
          filter))

(defn variant
  [m]
  (when (map? m) (first (keys m))))

(defn matches?
  [c kind request]
  (let [kind->fields (:kind c)]
    (and (= kind (variant kind->fields))
         (let [fields (get kind->fields kind)
               filters (:filters fields)
               other-fields (dissoc fields :filters)]
           (and (every? (fn [[k v]] (= v (get request k))) other-fields)
                (or (empty? filters)
                    (some (fn [f] (filter-match? f request)) filters)))))))
