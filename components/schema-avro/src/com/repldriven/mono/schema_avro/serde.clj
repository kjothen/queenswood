(ns com.repldriven.mono.schema-avro.serde
  (:require
   [deercreeklabs.lancaster :as avro]))

(defn json->schema [json] (avro/json->schema json))

(defn serialize [schema data] (avro/serialize schema data))

(defn deserialize-same [schema data] (avro/deserialize-same schema data))

(comment
  (require '[clojure.java.io :as io] '[clojure.data.json :as json])
  (json->schema (-> "schema-avro/user.avsc"
                    io/resource
                    io/file
                    slurp)))
