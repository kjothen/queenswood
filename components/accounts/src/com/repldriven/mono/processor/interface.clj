(ns com.repldriven.mono.processor.interface
  (:require
   [com.repldriven.mono.processor.core :as core]))

(defn process
  "Process an account command and return result or anomaly."
  [config command]
  (core/process config command))
