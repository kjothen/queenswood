(ns com.repldriven.mono.system.core
  (:refer-clojure :exclude [ref])
  (:require
   [com.repldriven.mono.env.interface :as env]
   [com.repldriven.mono.log.interface :as log]
   [com.repldriven.mono.system.configurator :as configurator]

   [donut.system :as ds]

   [clojure.walk :as walk]))

(def mono-system-ns "system")
(def donut-system-ns "donut.system")

(defn- match-ns-keyword?
  [x match-ns]
  (and (keyword? x) (= match-ns (namespace x))))

(defn- nsmap->nsmap
  [m from-ns to-ns]
  (walk/postwalk
   (fn [x]
     (if (match-ns-keyword? x from-ns)
       (keyword to-ns (name x))
       (if (fn? x)
         (fn [to-ns-map]
           (let [args (reduce-kv (fn [m k v]
                                   (assoc m
                                          (if (match-ns-keyword? k to-ns)
                                            (keyword from-ns (name k))
                                            k)
                                          v))
                                 {}
                                 to-ns-map)]
             (x args)))
         x)))
   m))

(def required-component ::ds/required-component)

(defn ref [kws] (ds/ref kws))

(defn local-ref [kws] (ds/local-ref kws))

(defn system?
  [config-name]
  (ds/system? (nsmap->nsmap config-name mono-system-ns donut-system-ns)))

(defn start
  ([config-name]
   (try (ds/start (nsmap->nsmap config-name mono-system-ns donut-system-ns))
        (catch Exception e (log/error (format "Error starting system: %s" e)))))
  ([config-name custom-config]
   (try (ds/start (nsmap->nsmap config-name mono-system-ns donut-system-ns)
                  custom-config)
        (catch Exception e (log/error (format "Error starting system: %s" e)))))
  ([config-name custom-config component-ids]
   (try (ds/start (nsmap->nsmap config-name mono-system-ns donut-system-ns)
                  custom-config
                  component-ids)
        (catch Exception e
          (log/error (format "Error starting system: %s" e))))))

(defn instance [system kws] (get-in system (vec (cons ::ds/instances kws))))

(defn config
  [system group component]
  (get-in system [::ds/defs group component ::ds/config]))

(defn stop
  [system]
  (try (ds/stop system)
       (catch Exception e (log/error (format "Error stopping system: %s" e)))))

(defn suspend [system] (ds/suspend system))

(defn resume [system] (ds/resume system))

(defmacro with-sysdefs
  "Loads environment and creates sysdef, binds it to the provided symbol.

   Usage:
     (use-fixtures :once
       (fn [f]
         (with-sysdefs sysdefs \"classpath:app/test.yml\" :test
           (with-sys sys sysdefs
             (f)))))"
  [binding env-path profile & body]
  `(let [environment# (env/env ~env-path ~profile)
         ~binding (configurator/definition (:system environment#))]
     ~@body))

(defmacro with-sys
  "Starts a system from the given sysdef, binds it to the provided symbol,
   executes the body, and stops the system.

   Usage:
     (with-sysdefs sysdefs \"classpath:app/test.yml\" :test
       (with-sys sys sysdefs
         (f)))"
  [binding sysdef & body]
  `(let [~binding (start ~sysdef)]
     (try
       ~@body
       (finally
         (when ~binding (stop ~binding))))))
