(ns ^:eftest/synchronized com.repldriven.mono.bank-ledger-account.interface-test
  (:require
    [com.repldriven.mono.bank-ledger-account.interface :as SUT]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.testcontainers.interface]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(defn- fdb-config
  [sys]
  {:record-db (system/instance sys [:fdb :record-db])
   :record-store (system/instance sys [:fdb :store])})

(def ^:private template
  "Test chart of accounts passed into seed!, mirroring the canonical
  set bank-bank seeds at provisioning time."
  [{:gl-code "1100"
    :name "Cash at correspondent"
    :gl-account-type :gl-account-type-asset
    :gl-account-class :gl-account-class-detail
    :required :required-mandatory}
   {:gl-code "1200"
    :name "Pending outbound payments"
    :gl-account-type :gl-account-type-asset
    :gl-account-class :gl-account-class-detail
    :required :required-mandatory}
   {:gl-code "2100"
    :name "Customer deposits - current"
    :gl-account-type :gl-account-type-liability
    :gl-account-class :gl-account-class-control
    :required :required-mandatory}
   {:gl-code "2200"
    :name "Customer deposits - savings"
    :gl-account-type :gl-account-type-liability
    :gl-account-class :gl-account-class-control
    :required :required-mandatory}
   {:gl-code "2300"
    :name "Customer deposits - term deposits"
    :gl-account-type :gl-account-type-liability
    :gl-account-class :gl-account-class-control
    :required :required-mandatory}
   {:gl-code "2400"
    :name "Interest payable"
    :gl-account-type :gl-account-type-liability
    :gl-account-class :gl-account-class-detail
    :required :required-mandatory}
   {:gl-code "2500"
    :name "Suspense - unreconciled inbound"
    :gl-account-type :gl-account-type-liability
    :gl-account-class :gl-account-class-detail
    :required :required-mandatory}])

(defn- seed!
  "Test helper: create every template row in GBP, returning the
  created accounts or the first anomaly."
  [config bank-id]
  (reduce (fn [acc row]
            (let [result (SUT/new-account config bank-id "GBP" row)]
              (if (error/anomaly? result)
                (reduced result)
                (conj acc result))))
          []
          template))

;; --- Pure mapping checks ------------------------------------------------

(deftest product-type->control-code-test
  (is (= "2100"
         (SUT/product-type->control-code :product-type-sub-ledger-current)))
  (is (= "2200"
         (SUT/product-type->control-code :product-type-sub-ledger-savings)))
  (is (= "2300"
         (SUT/product-type->control-code
          :product-type-sub-ledger-term-deposit)))
  (testing "non-customer product types have no control"
    (is (nil? (SUT/product-type->control-code :product-type-unknown)))))

;; --- FDB-backed seed / lookup / add-control-legs
;; -----------------------------

(deftest seed!-test
  (with-test-system
   [sys "classpath:bank-ledger-account/application-test.yml"]
   (let [config (fdb-config sys)
         bank-id "bnk.test-seed"]
     (testing "seeds seven ledger accounts per currency, each with a led. id"
       (nom-test> [accounts (seed! config bank-id)
                   _ (is (= 7 (count accounts)))
                   _ (is (every? #(re-find #"^led\." (:ledger-account-id %))
                                 accounts))
                   _ (is (every? #(= "GBP" (:currency %)) accounts))]))
     (testing "each seeded account opens a default-posted balance"
       (nom-test> [control (SUT/find-by-code config bank-id "2100")
                   bals (balances/get-balances config
                                               (:ledger-account-id control))
                   _ (is (= 1 (count (:balances bals))))
                   _ (is (= :balance-type-default
                            (:balance-type (first (:balances bals)))))])))))

(deftest find-by-code-and-get-account-test
  (with-test-system
   [sys "classpath:bank-ledger-account/application-test.yml"]
   (let [config (fdb-config sys)
         bank-id "bnk.test-lookup"]
     (nom-test> [_ (seed! config bank-id)
                 control (SUT/find-by-code config bank-id "2100")
                 _ (is (= "2100" (:gl-code control)))
                 _ (is (= :gl-account-class-control
                          (:gl-account-class control)))
                 fetched
                 (SUT/get-account config bank-id (:ledger-account-id control))
                 _ (is (= (:ledger-account-id control)
                          (:ledger-account-id fetched)))])
     (testing "unknown code / id resolve to nil"
       (is (nil? (SUT/find-by-code config bank-id "9999")))
       (is (nil? (SUT/get-account config bank-id "led.nope")))))))

(deftest add-control-legs-test
  (with-test-system
   [sys "classpath:bank-ledger-account/application-test.yml"]
   (let [config (fdb-config sys)
         bank-id "bnk.test-expand"]
     (nom-test> [_ (seed! config bank-id)
                 control (SUT/find-by-code config bank-id "2100")
                 customer-leg {:account-id "acc.customer1"
                               :product-type :product-type-sub-ledger-current
                               :balance-type :balance-type-default
                               :balance-status :balance-status-posted
                               :side :side-credit
                               :amount 1000}
                 expanded (SUT/add-control-legs config bank-id [customer-leg])
                 _ (is (= 2 (count expanded)))
                 _ (is (= customer-leg (first expanded)))
                 control-leg (second expanded)
                 _ (is (= (:ledger-account-id control)
                          (:account-id control-leg)))
                 _ (is (= :side-credit (:side control-leg)))
                 _ (is (= 1000 (:amount control-leg)))])
     (testing "non-fanning legs pass through unchanged"
       (let [gl-leg {:account-id "led.something"
                     :balance-type :balance-type-default
                     :balance-status :balance-status-posted
                     :side :side-debit
                     :amount 1000}
             result (SUT/add-control-legs config bank-id [gl-leg])]
         (is (not (error/anomaly? result)))
         (is (= [gl-leg] result)))))))
