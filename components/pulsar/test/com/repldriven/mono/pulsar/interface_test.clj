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
    (let [system-config (SUT/configure-system (get-in @env/env [:system :pulsar]))]
      (try
        (let [running-system (system/start system-config)]
          (try
            (is (some? (system/instance running-system [:pulsar :reader])))
            (catch Exception e
              (assert false (format "Unable to get pulsar reader, %s" e)))
            (finally
              (system/stop running-system))))
        (catch Exception e
          (assert false (format "Unable to start system, %s" e)))))))

(comment
  (def system-config (SUT/configure-system (get-in @env/env [:system :pulsar])))
  (get-in system-config [:system/defs :pulsar :container-service-port :system/config])
  (def running-system (system/start system-config))
  (system/instance running-system [:pulsar :reader])
  (keys running-system)
  (keys (get-in (:donut.system/resolved-defs running-system)
               [:pulsar :container-service-port :donut.system/config]))

  (system/stop running-system)
  )
