(ns com.repldriven.mono.pubsub.pulsar.tenants
  (:require [com.repldriven.mono.log.interface :as log])
  (:import (org.apache.pulsar.client.admin PulsarAdmin Tenants)
           (org.apache.pulsar.common.policies.data TenantInfo TenantInfoImpl)))

(defn- create
  [^PulsarAdmin admin tenant-name
   & {:keys [roles clusters] :or {roles [] clusters ["standalone"]}}]
  (let [^Tenants tenants (.tenants admin)
        tenant-names (.getTenants tenants)]
    (when-not (contains? (set tenant-names) tenant-name)
      (let [^TenantInfo tenant-info (TenantInfoImpl. (set roles) (set clusters))]
        (log/info "Creating tenant:" tenant-name)
        (.createTenant tenants tenant-name tenant-info)))))

(defn create-tenants
  [{:keys [^PulsarAdmin admin tenants]}]
  (log/info "Ensure pulsar tenants exist:" tenants)
  (doall (mapv (fn [{:keys [tenant] :as opts}]
                 (create admin tenant (dissoc opts :tenant)))
           tenants)))
