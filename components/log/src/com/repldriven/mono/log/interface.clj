(ns com.repldriven.mono.log.interface
  (:require [com.repldriven.mono.log.config :as config]
            [com.repldriven.mono.log.core :as core]))

(defn init
  ([] (config/init :default))
  ([profile] (config/init profile)))

(defmacro info
  [& args]
  `(core/info ~args))

(defmacro warn
  [& args]
  `(core/warn ~args))

(defmacro error
  [& args]
  `(core/error ~args))
