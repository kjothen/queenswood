(ns com.repldriven.mono.test.interface
  (:require
    [com.repldriven.mono.test.core :as core]))

(defn refute-anomaly [anom] (core/refute-anomaly anom))
