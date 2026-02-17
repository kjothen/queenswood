(ns ^:eftest/synchronized com.repldriven.mono.pulsar-mqtt-processor.main-test
  (:require
   com.repldriven.mono.mqtt.interface
   com.repldriven.mono.pulsar.interface
   com.repldriven.mono.testcontainers.interface

   [com.repldriven.mono.pulsar-mqtt-processor.main :as SUT]
   [com.repldriven.mono.pulsar-mqtt-processor.processor :as processor]

   [com.repldriven.mono.error.interface :as error]
   [com.repldriven.mono.system.interface :as system]

   [clojure.core.async :as async]
   [clojure.test :refer [deftest is testing]]))

(deftest main-test
  (testing "System should start and process commands"
    (let [sys (SUT/start "classpath:pulsar-mqtt-processor/application-test.yml"
                         :test)]
      (is (not (error/anomaly? sys)) "System should start")
      (is (system/system? sys) "System should be valid")
      (when (system/system? sys)
        (let [{:keys [stop]} (processor/run sys)]
          (async/>!! stop :stop)
          (is (not (error/anomaly? (system/stop sys)))))))))
