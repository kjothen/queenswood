(ns com.repldriven.mono.pulsar.system
  (:require [clojure.pprint]
            [com.repldriven.mono.pulsar.local-components :as local-components]
            [com.repldriven.mono.pulsar.components :as components]
            [com.repldriven.mono.system.interface :as system]))

(defn- deep-merge
  [& values]
  (if (every? map? values)
    (apply merge-with deep-merge values)
    (last values)))

(defn container-component
  [config]
  (update local-components/container :conf (fn [conf] (deep-merge conf (:container config)))))

(defn container-service-url-component
  [config]
  (-> local-components/service-url
    (assoc-in [:conf :container] (system/ref [:pulsar :container]))
    (update :conf (fn [conf] (deep-merge conf (:service-url config))))))

(defn container-service-http-url-component
  [config]
  (-> local-components/service-http-url
    (assoc-in [:conf :container] (system/ref [:pulsar :container]))
    (update :conf (fn [conf] (deep-merge conf (:service-http-url config))))))

(defmulti base-components (fn [config] (when (contains? config :container) :container)))

(defmethod base-components :container
  [config]
  {:container (container-component config),
   :service-url (container-service-url-component config),
   :service-http-url (container-service-http-url-component config)})

(defmethod base-components :default [config] (select-keys config [:service-url :service-http-url]))

(defn admin-component
  [_]
  (assoc-in components/admin [:conf] {:service-http-url (system/ref [:pulsar :service-http-url])}))

(defn client-component
  [_]
  (assoc-in components/client [:conf] {:service-url (system/ref [:pulsar :service-url])}))

(defn reader-component
  [config]
  (assoc-in components/reader [:conf] {:client (system/ref [:pulsar :client]), :config (:reader config)}))

(defn create
  [config]
  (let [service (cond-> {:pulsar (base-components config)}
                  (contains? config :admin) (assoc-in [:pulsar :admin] (admin-component config))
                  (contains? config :client) (assoc-in [:pulsar :client] (client-component config))
                  (contains? config :reader) (assoc-in [:pulsar :reader] (reader-component config)))]
    {system/defs service}))
