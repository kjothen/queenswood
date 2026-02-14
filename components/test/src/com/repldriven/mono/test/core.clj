(ns com.repldriven.mono.test.core
  (:require
    [clojure.test :refer [is]]
    [com.repldriven.mono.error.interface :as error]))

(defn refute-anomaly
  "Fails the test if called with an anomaly, showing its kind and message."
  [anom]
  (is false
      (format "Unexpected anomaly [%s]: %s"
              (error/kind anom)
              (or (:message anom) (pr-str anom)))))
