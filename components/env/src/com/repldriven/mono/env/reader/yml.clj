(ns com.repldriven.mono.env.reader.yml
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.walk :refer [postwalk]]
            [clj-yaml.core :as yaml]
            [flatland.ordered.map]
            [com.repldriven.mono.env.reader.edn :as reader.edn]))

(defmulti yml-reader (fn [m] (keyword (get m :tag))))

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

(defn keys->strs
  [form]
  (postwalk #(if (map? %) (into (hash-map) (map (fn [[k v]] [(name k) v]) %)) %)
            form))

(defmulti tag-reader (fn [m] (keyword (get m :tag))))

;; Basic common tags
(defmethod tag-reader :!profile
  [{:keys [value]}]
  (symbol (str "#profile " (yaml-collections->edn-collections value))))

(defmethod tag-reader :!port [{:keys [value]}] (symbol (str "#port " value)))

(defmethod tag-reader :!include
  [{:keys [value]}]
  (let [key-fn (fn [{:keys [key]}]
                 (if (and (str/starts-with? key "\"") (str/ends-with? key "\""))
                   (subs key 1 (dec (count key)))
                   (keyword key)))]
    (-> value
        io/resource
        io/reader
        (yaml/parse-stream {:key-fn key-fn :unknown-tag-fn tag-reader})
        yaml-collections->edn-collections)))

(defmethod tag-reader :!strs [{:keys [value]}] (keys->strs value))

(defmethod tag-reader :!str [{:keys [value]}] (str "\"" (name value) "\""))

(defmethod tag-reader :!keyword [{:keys [value]}] (keyword value))

(defmethod tag-reader :default [m] (:value m))

(defn key-fn
  [{:keys [key]}]
  (if (and (str/starts-with? key "\"") (str/ends-with? key "\""))
    (subs key 1 (dec (count key)))
    (keyword key)))

(defn- resolve-source
  "Resolve a source string to a resource. Handles classpath: prefix."
  [source]
  (if (str/starts-with? source "classpath:")
    (io/resource (subs source (count "classpath:")))
    source))

(defn config
  [source profile]
  (-> (resolve-source source)
      io/reader
      (yaml/parse-stream {:key-fn key-fn :unknown-tag-fn tag-reader})
      yaml-collections->edn-collections
      str
      string->stream
      (reader.edn/read-config profile)))
