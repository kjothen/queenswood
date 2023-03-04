(ns com.repldriven.mono.env.config.yml
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.walk :refer [postwalk]]
            [clj-yaml.core :as yaml]
            [flatland.ordered.map]
            [com.repldriven.mono.env.config.edn :as config.edn]))

(defn string->stream
  ([s] (string->stream s "UTF-8"))
  ([s encoding]
   (-> s
       (.getBytes encoding)
       (java.io.ByteArrayInputStream.))))

(defn yaml-collections->edn-collections
  [form]
  (postwalk #(cond (= "class flatland.ordered.map.OrderedMap" (str (type %)))
                   (into (hash-map) %)
                   (seq? %) (into (vector) %)
                   :else %)
            form))

(defmulti tag-reader (fn [m] (keyword (get m :tag))))

(defmethod tag-reader :!profile
  [{:keys [value]}]
  (symbol (str "#profile " (pr-str (yaml-collections->edn-collections value)))))

(defmethod tag-reader :!port [{:keys [value]}] (symbol (str "#port " value)))

(defmethod tag-reader :default [m] (:value m))

(def reader tag-reader)

(defn config
  [source profile]
  (-> (io/reader source)
      (yaml/parse-stream
       {:keywords true :unsafe false :unknown-tag-fn tag-reader})
      yaml-collections->edn-collections
      str
      string->stream
      (config.edn/read-config profile)))
