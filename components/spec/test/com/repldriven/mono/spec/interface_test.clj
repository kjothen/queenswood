(ns com.repldriven.mono.spec.interface-test
  (:require [clojure.test :as test :refer [deftest is testing]]
            [malli.core :as m]
            [com.repldriven.mono.spec.interface :as SUT]))

(deftest non-empty-string-test
  (testing "A non-empty string should meet the spec"
    (is (m/validate SUT/non-empty-string? "abc"))))

