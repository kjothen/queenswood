(ns com.repldriven.mono.testcontainers-system.interface-test
  (:require [clojure.test :as test :refer [deftest is testing use-fixtures]]
            [com.repldriven.mono.system.interface :as system]
            [com.repldriven.mono.test-system.interface :as test-system]))

(defn with-system-fixture
  [f]
  (system/with-*sys* test-system/*sysdef*
    (f)))

(use-fixtures :once
  (test-system/fixture "classpath:testcontainers_system/test-application.yml" :test)
  with-system-fixture)

(deftest testcontainers-system-test
  (testing
   "Developers should be able to start and stop a testcontainers system"
   (is (= [8080 8081]
          (keys (system/instance system/*sys* [:helloworld :container-mapped-ports]))))
   (is (= (system/instance system/*sys* [:helloworld :container-mapped-exposed-port])
          (get (system/instance system/*sys* [:helloworld :container-mapped-ports])
               8080)))))
