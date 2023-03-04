(ns com.repldriven.mono.system.interface
  (:refer-clojure :exclude [ref])
  (:require [com.repldriven.mono.system.core :as core]
            [com.repldriven.mono.system.reader.edn :as reader.edn]
            [com.repldriven.mono.system.reader.yml :as reader.yml]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.log.interface :as log]))

;;;; system env tagged literal readers

;;; edn
(defmethod env/edn-reader 'system
  [opts tag value]
  (reader.edn/system opts tag value))

;;; yaml
(defmethod env/yml-reader :!system/local-ref [m] (reader.yml/local-ref m))

(defmethod env/yml-reader :!system/ref [m] (reader.yml/ref m))

(defmethod env/yml-reader :!system/required-component
  [m]
  (reader.yml/required-component m))

;;;; system interface

(defn system? [config-name] (core/system? config-name))

(def required-component core/required-component)

(defn ref [kws] (core/ref kws))

(defn local-ref [kws] (core/local-ref kws))

(defn start
  ([config-name] (core/start config-name))
  ([config-name custom-config] (core/start config-name custom-config))
  ([config-name custom-config component-ids]
   (core/start config-name custom-config component-ids)))

(defn start!
  ([atom config-name] (reset! atom (start config-name)) nil)
  ([atom config-name custom-config]
   (reset! atom (start config-name custom-config))
   nil)
  ([atom config-name custom-config component-ids]
   (reset! atom (start config-name custom-config component-ids))
   nil))

(defn instance [system kws] (core/instance system kws))

(defn config [system group component] (core/config system group component))

(defn stop [system] (core/stop system))

(defn stop! [atom] (reset! atom (stop @atom)) nil)

(defn suspend [system] (core/suspend system))

(defn resume [system] (core/resume system))

(defn merge-component-config
  [component config]
  (core/merge-component-config component config))

;; imported code from juxt/clip
(defmacro with-system
  "Takes a binding and a system like with-open, and tries to close the
   system."
  [[binding system-config] & body]
  `(let [system-config# ~system-config
         system# (try (start system-config#)
                      (catch Exception e#
                        (log/error (format "Unable to start system, %s" e#))))
         ~binding system#]
     (when system# (try ~@body (finally (stop system#))))))
