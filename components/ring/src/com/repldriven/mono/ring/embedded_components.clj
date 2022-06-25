(ns com.repldriven.mono.ring.embedded-components
  (:require [com.repldriven.mono.system.interface :as system]
            [ring.adapter.jetty :as jetty])
  (:import (org.eclipse.jetty.server Server)))

(def default-jetty-adapter-options {:join? false})

(def jetty-adapter
  "Component definition for a
  [ring jetty adapter](https://ring-clojure.github.io/ring/ring.adapter.jetty.html)
  in a [donut.system](https://github.com/donut-power/system)"
  {:start (fn [{:keys [handler options]} instance _]
            (or instance (jetty/run-jetty handler options)))
   :stop  (fn [_ ^Server instance _]
            (when (some? instance) (.stop instance)))
   :conf  {:handler system/required-component
           :options default-jetty-adapter-options}})
