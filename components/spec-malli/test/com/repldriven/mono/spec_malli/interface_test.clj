(ns com.repldriven.mono.spec-malli.interface-test
  (:require
    [com.repldriven.mono.spec-malli.interface :as SUT]

    [malli.core :as m]

    [clojure.test :as test :refer :all]))

(deftest non-empty-string-test
  (testing "A non-empty string should meet the spec"
    (is (m/validate (m/schema SUT/non-empty-string?) "a"))))
