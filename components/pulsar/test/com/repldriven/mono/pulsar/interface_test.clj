(ns com.repldriven.mono.pulsar.interface-test
  (:refer-clojure :exclude [send])
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
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
  (testing "Developers should be able to start/stop a pulsar system from  REPL"
    (with-system [sys (SUT/configure-system (get-in @env/env [:system :pulsar]))]
      (let [producer (system/instance sys [:pulsar :producer])
            consumer (system/instance sys [:pulsar :consumer])
            msg {:y "42"}]
        (.. producer (send (.getBytes (json/write-str msg))))
        (is (= msg
               (json/read-str (.. consumer (receive 500 TimeUnit/MILLISECONDS)
                                  getValue getJsonNode toString)
                              :key-fn keyword)))))))

(comment
  (env/set-env! (io/resource "pulsar/test-env.edn") :test)
  (def system-config (SUT/configure-system (get-in @env/env [:system :pulsar])))
  (def running-system (system/start system-config))

  (def ^Producer producer (system/instance running-system [:pulsar :producer]))
  (def ^Consumer consumer (system/instance running-system [:pulsar :consumer]))

  (def msg {:y "42"})
  (.. producer (send (.getBytes (json/write-str msg))))
  (.. producer getStats getNumMsgsSent)

  (def recv-msg (.. consumer (receive 500 TimeUnit/MILLISECONDS)))
  (.. consumer getStats getNumMsgsReceived)
  (.. consumer (acknowledge recv-msg))
  (= (json/read-str (.. recv-msg getValue getJsonNode toString) :key-fn keyword)
     msg)
  (system/stop running-system)
  )
