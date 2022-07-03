(ns com.repldriven.mono.symmetric-key-api.main-test
  (:require [clojure.test :as test :refer [deftest is testing use-fixtures]]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.symmetric-key-api.main :as SUT]
            [clojure.java.io :as io]
            [org.httpkit.client :as http]))

(defn env-fixture
  [f]
  (env/set-env! (io/resource "symmetric-key-api/test-env.edn") :test)
  (f))

(use-fixtures :each env-fixture)

(deftest main-test
  (testing "Operations should be able to start the system from the main entry point"
    (try
      (SUT/-main "-c" (io/as-file (io/resource "symmetric-key-api/test-env.edn")) "-p" "test")
      (is (some? @SUT/system))
      (catch Exception e
        (assert false (format "Unable to start system, %s" e)))
      (finally (SUT/stop!)))))

(deftest development-test
  (testing "Developers should be able to start and stop the system from the REPL"
    (let [port (get-in @env/env [:system :ring :jetty-adapter :options :port])]
      (try
        (SUT/start!)
        (let [status (:status @(http/options (str "http://localhost:" port)))]
          (is (= 200 status)))
        (catch Exception e
          (assert false (format "Unable to start system, %s" e)))
        (finally (SUT/stop!))))))
