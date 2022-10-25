(ns com.repldriven.mono.pulsar.interface-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.pulsar.env-reader]
            [com.repldriven.mono.pulsar.interface :as SUT]
            [com.repldriven.mono.system.interface :as system
             :refer [with-system]])
  (:import (java.util.concurrent TimeUnit)
           (org.apache.pulsar.client.admin PulsarAdmin)
           (org.apache.pulsar.client.api Consumer Producer)))

(defn env-fixture
  [f]
  (env/set-env! (io/resource "pulsar/test-env.edn") :test)
  (f))

(use-fixtures :once env-fixture)

(deftest development-test
  (testing "Developers ld be able to start/stop a pulsar system from  REPL"
    (with-system [sys (SUT/configure-system (get-in @env/env [:system :pulsar]))]
      (is (some? (system/instance sys [:pulsar :reader])))
      (is (some? (system/instance sys [:pulsar :producer])))
      (let [producer (system/instance sys [:pulsar :producer])
            consumer (system/instance sys [:pulsar :consumer])
            msg (.getBytes "my-message")]
        (.send producer msg)
        (is (= msg
               (.. consumer (receive 500 TimeUnit/MILLISECONDS) (getData))))))))

(comment
  (env/set-env! (io/resource "pulsar/test-env.edn") :test)
  (def system-config (SUT/configure-system (get-in @env/env [:system :pulsar])))
  (def running-system (system/start system-config))

  (def ^Producer producer (system/instance running-system [:pulsar :producer]))
  (def ^Consumer consumer (system/instance running-system [:pulsar :consumer]))

  (.. producer (newMessage) (value "my-message") (send))
  (.. consumer (receive 500 TimeUnit/MILLISECONDS) (getData))

  (system/stop running-system)

  )
