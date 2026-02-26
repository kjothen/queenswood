(ns com.repldriven.mono.avro.serde
  (:require
    [com.repldriven.mono.error.interface :as error]

    [deercreeklabs.lancaster :as avro]

    [clojure.string :as str]
    [clojure.walk :as walk]))

(defn- str->avro-key [s] (keyword (str/replace s "_" "-")))

(defn- avro-key->str [k] (str/replace (name k) "-" "_"))

(defn- stringify-keys
  [m]
  (walk/postwalk
   (fn [x] (if (map? x) (into {} (map (fn [[k v]] [(avro-key->str k) v])) x) x))
   m))

(defn- keywordize-keys
  [m]
  (walk/postwalk
   (fn [x] (if (map? x) (into {} (map (fn [[k v]] [(str->avro-key k) v])) x) x))
   m))

(defn json->schema
  [json]
  (error/try-nom :avro/json->schema
                 "Failed to parse Avro schema from JSON"
                 (avro/json->schema json)))

(defn serialize
  [schema data]
  (error/try-nom :avro/serialize
                 "Failed to serialize data to Avro"
                 (avro/serialize schema (keywordize-keys data))))

(defn deserialize-same
  [schema data]
  (error/try-nom :avro/deserialize-same
                 "Failed to deserialize Avro data"
                 (stringify-keys (avro/deserialize-same schema data))))

