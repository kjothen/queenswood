(ns com.repldriven.mono.env.reader.edn
  (:require [aero.core :as aero]
            [com.repldriven.mono.utility.interface :as util])
  (:import (java.net ServerSocket)))

;; edn-reader multimethod (extends aero/reader)
(def edn-reader aero/reader)

(defmethod aero/reader 'port
  [_ _ value]
  (if (zero? value)
    (with-open [socket (ServerSocket. 0)] (.getLocalPort socket))
    value))

(defn read-config [source profile] (aero/read-config (util/resolve-source source) {:profile profile}))

(defn config [source profile] (read-config source profile))
