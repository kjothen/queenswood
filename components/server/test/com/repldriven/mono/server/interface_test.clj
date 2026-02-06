(ns com.repldriven.mono.server.interface-test
  (:require [clojure.test :as test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.server.interface :as SUT]
            [com.repldriven.mono.system.interface :as system :refer
             [with-system]]))

(defn env-fixture
  [f]
  (env/set-env! (io/resource "server/test-env.edn") :test)
  (f))

(use-fixtures :once env-fixture)

(deftest development-test
  (testing "Developers should be able to start and stop a server"
           (with-system
            [sys (SUT/configure-system (get-in @env/env [:system :server]))]
            (is (some? sys))
            (is (= 1 1)))))
