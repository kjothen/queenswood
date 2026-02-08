(ns com.repldriven.mono.test-system.interface
  (:require
   [com.repldriven.mono.test-system.core :as core]))

(def ^:dynamic *env* nil)
(def ^:dynamic *sysdef* nil)

(defn fixture
  ([env-path profile]
   (core/fixture env-path profile #'*env* #'*sysdef*))
  ([env-path profile configure-fn]
   (core/fixture env-path profile #'*env* #'*sysdef* configure-fn)))
