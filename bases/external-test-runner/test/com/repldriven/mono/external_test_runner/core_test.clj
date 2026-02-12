(ns com.repldriven.mono.external-test-runner.core-test
  (:require
    [com.repldriven.mono.external-test-runner.core :as core]

    [clojure.test :as test :refer :all]))

(deftest dummy-test (is (= 1 1)))

