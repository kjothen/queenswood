(ns com.repldriven.mono.iam-api.main-test
  (:require [clojure.test :as test :refer [deftest is testing use-fixtures]]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.iam-api.main :as SUT]
            [clojure.java.io :as io]
            [org.httpkit.client :as http]))

(deftest start
  (testing
   "Ops should be able to start the system from the main entry point"
   (try (SUT/-main "-c" (io/as-file (io/resource "iam-api/test-env.edn"))
                   "-p" "test")
        (is (some? @SUT/system))
        (catch Exception e
          (assert false (format "Unable to start system, %s" e)))
        (finally (SUT/stop!)))))
