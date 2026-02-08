(ns com.repldriven.mono.symmetric-key-api.system-test
  (:require
   com.repldriven.mono.server.interface

   [com.repldriven.mono.system.interface :as system]
   [com.repldriven.mono.test-system.interface :as test-system]

   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once
  (test-system/fixture "classpath:symmetric-key-api/test-application.yml" :test)
  (fn [f] (system/with-*sys* test-system/*sysdef* (f))))

(deftest system-test
  (testing "Developers should be able to start a symmetric-key-api system from a REPL"
    (is (some? system/*sys*))))
