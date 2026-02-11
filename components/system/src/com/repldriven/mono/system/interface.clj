(ns com.repldriven.mono.system.interface
  (:refer-clojure :exclude [ref])
  (:require [com.repldriven.mono.system.core :as core]
            [com.repldriven.mono.system.configurator :as configurator]
            [com.repldriven.mono.system.reader.edn]
            [com.repldriven.mono.system.reader.yml]
            [com.repldriven.mono.log.interface :as log]))

;;;; system interface

(def ^:dynamic *sys* nil)

(defn system? [config-name] (core/system? config-name))

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

(def required-component core/required-component)

(def component configurator/component)

(defmacro defcomponents
  [ns-keyword component-map]
  `(configurator/defcomponents ~ns-keyword ~component-map))

(defn definition
  [config]
  (configurator/definition config))

(defn merge-component-config
  [component config]
  (configurator/merge-component-config component config))

(defmacro with-*sys*
  "Starts a system from the given system definition, binds it to *sys*,
   executes the body, and stops the system.

   Usage:
     (with-*sys* system-definition
       (instance *sys* [:server :jetty-adapter]))"
  [system-config & body]
  `(let [system# (start ~system-config)]
     (try
       (push-thread-bindings {#'*sys* system#})
       ~@body
       (finally
         (pop-thread-bindings)
         (when system# (stop system#))))))

(defmacro with-sysdefs [[sym env-path profile] & body]
  `(core/with-sysdefs [~sym ~env-path ~profile] ~@body))

(defmacro with-sys [[sym sysdef] & body]
  `(core/with-sys [~sym ~sysdef] ~@body))
