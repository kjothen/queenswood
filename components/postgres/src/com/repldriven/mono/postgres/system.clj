(ns com.repldriven.mono.postgres.system
  (:require [clojure.pprint]
            [com.repldriven.mono.postgres.local-components :as local-components]
            [com.repldriven.mono.postgres.components :as components]
            [com.repldriven.mono.system.interface :as system]))

(defn- deep-merge
  [& values]
  (if (every? map? values)
    (apply merge-with deep-merge values)
    (last values)))

(defn container-component
  [config]
  (update local-components/container :conf (fn [conf] (deep-merge conf (:container config)))))

(defn container-mapped-port-component
  [config]
  (update local-components/container-mapped-port :conf
    (fn [conf]
      (merge conf {:container (system/ref [:postgres :container])
                   :exposed-port (get-in config [:container :exposed-port])}))))

(defmulti base-components (fn [config] (when (contains? config :container) :container)))

(defmethod base-components :container
  [config]
  {:container (container-component config),
   :mapped-port (container-mapped-port-component config)})

(defmethod base-components :default [config] (select-keys config [:datasource]))

(defn datasource-component
  [config]
  (assoc-in components/datasource [:conf] {:spec (:datasource config)}))

(defn create
  [config]
  (let [service (cond-> {:postgres (base-components config)}
                  (contains? config :datasource) (assoc-in [:postgres :datasource] (datasource-component config)))]
    {system/defs service}))
