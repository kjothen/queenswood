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

;; --- Pure template / mapping checks -------------------------------------

(deftest template-test
  (testing "exactly the seven canonical ledger accounts"
    (is (= ["1100" "1200" "2100" "2200" "2300" "2400" "2500"]
           (mapv :gl-code SUT/template))))
  (testing "2100/2200/2300 are control accounts; others are detail"
    (let [by-code
          (into {} (map (juxt :gl-code :gl-account-class)) SUT/template)]
      (is (= :gl-account-class-control (by-code "2100")))
      (is (= :gl-account-class-control (by-code "2200")))
      (is (= :gl-account-class-control (by-code "2300")))
      (is (= :gl-account-class-detail (by-code "1100")))
      (is (= :gl-account-class-detail (by-code "2400"))))))

(deftest control-code-for-product-type-test
  (is (= "2100"
         (SUT/control-code-for-product-type :product-type-sub-ledger-current)))
  (is (= "2200"
         (SUT/control-code-for-product-type :product-type-sub-ledger-savings)))
  (is (= "2300"
         (SUT/control-code-for-product-type
          :product-type-sub-ledger-term-deposit)))
  (testing "non-customer product types have no control"
    (is (nil? (SUT/control-code-for-product-type :product-type-unknown)))))

(deftest mandatory?-test
  (testing "every seeded gl-code is mandatory in this wave"
    (doseq [code ["1100" "1200" "2100" "2200" "2300" "2400" "2500"]]
      (is (true? (SUT/mandatory? code)) (str code " should be mandatory"))))
  (testing "unknown gl-codes are not mandatory"
    (is (false? (SUT/mandatory? "9999")))
    (is (false? (SUT/mandatory? nil)))))

;; --- FDB-backed seed / lookup / expand-legs -----------------------------

(deftest seed!-test
  (with-test-system
   [sys "classpath:bank-ledger-account/application-test.yml"]
   (let [config (fdb-config sys)
         bank-id "bnk.test-seed"]
     (testing "seeds seven ledger accounts per currency, each with a led. id"
       (nom-test> [accounts (SUT/seed! config bank-id ["GBP"])
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
     (nom-test> [_ (SUT/seed! config bank-id ["GBP"])
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

(deftest expand-legs-test
  (with-test-system
   [sys "classpath:bank-ledger-account/application-test.yml"]
   (let [config (fdb-config sys)
         bank-id "bnk.test-expand"]
     (nom-test> [_ (SUT/seed! config bank-id ["GBP"])
                 control (SUT/find-by-code config bank-id "2100")
                 customer-leg {:account-id "acc.customer1"
                               :product-type :product-type-sub-ledger-current
                               :balance-type :balance-type-default
                               :balance-status :balance-status-posted
                               :side :side-credit
                               :amount 1000}
                 expanded (SUT/expand-legs config bank-id [customer-leg])
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
             result (SUT/expand-legs config bank-id [gl-leg])]
         (is (not (error/anomaly? result)))
         (is (= [gl-leg] result)))))))
