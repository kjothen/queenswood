(ns com.repldriven.mono.env.core
  (:require [aero.core :as aero]
            [clojure.java.io :as io])
  (:import (java.net ServerSocket)))

(def reader aero/reader)

(defmethod aero/reader 'port
  [_ _ value]
  (if (zero? value)
    (with-open [socket (ServerSocket. 0)] (.getLocalPort socket))
    value))

(defn config
  [source profile]
  (aero/read-config (io/file source) {:profile profile}))

(def env (atom nil))

(defn set-env!
  ([] (set-env! "env.edn"))
  ([source] (set-env! source :default))
  ([source profile] (reset! env (config source profile))))

(defn reset-env! [conf] (reset! env conf))
