(ns com.repldriven.mono.system.donut
  (:require [com.repldriven.mono.log.interface :as log]
            [donut.system :as ds]))

(def defs ::ds/defs)
(def required-component ::ds/required-component)

(defn ref
  [kws]
  (ds/ref kws))

(defn start
  ([config-name]
   (try
     (ds/start config-name)
     (catch Exception e
       (log/error (format "Error starting system, %s, %s" config-name e)))))
  ([config-name custom-config]
   (try
     (ds/start config-name custom-config)
     (catch Exception e
       (log/error (format "Error starting system, %s, %s" config-name e)))))
  ([config-name custom-config component-ids]
   (try
     (ds/start config-name custom-config component-ids)
     (catch Exception e
       (log/error (format "Error starting system, %s, %s" config-name e))))))

(defn instance
  [system kws]
  (get-in system (vec (cons ::ds/instances kws))))

(defn stop
  [system]
  (try
    (ds/stop system)
    (catch Exception e
      (log/error (format "Error stopping system, %s, %s" system e)))))

(defn suspend
  [system]
  (ds/suspend system))

(defn resume
  [system]
  (ds/resume system))
