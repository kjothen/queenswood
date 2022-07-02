(ns com.repldriven.mono.system.interface
  (:refer-clojure :exclude [ref])
  (:require [com.repldriven.mono.system.donut :as donut]
            [com.repldriven.mono.system.env-reader :as env-reader]
            [com.repldriven.mono.env.interface :as env]))

(defmethod env/reader 'system
  [opts tag value]
  (env-reader/system opts tag value))

(def required-component donut/required-component)

(defn ref
  [kws]
  (donut/ref kws))

(defn start
  ([config-name]
   (donut/start config-name))
  ([config-name custom-config]
   (donut/start config-name custom-config))
  ([config-name custom-config component-ids]
   (donut/start config-name custom-config component-ids)))

(defn start!
  ([atom config-name]
   (reset! atom (start config-name))
   nil)
  ([atom config-name custom-config]
   (reset! atom (start config-name custom-config))
   nil)
  ([atom config-name custom-config component-ids]
   (reset! atom (start config-name custom-config component-ids))
   nil))

(defn instance
  [system kws]
  (donut/instance system kws))

(defn stop
  [system]
  (donut/stop system))

(defn stop!
  [atom]
  (reset! atom (stop @atom))
  nil)

(defn suspend
  [system]
  (donut/suspend system))

(defn resume
  [system]
  (donut/resume system))

(defn merge-component-config
  [component config]
  (donut/merge-component-config component config))
