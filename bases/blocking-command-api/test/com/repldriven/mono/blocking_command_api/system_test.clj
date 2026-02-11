(ns com.repldriven.mono.blocking-command-api.system-test
  (:require
   com.repldriven.mono.mqtt.interface
   com.repldriven.mono.pulsar.interface
   com.repldriven.mono.server.interface
   com.repldriven.mono.testcontainers.interface

   [com.repldriven.mono.system.interface :as system]

   [clojure.test :refer [deftest is testing]]))

(deftest system-test
  (testing "System configuration and lifecycle"
    (system/with-sysdefs [sysdefs "classpath:blocking-command-api/application-test.yml" :test]
      (is (system/system? sysdefs))
      (let [modified-sysdefs (update-in sysdefs [:system/defs :server] dissoc :jetty-adapter)]
        (system/with-sys [sys modified-sysdefs]
          (is (some? sys)))))))
