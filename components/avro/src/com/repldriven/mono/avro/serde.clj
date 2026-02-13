(ns com.repldriven.mono.avro.serde
  (:require
    [deercreeklabs.lancaster :as avro]))

(defn json->schema [json] (avro/json->schema json))

(defn serialize [schema data] (avro/serialize schema data))

(defn deserialize-same [schema data] (avro/deserialize-same schema data))

