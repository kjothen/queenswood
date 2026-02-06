(ns com.repldriven.mono.mqtt.component-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.repldriven.mono.mqtt.interface :as SUT]
            [com.repldriven.mono.system.interface :as system]
            [com.repldriven.mono.test-system.interface :as test-system]))

(defn with-system-fixture
  [f]
  (system/with-*sys* test-system/*sysdef*
    (f)))

(use-fixtures :once
  (test-system/fixture "classpath:mqtt/test-application.yml" :test)
  with-system-fixture)

(deftest mqtt-publish-subscribe-test
  (let [client (system/instance system/*sys* [:mqtt :client])
        topic "Hello"
        message "World"
        p (promise)]
    (SUT/subscribe client
                   {topic 0}
                   (fn [_ _ ^bytes payload]
                     (deliver p (String. payload "UTF-8"))))
    (SUT/publish client topic message)
    (is (= @p message))))
