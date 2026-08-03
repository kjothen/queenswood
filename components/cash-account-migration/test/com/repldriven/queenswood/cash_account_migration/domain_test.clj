(ns com.repldriven.queenswood.cash-account-migration.domain-test
  "Pure-function tests for what authoring a migration checks: the one
  compatibility rule, the target's status, and the ordering of notice
  against the move. No FDB."
  (:require
    [com.repldriven.queenswood.cash-account-migration.domain :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(defn- version
  [product-id version-id status product-type]
  {:product-id product-id
   :version-id version-id
   :status status
   :product-type product-type})

(def ^:private savings-v1
  (version "prd.super" "ver.1"
           :cash-account-product-status-published
           :product-type-sub-ledger-savings))

(def ^:private savings-v2
  (version "prd.mega" "ver.2"
           :cash-account-product-status-published
           :product-type-sub-ledger-savings))

(def ^:private data
  {:bank-id "org.1"
   :name "Super-saver to mega-saver"
   :source-product-id "prd.super"
   :target-product-id "prd.mega"
   :target-version-id "ver.2"})

(deftest new-migration-test
  (testing "a migration between two savings products is authored in draft"
    ;; Source and target are different products — splitting a product
    ;; line is the case this exists for, not only evolving one.
    (let [m (SUT/new-migration data savings-v1 savings-v2)]
      (is (= :cash-account-migration-status-draft (:status m)))
      (is (= "org.1" (:bank-id m)))
      (is (= "prd.super" (:source-product-id m)))
      (is (= "prd.mega" (:target-product-id m)))
      (is (= "ver.2" (:target-version-id m)))
      (is (string? (:migration-id m)))
      (is (number? (:created-at m)))))
  (testing "nothing about it is approved or dated by authoring it"
    (let [m (SUT/new-migration data savings-v1 savings-v2)]
      (is (not (contains? m :approved-at)))
      (is (not (contains? m :notified-on)))
      (is (not (contains? m :due-on)))))
  (testing "an empty version narrowing is left off rather than stored empty"
    (let [m (SUT/new-migration (assoc data :source-version-ids [])
                               savings-v1
                               savings-v2)]
      (is (not (contains? m :source-version-ids)))))
  (testing "naming versions narrows the cohort"
    (let [m (SUT/new-migration (assoc data :source-version-ids ["ver.1"])
                               savings-v1
                               savings-v2)]
      (is (= ["ver.1"] (:source-version-ids m)))))
  (testing "the notice and move dates are carried when given"
    (let [m (SUT/new-migration
             (assoc data :notified-on 20260601 :due-on 20260801)
             savings-v1
             savings-v2)]
      (is (= 20260601 (:notified-on m)))
      (is (= 20260801 (:due-on m))))))

(deftest compatibility-test
  (testing "product type is the one thing that must match"
    (let [current (version "prd.cur" "ver.9"
                           :cash-account-product-status-published
                           :product-type-sub-ledger-current)
          result (SUT/new-migration data savings-v1 current)]
      (is (error/rejection? result))
      (is (= :cash-account-migration/product-type-mismatch
             (error/kind result)))))
  (testing
    "everything else may differ — that is an account's problem, not
            the migration's"
    ;; A target allowing only EUR is a perfectly valid migration; the
    ;; GBP accounts within it are reported ineligible when it runs.
    (let [eur (assoc savings-v2 :allowed-currencies ["EUR"])]
      (is (not (error/anomaly? (SUT/new-migration data savings-v1 eur)))))))

(deftest target-status-test
  (testing "a draft target is refused — the terms are still being written"
    (let [draft (assoc savings-v2 :status :cash-account-product-status-draft)
          result (SUT/new-migration data savings-v1 draft)]
      (is (error/rejection? result))
      (is (= :cash-account-migration/target-not-published
             (error/kind result)))))
  (testing "a discarded target is refused — the terms were abandoned"
    (let [discarded
          (assoc savings-v2 :status :cash-account-product-status-discarded)
          result (SUT/new-migration data savings-v1 discarded)]
      (is (error/rejection? result))
      (is (= :cash-account-migration/target-not-published
             (error/kind result))))))

(deftest degenerate-migration-test
  (testing "migrating a version onto itself moves nobody and is refused"
    (let [result (SUT/new-migration (assoc data :source-version-ids ["ver.2"])
                                    savings-v1
                                    savings-v2)]
      (is (error/rejection? result))
      (is (= :cash-account-migration/target-is-source (error/kind result)))))
  (testing
    "a source narrowed to several versions including the target is not
            degenerate — the others still move"
    (is (not (error/anomaly? (SUT/new-migration
                              (assoc data :source-version-ids ["ver.1" "ver.2"])
                              savings-v1
                              savings-v2))))))

(deftest notice-test
  (testing "notice given after the move is not notice"
    (let [result (SUT/new-migration
                  (assoc data :notified-on 20260801 :due-on 20260601)
                  savings-v1
                  savings-v2)]
      (is (error/rejection? result))
      (is (= :cash-account-migration/notice-after-due (error/kind result)))))
  (testing
    "same-day notice is allowed here — how long is long enough is not
            settled in the domain"
    (is (not (error/anomaly? (SUT/new-migration (assoc data
                                                       :notified-on 20260601
                                                       :due-on 20260601)
                                                savings-v1
                                                savings-v2)))))
  (testing "either date alone is fine — a draft need not have both yet"
    (is (not (error/anomaly? (SUT/new-migration (assoc data :due-on 20260601)
                                                savings-v1
                                                savings-v2))))
    (is (not (error/anomaly? (SUT/new-migration
                              (assoc data :notified-on 20260601)
                              savings-v1
                              savings-v2))))))

(def ^:private target (assoc savings-v2 :allowed-currencies ["GBP"]))

(defn- account
  [opts]
  (merge {:account-id "acc.1"
          :product-id "prd.super"
          :version-id "ver.1"
          :currency "GBP"
          :account-status :cash-account-status-opened}
         opts))

(deftest in-cohort?-test
  (let [migration {:source-product-id "prd.super"}]
    (testing "an account of the source product is in the cohort"
      (is (true? (SUT/in-cohort? migration (account {})))))
    (testing
      "an account of another product is not — the scan sees the whole
              bank, and only this product's accounts are being moved"
      (is (not (SUT/in-cohort? migration (account {:product-id "prd.other"})))))
    (testing "naming no versions takes every version of the product"
      (is (true? (SUT/in-cohort? (assoc migration :source-version-ids [])
                                 (account {:version-id "ver.7"})))))
    (testing "naming versions narrows to accounts pinned to those"
      (let [narrowed (assoc migration :source-version-ids ["ver.1" "ver.2"])]
        (is (true? (SUT/in-cohort? narrowed (account {:version-id "ver.2"}))))
        (is (not (SUT/in-cohort? narrowed (account {:version-id "ver.9"}))))))))

(deftest verdict-test
  (testing "an open account on an allowed currency would move"
    (is (= :cash-account-migration-outcome-eligible
           (:outcome (SUT/verdict target (account {}))))))
  (testing "an account already on the target is reported, not moved again"
    ;; This is what a re-run sees for everything an earlier run moved,
    ;; and is how the pass stays idempotent without tracking its work.
    (let [v (SUT/verdict target (account {:version-id "ver.2"}))]
      (is (= :cash-account-migration-outcome-ineligible (:outcome v)))
      (is (= :cash-account-migration-ineligibility-already-on-target
             (:ineligibility v)))))
  (testing "a closed account's terms are not in play"
    (let [v (SUT/verdict target
                         (account {:account-status
                                   :cash-account-status-closed}))]
      (is (= :cash-account-migration-ineligibility-account-not-open
             (:ineligibility v)))))
  (testing "a currency the target does not allow leaves that account behind"
    ;; The migration itself stays valid — this is the case the whole
    ;; per-account eligibility design exists for.
    (let [v (SUT/verdict target (account {:currency "EUR"}))]
      (is (= :cash-account-migration-outcome-ineligible (:outcome v)))
      (is (= :cash-account-migration-ineligibility-currency-not-allowed
             (:ineligibility v)))))
  (testing "an eligible verdict carries no reason — there is nothing to explain"
    (is (not (contains? (SUT/verdict target (account {})) :ineligibility)))))

(deftest run-lifecycle-test
  (let [migration {:bank-id "org.1" :migration-id "mig.1"}
        run (SUT/new-run migration 20260801 true)]
    (testing "a run opens as running, and a preview says so on the record"
      (is (= :cash-account-migration-run-status-running (:status run)))
      (is (true? (:dry-run run)))
      (is (= 20260801 (:business-day run)))
      (is (number? (:started-at run))))
    (testing "closing carries the counts a reader wants before any verdict"
      (let [closed (SUT/close-run
                    run
                    {:seen 9588 :moved 9176 :ineligible 412 :failed 0})]
        (is (= :cash-account-migration-run-status-completed (:status closed)))
        (is (= 9588 (:accounts-seen closed)))
        (is (= 412 (:accounts-ineligible closed)))
        (is (number? (:finished-at closed)))))
    (testing "a run that could not finish is failed, not completed"
      (let [failed (SUT/fail-run run (error/reject :some/anomaly {}))]
        (is (= :cash-account-migration-run-status-failed (:status failed)))
        (is (string? (:error failed)))))))

(deftest account-verdict-test
  (let [run {:bank-id "org.1" :run-id "run.1" :migration-id "mig.1"}]
    (testing "a verdict records where the account was"
      (let [v (SUT/account-verdict run
                                   (account {})
                                   {:outcome
                                    :cash-account-migration-outcome-eligible})]
        (is (= "acc.1" (:account-id v)))
        (is (= "ver.1" (:from-version-id v)))))
    (testing "nothing moved, so nothing records where it moved to"
      ;; A dry run's eligible verdict must not read as though the
      ;; account landed somewhere.
      (let [v (SUT/account-verdict run
                                   (account {})
                                   {:outcome
                                    :cash-account-migration-outcome-eligible})]
        (is (not (contains? v :to-version-id)))))
    (testing "an ineligible verdict carries its reason"
      (let [v (SUT/account-verdict
               run
               (account {})
               {:outcome :cash-account-migration-outcome-ineligible
                :ineligibility
                :cash-account-migration-ineligibility-currency-not-allowed})]
        (is (= :cash-account-migration-ineligibility-currency-not-allowed
               (:ineligibility v)))))))

(deftest name-test
  (testing "a migration carries the name an operator gave it"
    (is (= "Super-saver to mega-saver"
           (:name (SUT/new-migration data savings-v1 savings-v2)))))
  (testing "a blank name is refused here, not left to the record"
    ;; protojure drops an empty string from the wire and the proto2
    ;; required field then fails the Java parse — so without this the
    ;; failure would surface as a serialisation error, nowhere near the
    ;; thing that caused it.
    (doseq [blank [nil "" "   "]]
      (let [result
            (SUT/new-migration (assoc data :name blank) savings-v1 savings-v2)]
        (is (error/rejection? result))
        (is (= :cash-account-migration/name-required (error/kind result)))))))
