(ns com.repldriven.mono.pulsar-reader.main-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.repldriven.mono.pulsar-reader.main :as SUT]))

(deftest start-test
  (testing
   "Ops should be able to start the system from the main entry point"
   (try
     (SUT/-main "-c" "classpath:pulsar-reader/test-application.yml"
                "-p" "test")
     (is (some? @SUT/system))
     (catch Exception e
       (assert false (format "Unable to start system, %s" e)))
     (finally (SUT/stop @SUT/system)))))
