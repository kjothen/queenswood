(ns com.repldriven.mono.widget.interface
  (:require
    [com.repldriven.mono.widget.core :as core]))

(defn start-widget-processor
  [config]
  (core/start-widget-processor config))
