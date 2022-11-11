(ns com.repldriven.mono.blocking-command-api.system-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [com.repldriven.mono.blocking-command-api.system :as SUT]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.system.interface :as system]))

(deftest configuration
  (testing "System configuration MUST be valid"
    (env/set-env! (io/resource "blocking-command-api/test-env.edn") :test)
    (is (= true (system/system? (SUT/configure (:system @env/env)))))))
