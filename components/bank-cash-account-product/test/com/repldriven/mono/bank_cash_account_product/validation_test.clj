(ns com.repldriven.mono.bank-cash-account-product.validation-test
  (:require
    [com.repldriven.mono.bank-cash-account-product.validation :as SUT]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(def ^:private bp-default
  {:balance-type :balance-type-default :balance-status :balance-status-posted})

(def ^:private bp-cash
  {:balance-type :balance-type-cash :balance-status :balance-status-posted})

(def ^:private good-version
  {:balance-products [bp-default bp-cash]
   :allowed-currencies ["GBP" "EUR"]
   :allowed-payment-address-schemes [:payment-address-scheme-scan
                                     :payment-address-scheme-iban]})

(deftest unique-fields?-test
  (testing "returns true when every repeated-value field is distinct"
    (is (true? (SUT/unique-fields? good-version))))
  (testing "returns true when fields are absent or empty (nothing to check)"
    (is (true? (SUT/unique-fields? {})))
    (is (true? (SUT/unique-fields? {:balance-products []
                                    :allowed-currencies []
                                    :allowed-payment-address-schemes []}))))
  (testing "rejects when balance-products has duplicates"
    (let [r (SUT/unique-fields?
             (assoc good-version :balance-products [bp-default bp-default]))]
      (is (error/rejection? r))
      (is (= :cash-account-product/duplicate-items (error/kind r)))
      (is (= [:balance-products] (:fields (error/payload r))))))
  (testing "rejects when two entries are equal but differ in key order"
    (let [r (SUT/unique-fields? (assoc good-version
                                       :balance-products
                                       [{:balance-type :balance-type-default
                                         :balance-status :balance-status-posted}
                                        {:balance-status :balance-status-posted
                                         :balance-type
                                         :balance-type-default}]))]
      (is (error/rejection? r))
      (is (= [:balance-products] (:fields (error/payload r))))))
  (testing "rejects when allowed-currencies has duplicates"
    (let [r (SUT/unique-fields?
             (assoc good-version :allowed-currencies ["GBP" "GBP"]))]
      (is (error/rejection? r))
      (is (= [:allowed-currencies] (:fields (error/payload r))))))
  (testing "rejects when allowed-payment-address-schemes has duplicates"
    (let [r (SUT/unique-fields? (assoc good-version
                                       :allowed-payment-address-schemes
                                       [:payment-address-scheme-scan
                                        :payment-address-scheme-scan]))]
      (is (error/rejection? r))
      (is (= [:allowed-payment-address-schemes] (:fields (error/payload r))))))
  (testing "reports every duplicated field when more than one is non-distinct"
    (let [r (SUT/unique-fields? {:balance-products [bp-default bp-default]
                                 :allowed-currencies ["GBP" "GBP"]
                                 :allowed-payment-address-schemes
                                 [:payment-address-scheme-scan]})]
      (is (error/rejection? r))
      (is (= [:balance-products :allowed-currencies]
             (:fields (error/payload r))))
      (is (= "Duplicate items in: balance-products, allowed-currencies"
             (:message (error/payload r))))))
  (testing "ignores duplicates of length 1 — single item is trivially distinct"
    (is (true? (SUT/unique-fields?
                (assoc good-version :balance-products [bp-default]))))))
