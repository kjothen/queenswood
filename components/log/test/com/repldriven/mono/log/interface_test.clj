(ns com.repldriven.mono.log.interface-test
  (:require [clojure.test :as test :refer [deftest is testing]]
            [com.repldriven.mono.log.interface :as SUT]))

(deftest log-test
  (testing "The log should initialize without error"
    (is (nil? (SUT/error "ignore - testing error logging")))
    (is (nil? (SUT/info "ignore - testing info logging")))
    (is (nil? (SUT/warn "ignore - testing warning logging")))))
