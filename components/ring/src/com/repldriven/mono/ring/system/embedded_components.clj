(ns com.repldriven.mono.ring.system.embedded-components
  (:require [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.system.interface :as system]
            [ring.adapter.jetty :as jetty])
  (:import (org.eclipse.jetty.server Server)))

(def default-jetty-adapter-options {:join? false})

(def jetty-adapter
  "Component definition for a
  [ring jetty adapter](https://ring-clojure.github.io/ring/ring.adapter.jetty.html)
  in a [donut.system](https://github.com/donut-power/system)"
  {:system/start (fn [{:keys [config instance]}]
                   (or instance
                     (let [{:keys [handler options]} config]
                       (log/info "Starting jetty adapter:" handler options)
                       (jetty/run-jetty handler options))))
   :systen/stop  (fn [{:keys [^Server instance]}]
                   (when (some? instance)
                     (.stop instance)))
   :system/config  {:handler system/required-component
                    :options default-jetty-adapter-options}})
