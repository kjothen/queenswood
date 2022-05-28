(ns com.repldriven.mono.ring.interface-test
  (:require [clojure.test :as test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.ring.interface :as SUT]
            [com.repldriven.mono.system.interface :as system]))

(defn env-fixture
  [f]
  (env/set-env! (io/resource "ring/test-env.edn") :test)
  (f))

(use-fixtures :once env-fixture)

(deftest development-test
  (testing "Developers should be able to start and stop a ring web server"
    (let [system-config (SUT/create-system (get-in @env/env [:system :ring]))]
      (try
        (let [running-system (system/start system-config)]
          (try
            (is (= 1 1))
            (catch Exception e
              (assert false (format "Unable to do stuff, %s" e)))
            (finally
              (system/stop running-system))))
        (catch Exception e
          (assert false (format "Unable to start system, %s" e)))))))
