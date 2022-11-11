(ns com.repldriven.mono.ring.system.embedded-components
  (:require [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.system.interface :as system]
            [ring.adapter.jetty :as jetty])
  (:import (org.eclipse.jetty.server Server)))

(defn- assoc-if-missing
  [m ks v]
  (if (or (contains? m ks) (nil? v)) m (assoc-in m ks v)))

(defn- interceptor
  [k v]
  {:enter (fn [ctx] (assoc-if-missing ctx [:request k] v))})

(def interceptors
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (do
           (log/info "Building interceptors:" config)
           (reduce-kv (fn [coll k v] (conj coll (interceptor k v))) [] config))))
   :system/config nil})

(def default-jetty-adapter-options {:join? false})

(def jetty-adapter
  {:system/start
   (fn [{:system/keys [config instance]}]
     (or instance
         (let [{:keys [handler interceptors options]} config]
           (log/info "Starting jetty adapter:" handler interceptors options)
           (jetty/run-jetty (handler {:interceptors interceptors}) options))))
   :system/stop
   (fn [{:system/keys [^Server instance]}]
     (when (some? instance)
       (.stop instance)))
   :system/config
   {:handler system/required-component
    :interceptors nil
    :options default-jetty-adapter-options}})
