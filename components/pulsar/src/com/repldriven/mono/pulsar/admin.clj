(ns com.repldriven.mono.pulsar.admin
  (:require [clojure.string :as string]
            [com.repldriven.mono.log.interface :as log])
  (:import (org.apache.pulsar.client.admin Namespaces PulsarAdmin Tenants Topics)
           (org.apache.pulsar.common.naming TopicName)
           (org.apache.pulsar.common.policies.data TenantInfo TenantInfoImpl)))

(defn- ensure-tenant
  [^PulsarAdmin admin roles clusters tenant-name]
  (let [^Tenants tenants (.tenants admin)
        tenant-names (.getTenants tenants)]
    (when-not (contains? (set tenant-names) tenant-name)
      (let [^TenantInfo tenant-info (TenantInfoImpl. (set roles) (set clusters))]
        (log/info "Creating tenant: " tenant-name)
        (.createTenant tenants tenant-name tenant-info)))))

(defn- ensure-namespace
  [^PulsarAdmin admin tenant-name namespace-name]
  (let [^Namespaces namespaces (.namespaces admin)
        namespace-names (.getNamespaces namespaces tenant-name)
        fully-qualified-namespace-name (string/join "/" [tenant-name namespace-name])]
    (when-not (contains? (set namespace-names) fully-qualified-namespace-name)
      (do (log/info "Creating namespace: " fully-qualified-namespace-name)
          (.createNamespace namespaces fully-qualified-namespace-name)))))

(defn ensure-topic
  [^PulsarAdmin admin fully-qualified-topic-name &
   {:keys [roles clusters partitions], :or {roles [], clusters ["standalone"], partitions 1}}]
  (let [^TopicName topic-name (TopicName/get fully-qualified-topic-name)
        tenant-name (.getTenant topic-name)
        fully-qualified-namespace-name (.getNamespace topic-name)
        namespace-name (last (string/split fully-qualified-namespace-name #"/"))]
    (ensure-tenant admin roles clusters tenant-name)
    (ensure-namespace admin tenant-name namespace-name)
    (let [^Topics topics (.topics admin)
          domain-name (.getDomain topic-name)
          topic-names (.getList topics fully-qualified-namespace-name domain-name)]
      (when-not (contains? (set topic-names) fully-qualified-topic-name)
        (log/info "Creating topic: " fully-qualified-topic-name)
        (.createPartitionedTopic topics fully-qualified-topic-name partitions)))))
