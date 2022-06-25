(ns com.repldriven.mono.pulsar-reader.main-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.pulsar.interface :as pulsar]
            [com.repldriven.mono.pulsar-reader.main :as SUT]
            [com.repldriven.mono.system.interface :as system])
  (:import (org.apache.pulsar.client.admin PulsarAdmin)))

(defn env-fixture
  [f]
  (env/set-env! (io/resource "pulsar-reader/test-env.edn") :test)
  (f))

(use-fixtures :once env-fixture)

(deftest development-test
  (testing "Developers should be able to start and stop a pulsar system from the REPL"
    (let [system-config (pulsar/create-system (get-in @env/env [:system :pulsar]))]
      (try
        (let [booted-system (system/start system-config nil #{[:pulsar :admin]})]
          (Thread/sleep 5000) ; admin tenant api does not work until clusters are up...
          (try
            (let [^PulsarAdmin admin (system/instance booted-system [:pulsar :admin])
                  topic-names (get-in @env/env [:system :pulsar :reader :config "topicNames"])]
              (doseq [topic-name topic-names] (pulsar/ensure-topic admin topic-name))
              (try
                (let [_ (SUT/start! booted-system)]
                  (Thread/sleep 1000)
                  (SUT/stop!)
                  (is (= 1 1)))
                (catch Exception e
                  (assert false (format "Unable to fully start SUT, %s" e)))))
            (catch Exception e
              (assert false (format "Unable to operate SUT, %s" e)))
            (finally
              (system/stop booted-system))))
        (catch Exception e
          (assert false (format "Unable to boot SUT, %s" e)))))))
