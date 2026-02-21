(ns com.repldriven.mono.iam.interface-test
  (:require
    [com.repldriven.mono.iam.interface :as SUT]

    [malli.core :as m]

    [clojure.test :refer [deftest is testing]]))

(deftest project-id-schema
  (testing "ProjectId accepts valid RFC 1035 identifiers"
    (is (m/validate SUT/ProjectId "my-project"))
    (is (m/validate SUT/ProjectId "abcdef")))
  (testing "ProjectId rejects invalid identifiers"
    (is (not (m/validate SUT/ProjectId "SHORT")))
    (is (not (m/validate SUT/ProjectId "123abc")))
    (is (not (m/validate SUT/ProjectId "ab")))))

(deftest email-address-or-unique-id-schema
  (testing "EmailAddressOrUniqueId accepts valid email addresses"
    (is (m/validate SUT/EmailAddressOrUniqueId
                    "sa@example.iam.gserviceaccount.com")))
  (testing "EmailAddressOrUniqueId accepts 21-digit unique IDs"
    (is (m/validate SUT/EmailAddressOrUniqueId "123456789012345678901")))
  (testing "EmailAddressOrUniqueId rejects invalid values"
    (is (not (m/validate SUT/EmailAddressOrUniqueId "not-valid")))))
