(ns com.repldriven.mono.pulsar-reader.system-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [com.repldriven.mono.pubsub.interface]
            [com.repldriven.mono.system.interface :as system]
            [com.repldriven.mono.test-system.interface :as test-system]))

(use-fixtures :once
  (test-system/fixture "classpath:pulsar-reader/test-application.yml" :test)
  (fn [f] (system/with-*sys* test-system/*sysdef* (f))))

(deftest system-test
  (testing "Developers should be able to start a pulsar reader system from a REPL"
    (is (some? system/*sys*))))
