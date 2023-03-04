(ns com.repldriven.mono.iam-api.system-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [com.repldriven.mono.iam-api.system :as SUT]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.system.interface :as system]))

(deftest configuration
  (testing "System yaml configuration MUST be valid"
           (env/set-env! (io/resource "iam-api/test-application.yml") :test)
           (is (= true (system/system? (SUT/configure (:system @env/env))))))
  (testing "System edn configuration MUST be valid"
           (env/set-env! (io/resource "iam-api/test-env.edn") :test)
           (is (= true (system/system? (SUT/configure (:system @env/env)))))))
