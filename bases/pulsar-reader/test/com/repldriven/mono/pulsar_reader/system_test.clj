(ns com.repldriven.mono.pulsar-reader.system-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [com.repldriven.mono.pulsar-reader.system :as SUT]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.system.interface :as system]))

(deftest system-test
  (testing "System configuration MUST be valid"
    (env/set-env! (io/resource "pulsar-reader/test-env.edn") :test)
    (is (= true (system/system? (SUT/configure (:system @env/env)))))))