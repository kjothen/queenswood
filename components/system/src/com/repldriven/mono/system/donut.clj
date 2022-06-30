(ns com.repldriven.mono.system.donut
  (:require [clojure.walk :as walk]
            [com.repldriven.mono.log.interface :as log]
            [donut.system :as ds]))

(def defs ::ds/defs)
(def required-component ::ds/required-component)

(def mono-systemn-ns "system")
(def donut-system-ns "donut.system")

(defn- in? [coll x] (some #(= x %) coll))
(defn match-ns-keyword? [x match-ns] (and (keyword? x) (= match-ns (namespace x))))
(defn- nsmap->nsmap
  [m from-ns to-ns]
  (walk/postwalk
   (fn [x] (if (match-ns-keyword? x from-ns)
             (keyword to-ns (name x))
             (if (fn? x)
               (fn [to-ns-map]
                 (x (reduce-kv
                     (fn [m k v] (assoc m (if (match-ns-keyword? k to-ns) (keyword (name k)) k) v))
                     {}
                     to-ns-map)))
               x))) m))

(defn ref [kws] (ds/ref kws))

(defn start
  ([config-name]
   (try
     (ds/start (nsmap->nsmap config-name mono-systemn-ns donut-system-ns))
     (catch Exception e
       (log/error (format "Error starting system, %s, %s" config-name e)))))
  ([config-name custom-config]
   (try
     (ds/start (nsmap->nsmap config-name mono-systemn-ns donut-system-ns) custom-config)
     (catch Exception e
       (log/error (format "Error starting system, %s, %s" config-name e)))))
  ([config-name custom-config component-ids]
   (try
     (ds/start (nsmap->nsmap config-name mono-systemn-ns donut-system-ns) custom-config component-ids)
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
  (update component :system/config (fn [original] (deep-merge original config))))
