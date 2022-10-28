(ns com.repldriven.mono.pulsar.admin
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [com.repldriven.mono.log.interface :as log]
            [org.httpkit.client :as httpkit])
  (:import (org.apache.pulsar.client.admin Namespaces PulsarAdmin Tenants Topics)
           (org.apache.pulsar.common.naming TopicName)
           (org.apache.pulsar.common.policies.data TenantInfo TenantInfoImpl)
           (org.apache.pulsar.common.protocol.schema PostSchemaPayload)
           (org.apache.pulsar.common.schema SchemaInfo)))

(defn ensure-tenant
  [^PulsarAdmin admin tenant-name
   & {:keys [roles clusters] :or {roles [] clusters ["standalone"]}}]
  (let [^Tenants tenants (.tenants admin)
        tenant-names (.getTenants tenants)]
    (when-not (contains? (set tenant-names) tenant-name)
      (let [^TenantInfo tenant-info (TenantInfoImpl. (set roles) (set clusters))]
        (log/info "Creating tenant:" tenant-name)
        (.createTenant tenants tenant-name tenant-info)))))

(defn- configure-namespace
  [^PulsarAdmin admin fully-qualified-namespace-name config]
  (let [namespace-url (string/join "/" [(.getServiceUrl admin)
                                        "admin/v2/namespaces"
                                        fully-qualified-namespace-name])]
    (log/info "Configuring namespace:" fully-qualified-namespace-name config)
    (dorun
     (map (fn [[method settings]]
            (dorun
             (map (fn [[k v]]
                    (let [url (string/join "/" [namespace-url k])
                          body (json/write-str v)
                          headers {"Content-Type" "application/json"}
                          res @(httpkit/request {:method method
                                                 :url url
                                                 :headers headers
                                                 :body body})]
                      (log/info res)))
                  settings)))
          config))))

(defn ensure-namespace
  [^PulsarAdmin admin fully-qualified-namespace-name &
   {:keys [config]}]
  (let [^Namespaces namespaces (.namespaces admin)
        tenant-name (first (string/split fully-qualified-namespace-name #"/"))
        namespace-names (.getNamespaces namespaces tenant-name)]
    (when-not (contains? (set namespace-names) fully-qualified-namespace-name)
      (do (log/info "Creating namespace:" fully-qualified-namespace-name config)
          (.createNamespace namespaces fully-qualified-namespace-name)
          (when (some? config)
            (configure-namespace admin
                                 fully-qualified-namespace-name
                                 config))))))

(defn- ensure-topic-schema
  [^PulsarAdmin admin fully-qualified-topic-name schema]
  (log/info "Creating schema for topic:" fully-qualified-topic-name schema)
  (.createSchema (.schemas admin) fully-qualified-topic-name schema))

(defn ensure-topic
  [^PulsarAdmin admin fully-qualified-topic-name &
   {:keys [partitions schema] :or {partitions 1}}]
  (let [^TopicName topic-name (TopicName/get fully-qualified-topic-name)
        tenant-name (.getTenant topic-name)
        fully-qualified-namespace-name (.getNamespace topic-name)
        namespace-name (last (string/split fully-qualified-namespace-name #"/"))]
    (let [^Topics topics (.topics admin)
          domain-name (.getDomain topic-name)
          topic-names (.getList topics
                                fully-qualified-namespace-name
                                domain-name)]
      (when-not (contains? (set topic-names) fully-qualified-topic-name)
        (log/info "Creating topic:" fully-qualified-topic-name)
        (.createPartitionedTopic topics
                                 fully-qualified-topic-name
                                 partitions)
        (when (some? schema)
          (ensure-topic-schema admin fully-qualified-topic-name schema))))))
