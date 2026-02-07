(ns com.repldriven.mono.iam-api.main-test
  (:require [clojure.test :as test :refer [deftest is testing]]
            [com.repldriven.mono.iam-api.main :as SUT]
            [com.repldriven.mono.server.interface]
            [com.repldriven.mono.db.interface]))

(deftest start-test
  (testing
   "Ops should be able to start the system from the main entry point"
    (try
      (SUT/-main "-c" "classpath:iam-api/test-application.yml"
                 "-p" "test")
      (is (some? @SUT/system))
      (catch Exception e (assert false (format "Unable to start system, %s" e)))
      (finally (SUT/stop @SUT/system)))))
