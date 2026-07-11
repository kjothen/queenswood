(ns com.repldriven.mono.bank-bank.domain-test
  "Pure-function tests for the rejection paths of bank provisioning:
  `:onboarding/company-not-active` when the bound company snapshot is
  not active, and `:membership/already-exists` from the sole-membership
  check core runs inside the provisioning transaction."
  (:require
    [com.repldriven.mono.bank-bank.domain :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(def ^:private permissive-policies
  "Single allow-everything policy — an empty fields map in the oneof
  variant matches every request because the matcher only constrains
  on set fields."
  [{:enabled true :capabilities [{:kind {:bank {}} :effect :effect-allow}]}])

(def ^:private active-binding
  {:registry "uk-companies-house"
   :company-number "12345678"
   :company-name "Acme Ltd"
   :company-status "active"})

(deftest new-bank-test
  (testing "builds a bnk.-prefixed bank stamped with the binding"
    (let [bank (SUT/new-bank "Acme"
                             :bank-status-test
                             "000001"
                             active-binding
                             permissive-policies)]
      (is (re-find #"^bnk\." (:bank-id bank)))
      (is (= :bank-status-test (:status bank)))
      (is (= "000001" (:sort-code bank)))
      (is (= active-binding (:company-binding bank)))))
  (testing "omits :company-binding for admin-provisioned banks"
    (let [bank (SUT/new-bank "Acme"
                             :bank-status-test
                             "000001"
                             nil
                             permissive-policies)]
      (is (not (contains? bank :company-binding)))))
  (testing "rejects a binding whose company is not active"
    (let [r (SUT/new-bank "Acme"
                          :bank-status-test
                          "000001"
                          (assoc active-binding :company-status "dissolved")
                          permissive-policies)]
      (is (error/rejection? r))
      (is (= :onboarding/company-not-active (error/kind r))))))

(deftest check-sole-membership-test
  (testing "nil when the user has no memberships"
    (is (nil? (SUT/check-sole-membership "usr.1" []))))
  (testing "rejects when the user already belongs to a bank"
    (let [r (SUT/check-sole-membership "usr.1" [{:bank-id "bnk.1"}])]
      (is (error/rejection? r))
      (is (= :membership/already-exists (error/kind r))))))
