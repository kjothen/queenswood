(ns com.repldriven.mono.pulsar-reader.reader-test
  (:require
   com.repldriven.mono.pulsar.interface
   com.repldriven.mono.testcontainers.interface

   [com.repldriven.mono.env.interface :as env]
   [com.repldriven.mono.error.interface :as error]
   [com.repldriven.mono.system.interface :as system]

   [clojure.test :refer [deftest is testing]])
  (:import (org.apache.pulsar.client.api Reader)))

(deftest reader-test
  (testing "Pulsar reader system should start and create reader instance"
    (let [sys (error/nom-> (env/config "classpath:pulsar-reader/test-application.yml" :test)
                           system/defs
                           system/start)]
      (is (not (error/anomaly? sys)) "System should start")
      (is (system/system? sys) "System should be valid")
      (when (system/system? sys)
        (system/with-system sys
          (let [producer (system/instance sys [:pulsar :producer])
                ^Reader reader (system/instance sys [:pulsar :reader])
                schemas (system/instance sys [:pulsar :schemas])]
            (is (some? producer) "Producer should be created")
            (is (some? reader) "Reader should be created")
            (is (some? schemas) "Schemas should be created")
            (is (contains? schemas :user) "User schema should exist")))))))
