(ns com.repldriven.mono.env.config.edn
  (:require [aero.core :as aero]
            [clojure.java.io :as io])
  (:import (java.net ServerSocket)))

(defmethod aero/reader 'port
  [_ _ value]
  (if (zero? value)
    (with-open [socket (ServerSocket. 0)] (.getLocalPort socket))
    value))

(defn read-config [source profile] (aero/read-config source {:profile profile}))

(defn config [source profile] (read-config (io/file source) profile))
