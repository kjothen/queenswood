(ns com.repldriven.mono.system.donut
  (:require [clojure.walk :as walk]
            [com.repldriven.mono.log.interface :as log]
            [donut.system :as ds]))

(def defs ::ds/defs)
(def required-component ::ds/required-component)

;; (defn- in?
;;   [coll x]
;;   (some #(= x %) coll))

;; (defn- nsmap->nsmap
;;   [m from-ns to-ns]
;;   (walk/postwalk
;;    (fn [x] (if (and (keyword? x) (= from-ns (namespace x)))
;;              (keyword to-ns (name x))
;;              x))
;;    m))

;; (defn- renamespace-config
;;   [config-name]
;;   (let [result (nsmap->nsmap config-name mono-system-ns donut-system-ns)]
;;     (pprint/pprint result)
;;     result))

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

(defn- deep-merge
  [& values]
  (if (every? map? values)
    (apply merge-with deep-merge values)
    (last values)))

(defn merge-component-config
  [component config]
  (update component :conf (fn [conf] (deep-merge conf config))))
