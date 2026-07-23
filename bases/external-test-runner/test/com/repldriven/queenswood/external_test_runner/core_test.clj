(ns com.repldriven.queenswood.external-test-runner.core-test
  (:require
    com.repldriven.queenswood.external-test-runner.main
    [clojure.test :refer [deftest is]]))

(deftest dummy-test (is (= 1 1)))
