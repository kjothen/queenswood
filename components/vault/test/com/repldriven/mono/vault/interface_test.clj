(ns com.repldriven.mono.vault.interface-test
  (:require [clojure.java.io :as io]
            [clojure.test :as test :refer :all]
            [com.repldriven.mono.vault.interface :as vault]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.vault.env-reader]
            [com.repldriven.mono.vault.interface :as SUT]
            [com.repldriven.mono.system.interface :as system]))

(defn env-fixture
  [f]
  (env/set-env! (io/resource "vault/test-env.edn") :test)
  (f))

(use-fixtures :once env-fixture)

(deftest development-test
  (testing "Developers should be able to start/stop a vault system from the REPL"
    (let [system-config (SUT/configure-system (get-in @env/env [:system :vault]))]
      (try
        (let [running-system (system/start system-config)]
          (try
            (is (some? (system/instance running-system [:vault :http-url])))
            (catch Exception e
              (assert false (format "Unable to get vault reader, %s" e)))
            (finally
              (system/stop running-system))))
        (catch Exception e
          (assert false (format "Unable to start system, %s" e)))))))
