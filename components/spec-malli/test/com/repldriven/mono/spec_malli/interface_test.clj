(ns com.repldriven.mono.spec-malli.interface-test
  (:require [clojure.test :as test :refer :all]
            [malli.core :as m]
            [com.repldriven.mono.spec-malli.interface :as SUT]))

(deftest non-empty-string-test
  (testing "A non-empty string should meet the spec"
    (is (m/validate (m/schema SUT/non-empty-string?) "a"))))
