(ns com.repldriven.mono.blocking-command-api.system-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [com.repldriven.mono.mqtt.interface]
            [com.repldriven.mono.pubsub.interface]
            [com.repldriven.mono.system.interface :as system]
            [com.repldriven.mono.test-system.interface :as test-system]))

(use-fixtures :once
  (test-system/fixture "classpath:blocking-command-api/application-test.yml" :test))

(deftest system-test
  (testing "System configuration and lifecycle"
    (is (system/system? test-system/*sysdef*))
    (let [sys-config (update-in test-system/*sysdef* [:system/defs :server] dissoc :jetty-adapter)]
      (system/with-*sys* sys-config
        (is (some? system/*sys*))))))
