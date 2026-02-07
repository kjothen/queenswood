(ns com.repldriven.mono.env.interface
  (:require [com.repldriven.mono.env.core :as core]))

(def edn-reader core/edn-reader)
(def yml-reader core/yml-reader)

(defn env
  ([] (core/env))
  ([source] (core/env source))
  ([source profile] (core/env source profile)))

