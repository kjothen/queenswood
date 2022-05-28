(ns com.repldriven.mono.ring.system
  (:require [com.repldriven.mono.ring.embedded-components :as embedded-components]
            [com.repldriven.mono.system.interface :as system]))

(defn- deep-merge
  [& values]
  (if (every? map? values)
    (apply merge-with deep-merge values)
    (last values)))

(defn jetty-adapter-component
  [config]
  (update-in embedded-components/jetty-adapter [:conf :options]
    (fn [options] (deep-merge options (get-in config [:jetty-adapter :options])))))

(defn create
  [config]
  {system/defs
   {:ring
    {:jetty-adapter (jetty-adapter-component config)}}})
