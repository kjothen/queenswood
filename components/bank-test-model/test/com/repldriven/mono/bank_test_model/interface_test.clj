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
  (testing "open-account allocates the next synthetic id"
    (let [s0 SUT/init-state
          s1 (step s0 :open-account [])]
      (is (= [:acct-0] (SUT/known-accounts s1)))
      (is (= 0 (SUT/balance s1 :acct-0)))
      (is (= 1 (:next-id s1)))))
  (testing "successive opens increment the counter"
    (let [s2 (-> SUT/init-state
                 (step :open-account [])
                 (step :open-account []))]
      (is (= [:acct-0 :acct-1] (sort (SUT/known-accounts s2))))
      (is (= 2 (:next-id s2))))))

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
