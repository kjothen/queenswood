(ns com.repldriven.mono.bank-cash-account.validation-test
  "Pure-function tests for the domain validation predicates. These
  back the rejection paths the open-cash-account command surfaces
  (`:cash-account-product/not-published`, `:cash-account/invalid-
  currency`, `:cash-account/party-status`) — exercising them at
  the domain layer keeps the brick fast and leaves only true
  boundary concerns (party/product/account not-found, no-payment-
  schemes through the addresses path) in interface_test.clj."
  (:require
    [com.repldriven.mono.bank-cash-account.validation :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(deftest valid-product?-test
  (testing "returns true for a published version"
    (is (true? (SUT/valid-product? {:status
                                    :cash-account-product-status-published
                                    :version-id "prv.001"}))))
  (testing "rejects a draft version"
    (let [r (SUT/valid-product? {:status :cash-account-product-status-draft
                                 :version-id "prv.001"})]
      (is (error/rejection? r))
      (is (= :cash-account-product/not-published (error/kind r)))))
  (testing "rejects a discarded version"
    (let [r (SUT/valid-product? {:status :cash-account-product-status-discarded
                                 :version-id "prv.001"})]
      (is (error/rejection? r))
      (is (= :cash-account-product/not-published (error/kind r))))))

(deftest valid-currency?-test
  (testing "returns true when the currency is in :allowed-currencies"
    (is (true? (SUT/valid-currency? "GBP"
                                    {:allowed-currencies ["GBP" "USD"]}))))
  (testing "rejects when the currency is not in the allowed list"
    (let [r (SUT/valid-currency? "EUR" {:allowed-currencies ["GBP" "USD"]})]
      (is (error/rejection? r))
      (is (= :cash-account/invalid-currency (error/kind r)))))
  (testing "an empty allowed list is treated as unrestricted"
    (is (true? (SUT/valid-currency? "EUR" {:allowed-currencies []})))))

(deftest valid-party?-test
  (testing "returns true for an active party"
    (is (true? (SUT/valid-party? {:status :party-status-active}))))
  (testing "rejects a pending party"
    (let [r (SUT/valid-party? {:status :party-status-pending})]
      (is (error/rejection? r))
      (is (= :cash-account/party-status (error/kind r)))))
  (testing "rejects a closed party"
    (let [r (SUT/valid-party? {:status :party-status-closed})]
      (is (error/rejection? r))
      (is (= :cash-account/party-status (error/kind r))))))
