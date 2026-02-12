(ns com.repldriven.mono.symmetric-key-api.main-test
  (:require
    ;; system components
    com.repldriven.mono.server.interface

    [com.repldriven.mono.symmetric-key-api.main :as SUT]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]

    [clojure.test :as test :refer [deftest is testing]]))

(deftest main-test
  (testing "System should start and stop without anomalies"
    (let [sys (SUT/start "classpath:symmetric-key-api/test-application.yml"
                         :test)]
      (is (not (error/anomaly? sys)) "System should start")
      (is (system/system? sys) "System should be valid")
      (when (system/system? sys) (is (not (error/anomaly? (SUT/stop sys))))))))
