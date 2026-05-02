(ns com.repldriven.mono.bank-cash-account-product.domain-test
  "Pure-function tests for the rejection paths surfaced by
  cash-account-product domain operations: `:version-immutable`
  on update/publish/discard against non-draft versions, and
  `:draft-already-exists` on new-version when a draft is open.

  Replaces the slow integration paths in interface_test.clj that
  spun up FDB just to assert these anomaly kinds."
  (:require
    [com.repldriven.mono.bank-cash-account-product.domain :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(def ^:private permissive-policies
  "Single allow-everything policy — the domain functions check
  capability before some rejections fire, so tests that need to
  reach a downstream check pass this in. The `:kind` is a oneof
  map (variant → fields), and an empty fields map matches every
  request because the matcher only constrains on set fields."
  [{:enabled true
    :capabilities [{:kind {:cash-account-product {}} :effect :effect-allow}]}])

(def ^:private published-version
  {:organization-id "org.1"
   :product-id "prd.1"
   :version-id "prv.1"
   :version-number 1
   :status :cash-account-product-status-published
   :name "Published"
   :product-type :product-type-current
   :balance-sheet-side :balance-sheet-side-liability
   :balance-products [{:balance-type :balance-type-default
                       :balance-status :balance-status-posted}]
   :interest-rate-bps 0
   :created-at 1700000000000
   :updated-at 1700000000000})

(def ^:private discarded-version
  (assoc published-version
         :version-id "prv.2"
         :version-number 2
         :status :cash-account-product-status-discarded))

(def ^:private draft-version
  (assoc published-version
         :version-id "prv.3"
         :version-number 3
         :status :cash-account-product-status-draft))

(def ^:private good-data
  {:name "v2"
   :product-type :product-type-current
   :balance-sheet-side :balance-sheet-side-liability
   :balance-products [{:balance-type :balance-type-default
                       :balance-status :balance-status-posted}]
   :allowed-currencies ["GBP"]
   :allowed-payment-address-schemes [:payment-address-scheme-scan]})

(deftest update-version-test
  (testing "rejects with :version-immutable when existing is published"
    (let [r
          (SUT/update-version published-version good-data permissive-policies)]
      (is (error/rejection? r))
      (is (= :cash-account-product/version-immutable (error/kind r)))))
  (testing "rejects with :version-immutable when existing is discarded"
    (let [r
          (SUT/update-version discarded-version good-data permissive-policies)]
      (is (error/rejection? r))
      (is (= :cash-account-product/version-immutable (error/kind r)))))
  (testing "succeeds when existing is a draft, preserving id and number"
    (let [v (SUT/update-version draft-version
                                (assoc good-data :name "renamed")
                                permissive-policies)]
      (is (= "prv.3" (:version-id v)))
      (is (= 3 (:version-number v)))
      (is (= "renamed" (:name v)))
      (is (= :cash-account-product-status-draft (:status v))))))

(deftest publish-test
  (testing "rejects with :version-immutable when already published"
    (let [r (SUT/publish published-version permissive-policies)]
      (is (error/rejection? r))
      (is (= :cash-account-product/version-immutable (error/kind r)))))
  (testing "rejects with :version-immutable when discarded"
    (let [r (SUT/publish discarded-version permissive-policies)]
      (is (error/rejection? r))
      (is (= :cash-account-product/version-immutable (error/kind r)))))
  (testing "flips draft to :published, preserving other fields"
    (let [v (SUT/publish draft-version permissive-policies)]
      (is (= :cash-account-product-status-published (:status v)))
      (is (= "prv.3" (:version-id v)))
      (is (= 3 (:version-number v))))))

(deftest discard-test
  (testing "rejects with :version-immutable when already published"
    (let [r (SUT/discard published-version permissive-policies)]
      (is (error/rejection? r))
      (is (= :cash-account-product/version-immutable (error/kind r)))))
  (testing "rejects with :version-immutable when already discarded"
    (let [r (SUT/discard discarded-version permissive-policies)]
      (is (error/rejection? r))
      (is (= :cash-account-product/version-immutable (error/kind r)))))
  (testing "flips draft to :discarded and stamps :discarded-at"
    (let [v (SUT/discard draft-version permissive-policies)]
      (is (= :cash-account-product-status-discarded (:status v)))
      (is (some? (:discarded-at v))))))

(deftest new-version-test
  (testing "rejects with :draft-already-exists when versions contains a draft"
    (let [r (SUT/new-version "org.1"
                             "prd.1"
                             [draft-version]
                             good-data
                             permissive-policies)]
      (is (error/rejection? r))
      (is (= :cash-account-product/draft-already-exists (error/kind r)))))
  (testing "succeeds when prior versions are all published or discarded"
    (let [v (SUT/new-version "org.1"
                             "prd.1"
                             [published-version discarded-version]
                             good-data
                             permissive-policies)]
      (is (= :cash-account-product-status-draft (:status v)))
      (is (= 3 (:version-number v)) "version-number is 1 + (count versions)")))
  (testing "succeeds with no prior versions — fresh product flow"
    (let [v (SUT/new-version "org.1" "prd.1" [] good-data permissive-policies)]
      (is (= 1 (:version-number v)))
      (is (= :cash-account-product-status-draft (:status v))))))
