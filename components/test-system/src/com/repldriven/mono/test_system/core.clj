(ns com.repldriven.mono.test-system.core
  (:require [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.system.interface :as system]))

(defn fixture
  "Creates a test fixture that loads an environment and constructs a system definition.

  Usage:
    (use-fixtures :once (test-system/fixture \"classpath:my-app/test-env.yml\" :test))

  The environment will be bound to *env* and the system definition to *sysdef*.
  Tests should use system/with-*sys* with *sysdef* to start systems as needed."
  [env-path profile env-var sysdef-var]
  (fn [f]
    (let [environment (env/env env-path profile)
          sys-def (system/definition (:system environment))]
      (try
        (push-thread-bindings {env-var environment sysdef-var sys-def})
        (f)
        (finally
          (pop-thread-bindings))))))
