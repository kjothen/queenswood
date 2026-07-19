(ns com.repldriven.mono.bank-party.domain-test
  "Pure-function tests for merge-party's source-state guards. No FDB,
  no processor — this pins the lifecycle-transition convention
  (docs/recipes/lifecycle-transitions.md): reject before any
  capability check when either party isn't in a valid source state."
  (:require
    [com.repldriven.mono.bank-party.domain :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(defn- party
  [party-id status]
  {:bank-id "bnk.test"
   :party-id party-id
   :type :party-type-person
   :display-name "Test Party"
   :status status
   :created-at 0
   :updated-at 0})

(defn- policy-allowing
  [& actions]
  {:enabled true
   :capabilities (mapv (fn [action]
                         {:effect :effect-allow
                          :kind {:party {:action action}}})
                       actions)})

(def ^:private survivor (party "pty.survivor" :party-status-active))
(def ^:private merged-away (party "pty.merged" :party-status-suspended))
(def ^:private allow-merge [(policy-allowing :party-action-merge)])

(deftest merge-party-into-self-rejected-test
  (testing "merging a party into itself is rejected before any status guard"
    (let [same (party "pty.self" :party-status-suspended)
          result (SUT/merge-party same same false allow-merge)]
      (is (error/rejection? result))
      (is (= :party/merge-into-self (error/kind result))))))

(deftest merge-party-source-state-guard-test
  (testing
    "merging a party not in :party-status-suspended is rejected,
           regardless of policy"
    (doseq [status [:party-status-pending
                    :party-status-active
                    :party-status-closed
                    :party-status-rejected
                    :party-status-merged]]
      (let [away (party "pty.merged" status)
            result (SUT/merge-party survivor away false allow-merge)]
        (is (error/rejection? result))
        (is (= :party/invalid-status (error/kind result)))
        (is (= "pty.merged" (:party-id (error/payload result))))
        (is (= status (:status (error/payload result))))))))

(deftest merge-party-survivor-must-be-active-test
  (testing "a non-active survivor is rejected, regardless of policy"
    (doseq [status [:party-status-pending
                    :party-status-suspended
                    :party-status-closed
                    :party-status-rejected
                    :party-status-merged]]
      (let [not-active-survivor (party "pty.survivor" status)
            result
            (SUT/merge-party not-active-survivor merged-away false allow-merge)]
        (is (error/rejection? result))
        (is (= :party/invalid-status (error/kind result)))
        (is (= "pty.survivor" (:party-id (error/payload result))))
        (is (= status (:status (error/payload result))))))))

(deftest merge-party-capability-denied-test
  (testing "no allow capability for party-action-merge denies the merge"
    (let [result (SUT/merge-party survivor merged-away false [])]
      (is (error/unauthorized? result)))))

(deftest merge-party-open-accounts-rejected-test
  (testing "a merged-away party with any non-closed cash account is rejected"
    (let [result (SUT/merge-party survivor merged-away true allow-merge)]
      (is (error/rejection? result))
      (is (= :party/open-accounts (error/kind result)))
      (is (= "pty.merged" (:party-id (error/payload result)))))))

(deftest merge-party-happy-test
  (testing
    "a suspended party merged into an active survivor flips to
           merged and records the survivor's id as the pointer"
    (let [result (SUT/merge-party survivor merged-away false allow-merge)]
      (is (= :party-status-merged (:status result)))
      (is (= "pty.survivor" (:merged-into-party-id result)))
      (is (= "pty.merged" (:party-id result)))
      (is (int? (:updated-at result))))))
