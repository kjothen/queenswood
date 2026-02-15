(ns com.repldriven.mono.pulsar.pulsar.producer
  (:refer-clojure :exclude [send])
  (:require
    [com.repldriven.mono.pulsar.pulsar.schemas :as schemas]

    [com.repldriven.mono.log.interface :as log]

    [clojure.data.json :as json]
    [clojure.java.data :as j])
  (:import
    (java.util Map)
    (org.apache.pulsar.client.api Producer
                                  ProducerBuilder
                                  PulsarClient
                                  PulsarClientException
                                  Schema)
    (org.apache.pulsar.shade.org.apache.avro.generic GenericData$Record
                                                     GenericRecord)))

(defn- add-encryption-keys
  [^ProducerBuilder producer ks]
  (reduce #(.addEncryptionKey ^ProducerBuilder %1 %2) producer ks))

(defn- map->generic-record
  "Convert a Clojure map to an Avro GenericRecord using the provided schema."
  [^Schema pulsar-schema data]
  (when (map? data)
    (let [schema-info (.getSchemaInfo pulsar-schema)
          schema-def-str (.toString schema-info)
          schema-def-json (json/read-str schema-def-str)
          avro-schema-map (get schema-def-json "schema")
          avro-schema-json-str (json/write-str avro-schema-map)
          avro-schema (org.apache.pulsar.shade.org.apache.avro.Schema/parse ^String avro-schema-json-str)
          ^GenericRecord record (GenericData$Record. avro-schema)]
      (doseq [[k v] data]
        (.put record (name k) v))
      record)))

(defn create
  [{:keys [^PulsarClient client conf schemas]}]
  (try (log/info "Creating pulsar producer" conf schemas)
       (let [{:keys [cryptoKeyReader encryptionKeys schema]} conf
             manual-conf [:cryptoKeyReader :encryptionKeys :schema]
             conf-str-keys (into {} (map (fn [[k v]] [(name k) v]) conf))
             auto-conf
             (j/to-java Map (apply dissoc conf-str-keys (map name manual-conf)))
             resolved-schema (when (some? schema)
                              (schemas/resolve schemas schema))
             instance (if resolved-schema
                        (.. client (newProducer resolved-schema))
                        (.. client newProducer))
             ^ProducerBuilder builder (.. instance (loadConf auto-conf))
             ^ProducerBuilder builder-with-conf
             (cond-> builder
               (some? cryptoKeyReader) (.cryptoKeyReader cryptoKeyReader)
               (some? encryptionKeys) (add-encryption-keys encryptionKeys))]
         {:producer (.create builder-with-conf)
          :schema resolved-schema})
       (catch PulsarClientException e
         (log/error (format "Failed to create pulsar producer, %s" e)))))

(defn send
  ([producer-map data]
   (let [{:keys [^Producer producer schema]} producer-map
         converted-data (if (and schema (map? data))
                          (map->generic-record schema data)
                          data)]
     (.send producer converted-data)))
  ([producer-map data opts]
   (let [{:keys [^Producer producer schema]} producer-map
         converted-data (if (and schema (map? data))
                          (map->generic-record schema data)
                          data)]
     (.. producer newMessage (loadConf opts) (value converted-data) send))))

(defn send-async
  ([producer-map data]
   (let [{:keys [^Producer producer schema]} producer-map
         converted-data (if (and schema (map? data))
                          (map->generic-record schema data)
                          data)]
     (.sendAsync producer converted-data)))
  ([producer-map data opts]
   (let [{:keys [^Producer producer schema]} producer-map
         converted-data (if (and schema (map? data))
                          (map->generic-record schema data)
                          data)]
     (.. producer newMessage (loadConf opts) (value converted-data) sendAsync))))
