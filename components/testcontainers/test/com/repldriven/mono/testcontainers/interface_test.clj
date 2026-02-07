(ns com.repldriven.mono.testcontainers.interface-test
  (:require
   com.repldriven.mono.testcontainers.interface

   [com.repldriven.mono.system.interface :as system]
   [com.repldriven.mono.test-system.interface :as test-system]

   [clojure.test :as test :refer [deftest is testing use-fixtures]]))

(defn with-system-fixture
  [f]
  (system/with-*sys* test-system/*sysdef*
    (f)))

(use-fixtures :once
  (test-system/fixture "classpath:testcontainers/test-application.yml" :test)
  with-system-fixture)

(deftest testcontainers-test
  (testing
   "Developers should be able to start and stop a testcontainers system"
    (is (= [8080 8081]
           (keys (system/instance system/*sys* [:helloworld :container-mapped-ports]))))
    (is (= (system/instance system/*sys* [:helloworld :container-mapped-exposed-port])
           (get (system/instance system/*sys* [:helloworld :container-mapped-ports])
                8080)))))
