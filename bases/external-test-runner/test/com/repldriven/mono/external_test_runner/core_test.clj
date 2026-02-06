(ns com.repldriven.mono.external-test-runner.core-test
  (:require [clojure.test :as test :refer :all]
            [com.repldriven.mono.external-test-runner.core :as core]))

(deftest dummy-test
  (is (= 1 1)))

