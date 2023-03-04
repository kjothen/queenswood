(ns com.repldriven.mono.system.reader.yml
  (:require [clojure.string :as str]))

(defn local-ref
  [{:keys [value]}]
  (let [ks (str/split value #"\.")]
    (symbol (str "[#system local-ref " (pr-str (mapv keyword ks)) "]"))))

(defn ref
  [{:keys [value]}]
  (let [ks (str/split value #"\.")]
    (symbol (str "[#system ref " (pr-str (mapv keyword ks)) "]"))))

(defn required-component [_] (symbol (str "#system required-component")))
