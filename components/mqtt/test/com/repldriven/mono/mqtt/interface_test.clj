(ns com.repldriven.mono.mqtt.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.mqtt.interface :as SUT]
    [com.repldriven.mono.system.interface :as system]

    [clojure.test :refer [deftest is testing]]))

(defn- test-system
  []
  (error/nom-> (env/config "classpath:mqtt/test-application.yml" :test)
               system/defs
               system/start))

(deftest mqtt-publish-subscribe-test
  (testing "MQTT client should publish and subscribe to messages"
    (system/with-system [sys (test-system)]
      (let [client (system/instance sys [:mqtt :client])
            topic "Hello"
            message "World"
            p (promise)]
        (SUT/subscribe client
                       {topic 0}
                       (fn [_ _ ^bytes payload]
                         (deliver p (String. payload "UTF-8"))))
        (SUT/publish client topic message)
        (is (= @p message))))))
