(ns ^:eftest/synchronized com.repldriven.mono.pulsar-mqtt-processor.main-test
  (:require
   com.repldriven.mono.mqtt.interface
   com.repldriven.mono.pulsar.interface
   com.repldriven.mono.testcontainers.interface

   [com.repldriven.mono.pulsar-mqtt-processor.main :as SUT]

   [com.repldriven.mono.error.interface :as error]
   [com.repldriven.mono.log.interface :as log]
   [com.repldriven.mono.system.interface :as system]

   [clojure.core.async :as async]
   [clojure.pprint :as pp]
   [clojure.test :refer [deftest is testing]]))

(deftest main-test
  (testing "System should start and process commands"
    (let [sys (SUT/start "classpath:pulsar-mqtt-processor/application-test.yml"
                         :test)]
      (is (not (error/anomaly? sys)) "System should start")
      (is (system/system? sys) "System should be valid")
      (when (system/system? sys)
        ;; Start command processing
        (let [{:keys [stop]} (SUT/run sys)]
          ;; TODO: Send test commands and verify responses

          ;; Stop processing
          (async/>!! stop :stop)

          ;; Stop system
          (let [result (system/stop sys)]
            (when (error/anomaly? result)
              (println (with-out-str (pp/pprint result)))
              (is (not (error/anomaly? result))))))))))
