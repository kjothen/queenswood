(ns com.repldriven.mono.env.interface
  (:require [com.repldriven.mono.env.core :as core]
            [com.repldriven.mono.env.config.yml :as config.yml]))

(def edn-reader core/edn-reader)
(def yml-reader config.yml/yml-reader)

(defn env
  ([] (core/env))
  ([source] (core/env source))
  ([source profile] (core/env source profile)))

