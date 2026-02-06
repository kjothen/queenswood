(ns com.repldriven.mono.env.config.edn
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.net ServerSocket)))

(def reader aero/reader)

(defmethod aero/reader 'port
  [_ _ value]
  (if (zero? value)
    (with-open [socket (ServerSocket. 0)] (.getLocalPort socket))
    value))

(defn- resolve-source
  "Resolve a source string to a resource. Handles classpath: prefix."
  [source]
  (if (str/starts-with? source "classpath:")
    (io/resource (subs source (count "classpath:")))
    source))

(defn read-config [source profile] (aero/read-config (resolve-source source) {:profile profile}))

(defn config [source profile] (read-config source profile))
