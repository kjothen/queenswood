(ns com.repldriven.mono.test-system.core
  (:require
   [com.repldriven.mono.env.interface :as env]
   [com.repldriven.mono.system.interface :as system]))

(defn fixture
  "Creates a test fixture that loads an environment and constructs a system definition.

  Usage:
    (use-fixtures :once (test-system/fixture \"classpath:my-app/test-env.yml\" :test))
    (use-fixtures :once (test-system/fixture \"classpath:my-app/test-env.yml\" :test my-configure-fn))

  The environment will be bound to *env* and the system definition to *sysdef*.
  Tests should use system/with-*sys* with *sysdef* to start systems as needed.

  Optionally provide a configure-fn that takes the system config and returns a system definition.
  If not provided, uses system/defs."
  ([env-path profile env-var sysdef-var]
   (fixture env-path profile env-var sysdef-var system/defs))
  ([env-path profile env-var sysdef-var configure-fn]
   (fn [f]
     (let [environment (env/config env-path profile)
           sys-def (configure-fn environment)]
       (try
         (push-thread-bindings {env-var environment sysdef-var sys-def})
         (f)
         (finally
           (pop-thread-bindings)))))))
