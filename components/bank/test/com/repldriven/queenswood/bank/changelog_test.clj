(ns com.repldriven.queenswood.bank.changelog-test
  (:require
    [com.repldriven.queenswood.bank.changelog :as changelog]

    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(deftest changelog-carries-the-shared-envelope-test
  (testing
    "a status transition serialises as a ChangelogEvent the
           generic relay can decode without knowing this domain"
    (let [bytes (changelog/status-changed {:bank-id "bnk.changelog.1"
                                           :status-before :bank-status-test
                                           :status-after :bank-status-live})
          decoded (schema/pb->ChangelogEvent bytes)]
      (is (= "bank-status-changed" (:event-name decoded)))
      (is (= "bnk.changelog.1:bank-status-live" (:dedup-key decoded)))
      (is (= "bnk.changelog.1" (:causation-id decoded)))
      (is (seq (:event-id decoded)) "an event-id is minted for dedup")
      (is (pos? (count (:payload decoded))) "the Avro payload is carried"))))

(deftest status-is-an-enum-not-a-string-test
  (testing
    "a stringified status is rejected rather than carried — the
           payload field is the same Avro enum the Bank record uses, so
           a status that isn't one of the symbols cannot be published"
    (let [result (changelog/status-changed {:bank-id "bnk.changelog.2"
                                            :status-after "bank-status-live"})]
      (is (error/anomaly? result)))))

(deftest bank-creation-has-no-status-before-test
  (testing "a newly created bank has no source status"
    (let [bytes (changelog/status-changed {:bank-id "bnk.changelog.3"
                                           :status-after :bank-status-test})
          decoded (schema/pb->ChangelogEvent bytes)]
      (is (= "bnk.changelog.3:bank-status-test" (:dedup-key decoded))))))
