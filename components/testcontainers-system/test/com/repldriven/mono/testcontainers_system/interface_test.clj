(ns com.repldriven.mono.testcontainers-system.interface-test
  (:require [clojure.test :as test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.testcontainers-system.interface :as SUT]
            [com.repldriven.mono.system.interface :as system]))

(defn env-fixture
  [f]
  (env/set-env! (io/resource "testcontainers_system/test-env.edn") :test)
  (f))

(use-fixtures :once env-fixture)

(deftest testcontainers-system-test
  (testing "Developers should be able to start and stop a testcontainers system"
    (let [system-config (SUT/configure-system (get-in @env/env [:system :helloworld]) :helloworld)]
      (try
        (let [running-system (system/start system-config)]
          (try
            (is (= [8080 8081] (keys (system/instance running-system [:helloworld :container-mapped-ports]))))
            (is (= (system/instance running-system [:helloworld :container-mapped-exposed-port])
                  (get (system/instance running-system [:helloworld :container-mapped-ports]) 8080)))
            (catch Exception e
              (assert false (format "Unable to get container mapped ports, %s" e)))
            (finally
              (system/stop running-system))))
        (catch Exception e
          (assert false (format "Unable to start system, %s" e)))))))
