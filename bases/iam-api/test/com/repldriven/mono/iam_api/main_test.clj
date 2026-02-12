(ns com.repldriven.mono.iam-api.main-test
  (:require
    ;; system components
    com.repldriven.mono.db.interface
    com.repldriven.mono.server.interface
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.iam-api.main :as SUT]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]

    [clojure.test :as test :refer [deftest is testing]]))

(deftest main-test
  (testing "System should start and stop without anomalies"
    (let [sys (SUT/start "classpath:iam-api/test-application.yml" :test)]
      (is (not (error/anomaly? sys)) "System should start")
      (is (system/system? sys) "System should be valid")
      (when (system/system? sys) (is (not (error/anomaly? (SUT/stop sys))))))))
