(ns com.repldriven.mono.blocking-command-api.interceptors
  (:require [com.repldriven.mono.system.interface :as system]))

(defn- system-interceptor
  [system-ref k ks]
  {:enter (fn [ctx]
            (if-not (contains? ctx k)
              (if-some [component (system/instance @system-ref ks)]
                (assoc ctx k component)
                ctx)
              ctx))})

(defn mqtt-client-interceptor
  [system-ref]
  (system-interceptor system-ref :mqtt-client [:mqtt :client]))
