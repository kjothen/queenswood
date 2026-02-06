(ns com.repldriven.mono.blocking-command-api.system-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [com.repldriven.mono.system.interface :as system]
            [com.repldriven.mono.test-system.interface :as test-system]))

(use-fixtures :once
  (test-system/fixture "classpath:blocking-command-api/application-test.yml" :test))

(deftest configuration
  (testing "System configuration MUST be valid"
    (is (system/system? test-system/*sysdef*))))
