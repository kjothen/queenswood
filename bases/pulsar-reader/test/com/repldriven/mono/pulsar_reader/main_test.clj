(ns com.repldriven.mono.pulsar-reader.main-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [com.repldriven.mono.pulsar-reader.main :as SUT]))

(deftest start
  (testing
   "Ops should be able to start the system from the main entry point"
   (try (SUT/-main "-c" (io/as-file (io/resource "pulsar-reader/test-env.edn"))
                   "-p" "test")
        (is (some? @SUT/system))
        (catch Exception e
          (assert false (format "Unable to start system, %s" e)))
        (finally (SUT/stop!)))))
