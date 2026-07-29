(ns com.repldriven.queenswood.party.changelog-test
  (:require
    [com.repldriven.queenswood.party.changelog :as changelog]

    [com.repldriven.queenswood.schema.interface :as schema]

    [clojure.test :refer [deftest is testing]]))

(deftest changelog-carries-the-shared-envelope-test
  (testing
    "a status transition serialises as a ChangelogEvent the
           generic relay can decode without knowing this domain"
    (let [bytes (changelog/status-changed {:bank-id "bnk_changelog_test"
                                           :party-id "pty.changelog.1"
                                           :status-before :party-status-pending
                                           :status-after :party-status-active})
          decoded (schema/pb->ChangelogEvent bytes)]
      (is (= "party-status-changed" (:event-name decoded)))
      (is (= "pty.changelog.1:party-status-active" (:dedup-key decoded)))
      (is (seq (:event-id decoded)) "an event-id is minted for dedup")
      (is (pos? (count (:payload decoded))) "the Avro payload is carried"))))

(deftest party-creation-has-no-status-before-test
  (testing
    "a newly created party has no source status, and the envelope
           still decodes"
    (let [bytes (changelog/status-changed {:bank-id "bnk_changelog_test"
                                           :party-id "pty.changelog.2"
                                           :status-after :party-status-pending})
          decoded (schema/pb->ChangelogEvent bytes)]
      (is (= "pty.changelog.2:party-status-pending" (:dedup-key decoded))))))
