(ns ^:eftest/synchronized com.repldriven.mono.blocking-command-api.main-test
  (:require
    ;; system components
   com.repldriven.mono.server.interface
   com.repldriven.mono.testcontainers.interface

   [com.repldriven.mono.blocking-command-api.main :as SUT]
   [com.repldriven.mono.error.interface :as error]
   [com.repldriven.mono.system.interface :as system]

   [clojure.test :as test :refer [deftest is testing]]))

#_(deftest main-test
    (testing "System should start and stop without anomalies"
      (let [sys (SUT/start "classpath:blocking-command-api/application-test.yml"
                           :test)]
        (is (not (error/anomaly? sys)) "System should start")
        (is (system/system? sys) "System should be valid")
        (when (system/system? sys)
          (is (not (error/anomaly? (SUT/stop sys))))))))



