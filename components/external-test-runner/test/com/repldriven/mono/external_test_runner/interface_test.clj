(ns com.repldriven.mono.external-test-runner.interface-test
  (:require [clojure.test :as test :refer :all]
            [com.repldriven.mono.external-test-runner.interface :as external-test-runner]))

(deftest dummy-test
  (is (= 1 1)))
