(ns com.repldriven.mono.env.interface
  (:require [com.repldriven.mono.env.core :as core]))

(def reader core/reader)

(def env core/env)

(defn set-env!
  ([] (core/set-env!))
  ([source] (core/set-env! source))
  ([source profile] (core/set-env! source profile)))

(defn reset-env! [conf] (core/reset-env! conf))
