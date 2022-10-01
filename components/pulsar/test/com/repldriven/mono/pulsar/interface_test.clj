(ns com.repldriven.mono.pulsar.interface-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.pulsar.env-reader]
            [com.repldriven.mono.pulsar.interface :as SUT]
            [com.repldriven.mono.system.interface :as system :refer [with-system]])
  (:import (org.apache.pulsar.client.admin PulsarAdmin)))

(defn env-fixture
  [f]
  (env/set-env! (io/resource "pulsar/test-env.edn") :test)
  (f))

(use-fixtures :once env-fixture)

(deftest development-test
  (testing "Developers should be able to start/stop a pulsar system from a REPL"
    (with-system [sys (SUT/configure-system (get-in @env/env [:system :pulsar]))]
      (is (some? (system/instance sys [:pulsar :reader]))))))
