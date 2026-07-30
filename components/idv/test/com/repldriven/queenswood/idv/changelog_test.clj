(ns com.repldriven.queenswood.idv.changelog-test
  (:require
    [com.repldriven.queenswood.idv.changelog :as changelog]

    [com.repldriven.queenswood.schema.interface :as schema]

    [clojure.test :refer [deftest is testing]]))

(deftest changelog-carries-the-shared-envelope-test
  (testing
    "a status transition serialises as a ChangelogEvent the
           generic relay can decode without knowing this domain"
    (let [bytes (changelog/status-changed {:bank-id "bnk_changelog_test"
                                           :verification-id "idv.changelog.1"
                                           :party-id "pty.changelog.1"
                                           :status-before :idv-status-in-review
                                           :status-after :idv-status-accepted})
          decoded (schema/pb->ChangelogEvent bytes)]
      (is (= "idv-status-changed" (:event-name decoded)))
      (is (= "idv.changelog.1:idv-status-accepted" (:dedup-key decoded)))
      (is (= "pty.changelog.1" (:causation-id decoded))
          "the party is the key the consumer orders by")
      (is (seq (:event-id decoded)) "an event-id is minted for dedup")
      (is (pos? (count (:payload decoded))) "the Avro payload is carried"))))

(deftest idv-creation-has-no-status-before-test
  (testing
    "a newly opened IDV has no source status, and the envelope
           still decodes"
    (let [bytes (changelog/status-changed {:bank-id "bnk_changelog_test"
                                           :verification-id "idv.changelog.2"
                                           :party-id "pty.changelog.2"
                                           :status-after :idv-status-pending})
          decoded (schema/pb->ChangelogEvent bytes)]
      (is (= "idv.changelog.2:idv-status-pending" (:dedup-key decoded))))))
