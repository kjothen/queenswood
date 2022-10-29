(ns com.repldriven.mono.pulsar.system.components
  (:refer-clojure :exclude [name namespace type])
  (:require [clojure.data.json :as json]
            [clojure.java.data :as j]
            [clojure.java.data.builder :as builder]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.system.interface :as system]
            [com.repldriven.mono.pulsar.admin :as admin]
            [com.repldriven.mono.pulsar.crypto :as crypto])
  (:import [java.util Map]
           [org.apache.pulsar.client.api
            ClientBuilder Consumer MessageId
            PulsarClient PulsarClientException
            Producer Reader Schema]
           [org.apache.pulsar.client.api.schema
            SchemaDefinition]
           [org.apache.pulsar.client.admin
            PulsarAdmin PulsarAdminBuilder
            PulsarAdminException]
           [org.apache.pulsar.common.protocol.schema PostSchemaPayload]))

(defn- start [instance start-fn] (or instance (start-fn)))
(defn- stop [instance stop-fn] (when (some? instance) (stop-fn)))

(declare resolve-schema)

;; admin
(defn- ^PulsarAdmin start-admin
  [config]
  (let [{:keys [service-http-url]} config]
    (try
      (log/info "Opening pulsar admin connection:" service-http-url)
      (builder/to-java PulsarAdmin
                       (PulsarAdmin/builder)
                       {:serviceHttpUrl service-http-url}
                       {:builder-class PulsarAdminBuilder})
      (catch PulsarAdminException e
        (log/error (format "Failed to open pulsar admin connection, %s" e))))))

(defn- stop-admin
  [^PulsarAdmin instance]
  (try
    (log/info "Closing pulsar admin connection")
    (.close instance)
    (catch PulsarAdminException e
      (log/error (format "Failed to close pulsar admin connection, %s" e)))))

(def admin
  {:system/start (fn [{:system/keys [config instance]}]
                   (start instance #(start-admin config)))
   :system/post-start (fn [_] (Thread/sleep 5000))
   :system/stop (fn [{:system/keys [instance]}]
                  (stop instance #(stop-admin instance)))
   :system/config {:service-http-url system/required-component}})

;; client
(defn- ^PulsarClient start-client
  [config]
  (let [{:keys [service-url]} config]
    (try
      (log/info "Opening pulsar client connection:" service-url)
      (builder/to-java PulsarClient
                       (PulsarClient/builder)
                       {:serviceUrl service-url}
                       {:builder-class ClientBuilder})
      (catch PulsarClientException e
        (log/error (format "Failed to open pulsar client connection, %s" e))))))

(defn- stop-client
  [^PulsarClient instance]
  (try
    (log/info "Closing pulsar client connection")
    (.close instance)
    (catch PulsarClientException e
      (log/error (format "Failed to close pulsar client connection, %s" e)))))

(def client
  {:system/start (fn [{:system/keys [config instance]}]
                   (start instance #(start-client config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (stop instance #(stop-client instance)))
   :system/config {:service-url system/required-component}})

;; consumer
(defn- ^Consumer start-consumer
  [config]
  (let [{:keys [^PulsarClient client conf schemas]} config]
    (try
      (log/info "Opening pulsar consumer")
      (let [{:strs [cryptoKeyReader schema subscriptionName topics]} conf]
        (cond-> (.. client (newConsumer (resolve-schema schemas schema)))
          (some? cryptoKeyReader) (.cryptoKeyReader cryptoKeyReader)
          (some? subscriptionName) (.subscriptionName subscriptionName)
          (some? topics) (.topics topics)
          true (.subscribe)))
      (catch PulsarClientException e
        (log/error (format "Failed to open pulsar consumer, %s" e))))))

(defn- stop-consumer
  [^Consumer instance]
  (try
    (log/info "Closing pulsar consumer")
    (.close instance)
    (catch PulsarClientException e
      (log/error (format "Failed to close pulsar consumer, %s" e)))))

(def consumer
  {:system/start (fn [{:system/keys [config instance]}]
                   (start instance #(start-consumer config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (stop instance #(stop-consumer instance)))
   :system/config {:client system/required-component
                   :conf system/required-component}})

;; crypto / encryption
(def crypto-key-pair-generator
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (do
           (log/info "Creating pulsar crypto-key-pair-generator: " config)
           (crypto/key-pair-generator config))))
   :system/config system/required-component})

(def crypto-key-pair-file-reader
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (do
           (log/info "Creating pulsar crypto-key-pair-file-reader: " config)
           (crypto/key-pair-file-reader config))))
   :system/config system/required-component})

(def crypto-key-reader
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (do
           (log/info "Creating pulsar crypto-key-reader: " config)
           (crypto/key-reader config))))
   :system/config system/required-component})

(defn- add-encryption-keys
  [producer ks]
  (reduce #(.addEncryptionKey %1 %2) producer ks))

;; producer
(defn- ^Producer start-producer
  [config]
  (let [{:keys [^PulsarClient client conf schemas]} config]
    (try
      (log/info "Opening pulsar producer")
      (let [{:strs [cryptoKeyReader encryptionKeys schema topic]} conf]
        (cond-> (.. client (newProducer (resolve-schema schemas schema)))
          (some? cryptoKeyReader) (.cryptoKeyReader cryptoKeyReader)
          (some? encryptionKeys) (add-encryption-keys encryptionKeys)
          (some? topic) (.topic topic)
          true (.create)))
      (catch PulsarClientException e
        (log/error (format "Failed to open pulsar producer, %s" e))))))

(defn- stop-producer
  [^Producer instance]
  (try
    (log/info "Closing pulsar producer")
    (.close instance)
    (catch PulsarClientException e
      (log/error (format "Failed to close pulsar producer, %s" e)))))

(def producer
  {:system/start (fn [{:system/keys [config instance]}]
                   (start instance #(start-producer config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (stop instance #(stop-producer instance)))
   :system/config {:client system/required-component
                   :conf system/required-component}})

;; reader
(defn- ^Reader start-reader
  [config]
  (let [{:keys [^PulsarClient client conf]} config]
    (try
      (log/info "Opening pulsar reader")
      (let [{:strs [cryptoKeyReader schema startMessageId topics]
             :or {startMessageId MessageId/latest}} conf]
        (cond-> (.. client (newReader schema)
                    (startMessageId startMessageId))
          (some? cryptoKeyReader) (.cryptoKeyReader cryptoKeyReader)
          (some? topics) (.topics topics)
          true (.create)))
      (catch PulsarClientException e
        (log/error (format "Failed to open pulsar reader, %s" e))))))

(defn- stop-reader
  [^Reader instance]
  (try
    (log/info "Closing pulsar reader")
    (.close instance)
    (catch PulsarClientException e
      (log/error (format "Failed to close pulsar reader, %s" e)))))

(def reader
  {:system/start (fn [{:system/keys [config instance]}]
                   (start instance #(start-reader config)))
   :system/stop (fn [{:system/keys [instance]}]
                  (stop instance #(stop-reader instance)))
   :system/config {:client system/required-component
                   :conf system/required-component}})

;; schemas
(defn- build-schema-payload
  [type schema properties]
  (PostSchemaPayload.
   (or type "")
   (if (some? schema) (json/write-str schema) "")
   (or properties {})))

(defn- build-schema
  [type schema properties]
  (case type
    ;; complex schema
    ("JSON", "AVRO")
    (let [schema-definition (-> (SchemaDefinition/builder)
                                (.withJsonDef (json/write-str schema))
                                (.withJSR310ConversionEnabled true)
                                (.withAlwaysAllowNull false)
                                (.build))]
      (case type
        "JSON" (Schema/JSON schema-definition)
        "AVRO" (Schema/AVRO schema-definition)))

    ;; primitive schema
    "BOOL" (Schema/BOOL)
    "BYTEBUFFER" (Schema/BYTEBUFFER)
    "BYTES" (Schema/BYTES)
    "DATE" (Schema/DATE)
    "DOUBLE" (Schema/DOUBLE)
    "FLOAT" (Schema/FLOAT)
    "INSTANT" (Schema/INSTANT)
    "INT16" (Schema/INT16)
    "INT32" (Schema/INT32)
    "INT64" (Schema/INT64)
    "INT8" (Schema/INT8)
    "LOCAL_DATE" (Schema/LOCAL_DATE)
    "LOCAL_DATE_TIME" (Schema/LOCAL_DATE_TIME)
    "STRING" (Schema/STRING)
    "TIME" (Schema/TIME)
    "TIMESTAMP" (Schema/TIMESTAMP)

    ;; auto schema
    "AUTO_CONSUME" (Schema/AUTO_CONSUME)
    "AUTO_PRODUCE_BYTES" (Schema/AUTO_PRODUCE_BYTES)))

(defn- build-schemas
  [config]
  (reduce-kv (fn [m k {:keys [type schema properties]}]
               (let [payload (build-schema-payload type schema properties)
                     schema' (build-schema type schema properties)]
                 (assoc m k {:payload payload
                             :schema schema'})))
             {}
             config))

(defn- resolve-schema
  [schemas schema']
  (if (keyword? schema')
    (get-in schemas [schema' :schema])
    (let [{:keys [type schema properties]} schema']
      (build-schema type schema properties))))

(defn- resolve-schema-payload
  [schemas schema']
  (if (keyword? schema')
    (get-in schemas [schema' :payload])
    (let [{:keys [type schema properties]} schema']
      (build-schema-payload type schema properties))))

(def schemas
  {:system/start (fn [{:system/keys [config instance]}]
                   (start instance #(build-schemas config)))
   :system/config system/required-component})

;; topics
(defn- configure-tenants
  [^PulsarAdmin admin tenants]
  (log/info "Ensure pulsar tenants are configured:" tenants)
  (doall
   (mapv (fn [{:keys [tenant] :as opts}]
           (admin/ensure-tenant admin tenant (dissoc opts :tenant)))
         tenants)))

(defn- configure-namespaces
  [^PulsarAdmin admin namespaces]
  (log/info "Ensure pulsar namespaces are configured:" namespaces)
  (doall
   (mapv (fn [{:keys [namespace] :as opts}]
           (admin/ensure-namespace admin namespace (dissoc opts :tenant)))
         namespaces)))

(defn- configure-topics
  [^PulsarAdmin admin schemas topics]
  (log/info "Ensure pulsar topics are configured:" topics)
  (doall
   (mapv
    (fn [{:keys [topic] :as opts}]
      (let [resolved-opts (update opts :schema #(resolve-schema-payload schemas %))]
            (admin/ensure-topic admin topic (dissoc resolved-opts :topic))))
      topics)))

(def topics
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [admin tenants namespaces schemas topics]} config]
           (try
             (configure-tenants admin tenants)
             (configure-namespaces admin namespaces)
             (configure-topics admin schemas topics)
             (catch PulsarAdminException e
               (log/error (format "Failed to create pulsar topics, %s" e)))))))
   :system/config {:admin system/required-component
                   :tenants system/required-component
                   :namespaces system/required-component
                   :topics system/required-component}})
