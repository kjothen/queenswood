(ns com.repldriven.mono.mqtt.interface-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is use-fixtures]]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.mqtt.interface :as SUT]
            [com.repldriven.mono.system.interface :as system]))

(defn env-fixture
  [f]
  (env/set-env! (io/resource "mqtt/test-env.edn") :test)
  (f))

(use-fixtures :once env-fixture)

(deftest dummy-test
  (let [system-def (SUT/configure-system (get-in @env/env [:system :mqtt]))]
    (log/info system-def)
    (try
      (let [running-system (system/start system-def)]
        (let [client (system/instance running-system [:mqtt :client])
              topic "Hello"
              message "World"
              p (promise)]
          (SUT/subscribe client {topic 0}
            (fn [_ _ ^bytes payload]
              (deliver p (String. payload "UTF-8"))))
          (SUT/publish client topic message)
          (is (= @p message)))
        (system/stop running-system))
      (catch Exception e
        (assert false (format "Unable to boot SUT, %s" e))))))
