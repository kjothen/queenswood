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
  (testing
   "Developers should be able to start and stop a testcontainers system"
   (system/with-*sys*
     (SUT/configure-system (get-in @env/env [:system :helloworld]) :helloworld)
     (is (= [8080 8081]
            (keys (system/instance system/*sys* [:helloworld :container-mapped-ports]))))
     (is (= (system/instance system/*sys* [:helloworld :container-mapped-exposed-port])
            (get (system/instance system/*sys* [:helloworld :container-mapped-ports])
                 8080))))))
