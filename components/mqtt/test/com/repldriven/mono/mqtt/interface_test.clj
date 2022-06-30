(ns com.repldriven.mono.mqtt.interface-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing use-fixtures]]
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
   (let [system-config (SUT/create-system (get-in @env/env [:system :mqtt]))]
     (log/info system-config)
     (try
       (let [running-system (system/start system-config)]
         (let [client (system/instance running-system [:mqtt :client])
               topic "Hello"
               message "World"
               p (promise)]
           (SUT/subscribe client {topic 0}
                          (fn [^String topic _ ^bytes payload]
                            (deliver p (String. payload "UTF-8"))))
           (SUT/publish client topic message)
           (is (= @p message)))
         (system/stop running-system))
       (catch Exception e
         (assert false (format "Unable to boot SUT, %s" e))))))

(comment
  (env/set-env! (io/resource "mqtt/test-env.edn") :test)
  (def system-config (SUT/create-system (get-in @env/env [:system :mqtt])))
  (tap> system-config)
  (def running-system (system/start system-config))
  (def client (system/instance running-system [:mqtt :client]))
  (tap> client)
  (SUT/subscribe client {"Hello" 0} (fn [^String topic _ ^bytes payload]
                                      (println (String. payload "UTF-8"))))
  (SUT/publish client "Hello" "World")
  (system/stop running-system)

  )