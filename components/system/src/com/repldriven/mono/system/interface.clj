(ns com.repldriven.mono.system.interface
  (:refer-clojure :exclude [ref])
  (:require [com.repldriven.mono.system.donut :as donut]
            [com.repldriven.mono.system.env-reader :as env-reader]
            [com.repldriven.mono.env.interface :as env]))

(defmethod env/reader 'system
  [opts tag value]
  (env-reader/system opts tag value))

(defn system?
  [config-name]
  (donut/system? config-name))

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

(defn config
  [system group component]
  (get-in system [:donut.system/defs group component :donut.system/config]))

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

;; imported code from juxt/clip
(defmacro with-system
  "Takes a binding and a system like with-open, and tries to close the
   system even when stopping causes an exception.
   If an exception is thrown during start, then the partially started
   system will be stopped and the exception rethrown.  If stopping that
   system throws an exception, a new exception will be thrown with the
   original exception as the cause, and the stopping exception under
   `::stop-exception`.
   If an exception is thrown during the final stop, then the exception
   will be thrown."
  [[binding system-config] & body]
  `(let [system-config# ~system-config
         system#
         (try
           (start system-config#)
           (catch Exception e#
             (when-let [partial-system# (::system (ex-data e#))]
               (try
                 (stop partial-system#)
                 (catch Exception e-stop#
                   (throw
                    (ex-info
                     (str "Exception thrown while starting system, "
                          "failed to stop partially started system.")
                     {::stop-exception e-stop#}
                     e#))
                   )))
             (throw e#)))
         ~binding system#]
     (try
       ~@body
       (finally
         (stop system#)))))
