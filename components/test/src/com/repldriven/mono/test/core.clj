(ns com.repldriven.mono.test.core
  (:require
    [clojure.test :refer [is]]
    [com.repldriven.mono.error.interface :as error]))

(defn refute-anomaly
  "Fails the test if value is an anomaly, showing its kind and message."
  [v]
  (is (not (error/anomaly? v))
      (format "Unexpected anomaly [%s]: %s"
              (error/kind v)
              (or (:message v) (pr-str v)))))
