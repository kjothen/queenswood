(ns com.repldriven.mono.spec-spec.interface-test
  (:require
    [com.repldriven.mono.spec-spec.interface :as SUT]

    [clojure.spec.alpha :as s]
    [clojure.test :as test :refer [deftest is testing]]))

(deftest non-empty-string-test
  (testing "A non-empty string should meet the spec"
    (is (s/valid? SUT/non-empty-string? "abc"))))
