(ns com.repldriven.mono.pulsar.interface-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.pulsar.env-reader]
            [com.repldriven.mono.pulsar.interface :as SUT]
            [com.repldriven.mono.system.interface :as system])
  (:import (org.apache.pulsar.client.admin PulsarAdmin)))

(defn env-fixture
  [f]
  (env/set-env! (io/resource "pulsar/test-env.edn") :test)
  (f))

(use-fixtures :once env-fixture)

(deftest development-test
  (testing "Developers should be able to start and stop a pulsar system from the REPL"
    (let [system-config (SUT/create-system (get-in @env/env [:system :pulsar]))]
      (try
        (let [booted-system (system/start system-config nil #{[:pulsar :admin]})]
          (Thread/sleep 5000) ; admin tenant api does not work until clusters are up...
          (try
            (let [^PulsarAdmin admin (system/instance booted-system [:pulsar :admin])
                  topic-names (get-in @env/env [:system :pulsar :reader "topicNames"])]
              (doseq [topic-name topic-names] (SUT/ensure-topic admin topic-name))
              (try
                (let [running-system (system/start booted-system)]
                  (Thread/sleep 1000)
                  (system/stop running-system)
                  (is (= 1 1)))
                (catch Exception e
                  (assert false (format "Unable to fully start SUT, %s" e)))))
            (catch Exception e
              (assert false (format "Unable to operate SUT, %s" e)))
            (finally
              (system/stop booted-system))))
        (catch Exception e
          (assert false (format "Unable to boot SUT, %s" e)))))))
