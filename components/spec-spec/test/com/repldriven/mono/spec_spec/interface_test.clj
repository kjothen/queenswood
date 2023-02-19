(ns com.repldriven.mono.spec-spec.interface-test
  (:require [clojure.test :as test :refer [deftest is testing]]
            [clojure.spec.alpha :as s]
            [com.repldriven.mono.spec-spec.interface :as SUT]))

(deftest non-empty-string-test
  (testing "A non-empty string should meet the spec"
           (is (s/valid? SUT/non-empty-string? "abc"))))
