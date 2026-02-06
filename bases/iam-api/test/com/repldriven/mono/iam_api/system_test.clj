(ns com.repldriven.mono.iam-api.system-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.server.interface]
            [com.repldriven.mono.sql.interface]
            [com.repldriven.mono.system.interface :as system]))

(deftest configuration
  (testing "System yaml configuration MUST be valid"
    (let [environment (env/env "classpath:iam-api/test-application.yml" :test)
          sys-def (system/definition (:system environment))]
      (is (system/system? sys-def))))
  (testing "System edn configuration MUST be valid"
    (let [environment (env/env "classpath:iam-api/test-env.edn" :test)
          sys-def (system/definition (:system environment))]
      (is (system/system? sys-def)))))
