(ns com.repldriven.mono.env.core
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.repldriven.mono.env.config.edn :as config.edn]
            [com.repldriven.mono.env.config.yml :as config.yml])
  (:import (java.net ServerSocket)
           (java.net URL)))

(def env (atom nil))

(def edn-reader config.edn/reader)
(def yml-reader config.yml/reader)

(defn file-type->keyword
  [source]
  (cond (str/ends-with? source ".edn") :edn
        (str/ends-with? source ".yml") :yml
        (str/ends-with? source ".yaml") :yml
        :else (throw (ex-info "Unknown file type" {:source source}))))

(defmulti file-type (fn [source] (type source)))
(defmethod file-type java.net.URL
  [source]
  (file-type->keyword (.getPath source)))
(defmethod file-type java.lang.String [source] (file-type->keyword source))

(defmethod file-type :default
  [source]
  (throw (ex-info "Cannot detect file type" {:source source})))

(defmulti config (fn [source _] (file-type source)))
(defmethod config :edn [source profile] (config.edn/config source profile))
(defmethod config :yml [source profile] (config.yml/config source profile))
(defmethod config :default
  [source _]
  (throw (ex-info "Unsupported config file type" {:source source})))

(defn set-env!
  ([] (set-env! "env.edn"))
  ([source] (set-env! source :default))
  ([source profile] (reset! env (config source profile))))

(defn reset-env! [conf] (reset! env conf))
