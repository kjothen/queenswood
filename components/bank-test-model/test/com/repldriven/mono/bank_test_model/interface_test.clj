(ns com.repldriven.mono.bank-test-model.interface-test
  (:require
    [com.repldriven.mono.bank-test-model.interface :as SUT]

    [clojure.test :refer [deftest is testing]]))

(defn- step
  "Applies the named command's `:next-state` with the given args
  vector to `state`. Mirrors how the runner threads commands."
  [state command args]
  (let [spec (get SUT/model command)]
    ((:next-state spec) state {:args args})))

(deftest open-account-test
  (testing
    "open-account allocates an org, settlement product, org-party, and first account"
    (let [s (step SUT/init-state :open-account [])]
      (is (= [:acct-0] (SUT/known-accounts s)))
      (is (= [:org-0] (keys (:orgs s))))
      (is (= [:prod-0] (keys (:products s))))
      (is (= [:party-0] (keys (:parties s))))
      (is (= 0 (SUT/balance s :acct-0)))
      (is (= :org-0 (get-in s [:accounts :acct-0 :org])))
      (is (= :prod-0 (get-in s [:accounts :acct-0 :product])))
      (is (= :party-0 (get-in s [:accounts :acct-0 :party])))
      (is (= [:acct-0] (get-in s [:orgs :org-0 :accounts])))
      (is (= [:prod-0] (get-in s [:orgs :org-0 :products])))
      (is (= [:party-0] (get-in s [:orgs :org-0 :parties])))
      (is (= :published (get-in s [:products :prod-0 :status])))
      (is (= :active (get-in s [:parties :party-0 :status])))
      (is (= :organization (get-in s [:parties :party-0 :type])))
      (is (= 1 (:next-id s)))
      (is (= 1 (:next-org-id s)))
      (is (= 1 (:next-product-id s)))
      (is (= 1 (:next-party-id s)))))
  (testing "successive opens make distinct orgs, products, and parties"
    (let [s (-> SUT/init-state
                (step :open-account [])
                (step :open-account []))]
      (is (= #{:acct-0 :acct-1} (set (SUT/known-accounts s))))
      (is (= #{:org-0 :org-1} (set (keys (:orgs s)))))
      (is (= #{:prod-0 :prod-1} (set (keys (:products s)))))
      (is (= #{:party-0 :party-1} (set (keys (:parties s)))))
      (is (= :prod-0 (get-in s [:accounts :acct-0 :product])))
      (is (= :prod-1 (get-in s [:accounts :acct-1 :product])))
      (is (= :party-0 (get-in s [:accounts :acct-0 :party])))
      (is (= :party-1 (get-in s [:accounts :acct-1 :party]))))))

(deftest add-account-test
  (testing "add-account opens a second account using the same party + product"
    (let [s (-> SUT/init-state
                (step :open-account [])
                (step :add-account [:org-0 :party-0 :prod-0]))]
      (is (= [:acct-0 :acct-1] (sort (SUT/known-accounts s))))
      (is (= 1 (count (:orgs s))))
      (is (= :org-0 (get-in s [:accounts :acct-1 :org])))
      (is (= :prod-0 (get-in s [:accounts :acct-1 :product])))
      (is (= :party-0 (get-in s [:accounts :acct-1 :party])))
      (is (= [:acct-0 :acct-1] (get-in s [:orgs :org-0 :accounts])))))
  (testing ":valid? rejects unknown orgs, cross-org products, cross-org parties"
    (let [spec (get SUT/model :add-account)
          s (-> SUT/init-state
                (step :open-account [])
                (step :open-account []))]
      (is (true? ((:valid? spec) s {:args [:org-0 :party-0 :prod-0]})))
      (is (false? ((:valid? spec) s {:args [:org-0 :party-0 :prod-1]}))
          "prod-1 belongs to org-1, not org-0")
      (is (false? ((:valid? spec) s {:args [:org-0 :party-1 :prod-0]}))
          "party-1 belongs to org-1, not org-0")
      (is (false? ((:valid? spec) s {:args [:org-99 :party-0 :prod-0]})))
      (is (false? ((:valid? spec) s {:args [:org-0 :party-99 :prod-0]})))
      (is (false? ((:valid? spec) s {:args [:org-0 :party-0 :prod-99]}))))))

(deftest create-and-publish-product-test
  (let [s0 (step SUT/init-state :open-account [])]
    (testing "create-product opens a draft attached to an org"
      (let [s1 (step s0 :create-product [:org-0])]
        (is (= 1 (:next-product-id s0))
            "prod-0 was already taken by the auto settlement product")
        (is (= :draft (get-in s1 [:products :prod-1 :status])))
        (is (= :org-0 (get-in s1 [:products :prod-1 :org])))
        (is (= [:prod-0 :prod-1] (get-in s1 [:orgs :org-0 :products])))))
    (testing "publish-product flips a draft to published"
      (let [s2 (-> s0
                   (step :create-product [:org-0])
                   (step :publish-product [:prod-1]))]
        (is (= :published (get-in s2 [:products :prod-1 :status])))))
    (testing "add-account against a draft is rejected by :valid?"
      (let [s3 (step s0 :create-product [:org-0])
            spec (get SUT/model :add-account)]
        (is (false? ((:valid? spec) s3 {:args [:org-0 :party-0 :prod-1]}))
            "draft products can't host accounts")))
    (testing "add-account against a freshly-published custom product works"
      (let [s4 (-> s0
                   (step :create-product [:org-0])
                   (step :publish-product [:prod-1])
                   (step :add-account [:org-0 :party-0 :prod-1]))]
        (is (= :prod-1 (get-in s4 [:accounts :acct-1 :product])))))))

(deftest inbound-transfer-test
  (let [s (-> SUT/init-state
              (step :open-account []))]
    (testing "credits a fresh account"
      (let [s' (step s :inbound-transfer [:acct-0 500])]
        (is (= 500 (SUT/balance s' :acct-0)))))
    (testing "credit on a negative account that improves it is permitted"
      (let [breached (assoc-in s [:accounts :acct-0 :available] -50)
            s' (step breached :inbound-transfer [:acct-0 20])]
        (is (= -30 (SUT/balance s' :acct-0))
            "improving=true rule lets the move through")))
    (testing "credit on a negative account that overshoots zero is permitted"
      (let [breached (assoc-in s [:accounts :acct-0 :available] -50)
            s' (step breached :inbound-transfer [:acct-0 100])]
        (is (= 50 (SUT/balance s' :acct-0)))))))

(deftest outbound-transfer-test
  (let [s (-> SUT/init-state
              (step :open-account []))]
    (testing "debit on a zero account is denied (would go negative)"
      (let [s' (step s :outbound-transfer [:acct-0 100])]
        (is (= 0 (SUT/balance s' :acct-0)) "policy denies — state unchanged")))
    (testing "debit that worsens an already-negative account is denied"
      (let [breached (assoc-in s [:accounts :acct-0 :available] -50)
            s' (step breached :outbound-transfer [:acct-0 10])]
        (is (= -50 (SUT/balance s' :acct-0)))))
    (testing "debit on a positive account stays in-bound"
      (let [funded (assoc-in s [:accounts :acct-0 :available] 200)
            s' (step funded :outbound-transfer [:acct-0 80])]
        (is (= 120 (SUT/balance s' :acct-0)))))))

(deftest internal-transfer-test
  (let [s (-> SUT/init-state
              (step :open-account [])
              (step :open-account []))]
    (testing "two-leg transfer between funded and zero account"
      (let [funded (assoc-in s [:accounts :acct-0 :available] 1000)
            s' (step funded :internal-transfer [:acct-0 :acct-1 400])]
        (is (= 600 (SUT/balance s' :acct-0)))
        (is (= 400 (SUT/balance s' :acct-1)))))
    (testing "transfer that would overdraw the source is denied — atomic"
      (let [s' (step s :internal-transfer [:acct-0 :acct-1 100])]
        (is (= 0 (SUT/balance s' :acct-0)))
        (is (= 0 (SUT/balance s' :acct-1))
            "credit leg also reverts when debit leg fails")))
    (testing "transfer that improves a breach on the source is permitted"
      (let [breached (-> s
                         (assoc-in [:accounts :acct-0 :available] -100)
                         (assoc-in [:accounts :acct-1 :available] 200))
            s' (step breached :internal-transfer [:acct-1 :acct-0 50])]
        (is (= -50 (SUT/balance s' :acct-0)) "improving — permitted")
        (is (= 150 (SUT/balance s' :acct-1)))))))

(deftest create-and-activate-person-party-test
  (let [s0 (step SUT/init-state :open-account [])]
    (testing "create-person-party adds a pending person-party to the org"
      (let [s (step s0 :create-person-party [:org-0])]
        (is (= :pending (get-in s [:parties :party-1 :status])))
        (is (= :person (get-in s [:parties :party-1 :type])))
        (is (= :org-0 (get-in s [:parties :party-1 :org])))
        (is (= [:party-0 :party-1] (get-in s [:orgs :org-0 :parties])))))
    (testing "activate-party flips pending → active"
      (let [s (-> s0
                  (step :create-person-party [:org-0])
                  (step :activate-party [:party-1]))]
        (is (= :active (get-in s [:parties :party-1 :status])))))
    (testing "add-account against a pending party is rejected by :valid?"
      (let [s (step s0 :create-person-party [:org-0])
            spec (get SUT/model :add-account)]
        (is (false? ((:valid? spec) s {:args [:org-0 :party-1 :prod-0]}))
            "person-party hasn't been activated yet")))
    (testing "after activation, accounts open against the person-party"
      (let [s (-> s0
                  (step :create-person-party [:org-0])
                  (step :activate-party [:party-1])
                  (step :add-account [:org-0 :party-1 :prod-0]))]
        (is (= :party-1 (get-in s [:accounts :acct-1 :party])))
        (is (= :prod-0 (get-in s [:accounts :acct-1 :product])))))))

(deftest apply-fee-test
  (let [s (-> SUT/init-state
              (step :open-account []))]
    (testing "fee posts on a positive account"
      (let [funded (assoc-in s [:accounts :acct-0 :available] 100)
            s' (step funded :apply-fee [:acct-0 30])]
        (is (= 70 (SUT/balance s' :acct-0)))))
    (testing "fee bypasses the available rule and can drive negative"
      (let [funded (assoc-in s [:accounts :acct-0 :available] 50)
            s' (step funded :apply-fee [:acct-0 200])]
        (is (= -150 (SUT/balance s' :acct-0))
            "fees ignore the available-balance rule by design")))))
