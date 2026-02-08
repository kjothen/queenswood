(ns com.repldriven.mono.blocking-command-api.main-test
  (:require
   com.repldriven.mono.testcontainers.interface
   com.repldriven.mono.server.interface

   [clojure.test :as test :refer [deftest is testing]]
   [com.repldriven.mono.blocking-command-api.main :as SUT]
   [com.repldriven.mono.mqtt.interface]
   [com.repldriven.mono.pulsar.interface]))

(deftest start-test
  (testing
   "Ops should be able to start the system from the main entry point"
    (try
      (SUT/-main "-c" "classpath:blocking-command-api/application-test.yml"
                 "-p" "test")
      (is (some? @SUT/system))
      (catch Exception e (assert false (format "Unable to start system, %s" e)))
      (finally (SUT/stop @SUT/system)))))
