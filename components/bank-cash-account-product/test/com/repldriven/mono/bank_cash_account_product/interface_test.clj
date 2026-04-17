(ns ^:eftest/synchronized
    com.repldriven.mono.bank-cash-account-product.interface-test
  (:require
    [com.repldriven.mono.bank-cash-account-product.interface :as SUT]

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

(deftest create-product-test
  (with-test-system
   [sys "classpath:bank-cash-account-product/application-test.yml"]
   (let [config (fdb-config sys)
         org-id "org_test_01"]
     (testing "creates product as initial draft v1"
       (nom-test> [result (SUT/new-product config
                                           org-id
                                           {:name "Current Account"
                                            :account-type :account-type-current
                                            :balance-sheet-side
                                            :balance-sheet-side-liability
                                            :allowed-currencies ["GBP" "USD"]})
                   version (:version result)
                   _ (is (= "Current Account" (:name version)))
                   _ (is (string? (:product-id version)))
                   _ (is (= org-id (:organization-id version)))
                   _ (is (= 1 (:version-number version)))
                   _ (is (= :cash-account-product-version-status-draft
                            (:status version)))
                   _ (is (= :account-type-current (:account-type version)))
                   _ (is (= :balance-sheet-side-liability
                            (:balance-sheet-side version)))
                   _ (is (= ["GBP" "USD"] (:allowed-currencies version)))
                   _ (is (pos? (:created-at version)))])))))

(deftest version-lifecycle-test
  (with-test-system
   [sys "classpath:bank-cash-account-product/application-test.yml"]
   (let [config (fdb-config sys)
         org-id "org_test_02"]
     (testing "upsert-draft updates the existing draft in place"
       (nom-test> [result (SUT/new-product config
                                           org-id
                                           {:name "Draft v1"
                                            :account-type :account-type-current
                                            :balance-sheet-side
                                            :balance-sheet-side-asset})
                   product-id (get-in result [:version :product-id])
                   v1-id (get-in result [:version :version-id])
                   updated (SUT/upsert-draft
                            config
                            org-id
                            product-id
                            {:name "Draft v1 renamed"
                             :account-type :account-type-current
                             :balance-sheet-side :balance-sheet-side-asset
                             :valid-from "2025-02-01"})
                   v (:version updated)
                   _ (is (= v1-id (:version-id v)))
                   _ (is (= 1 (:version-number v)))
                   _ (is (= "Draft v1 renamed" (:name v)))
                   _ (is (= "2025-02-01" (:valid-from v)))
                   _ (is (= :cash-account-product-version-status-draft
                            (:status v)))]))
     (testing "upsert-draft after publish creates v2 as a new draft"
       (nom-test> [result (SUT/new-product
                           config
                           org-id
                           {:name "Term Deposit"
                            :account-type :account-type-term-deposit
                            :balance-sheet-side :balance-sheet-side-liability})
                   product-id (get-in result [:version :product-id])
                   v1-id (get-in result [:version :version-id])
                   _ (SUT/publish config org-id product-id)
                   v2-result (SUT/upsert-draft
                              config
                              org-id
                              product-id
                              {:name "Term Deposit"
                               :account-type :account-type-term-deposit
                               :balance-sheet-side :balance-sheet-side-liability
                               :valid-from "2025-01-01"})
                   v2 (:version v2-result)
                   _ (is (= 2 (:version-number v2)))
                   _ (is (not= v1-id (:version-id v2)))
                   _ (is (= :cash-account-product-version-status-draft
                            (:status v2)))
                   _ (is (= "2025-01-01" (:valid-from v2)))]))
     (testing "get and list versions"
       (nom-test> [result (SUT/new-product config
                                           org-id
                                           {:name "Get Version Test"
                                            :account-type :account-type-current
                                            :balance-sheet-side
                                            :balance-sheet-side-asset})
                   product-id (get-in result [:version :product-id])
                   version-id (get-in result [:version :version-id])
                   loaded (SUT/get-version config org-id product-id version-id)
                   _ (is (= :account-type-current (:account-type loaded)))
                   _ (is (= 1 (:version-number loaded)))
                   versions (SUT/get-versions config org-id product-id)
                   _ (is (= 1 (count (:versions versions))))]))
     (testing "publish flips the latest draft to published"
       (nom-test> [result (SUT/new-product config
                                           org-id
                                           {:name "Publish Test"
                                            :account-type :account-type-current
                                            :balance-sheet-side
                                            :balance-sheet-side-asset})
                   product-id (get-in result [:version :product-id])
                   published (SUT/publish config org-id product-id)
                   _ (is (= :cash-account-product-version-status-published
                            (:status published)))]))
     (testing "publish rejects with :no-draft when latest is published"
       (let [result (SUT/new-product config
                                     org-id
                                     {:name "Double Publish Test"
                                      :account-type :account-type-current
                                      :balance-sheet-side
                                      :balance-sheet-side-asset})
             product-id (get-in result [:version :product-id])]
         (SUT/publish config org-id product-id)
         (let [rejected (SUT/publish config org-id product-id)]
           (is (error/rejection? rejected))
           (is (= :cash-account-product/no-draft (error/kind rejected)))))))))

(deftest get-products-test
  (with-test-system
   [sys "classpath:bank-cash-account-product/application-test.yml"]
   (let [config (fdb-config sys)
         org-id "org_test_products"]
     (testing "returns the latest version per product"
       (nom-test> [p1 (SUT/new-product config
                                       org-id
                                       {:name "P1"
                                        :account-type :account-type-current
                                        :balance-sheet-side
                                        :balance-sheet-side-asset})
                   p1-id (get-in p1 [:version :product-id])
                   _ (SUT/publish config org-id p1-id)
                   _ (SUT/upsert-draft config
                                       org-id
                                       p1-id
                                       {:name "P1 v2"
                                        :account-type :account-type-current
                                        :balance-sheet-side
                                        :balance-sheet-side-asset})
                   p2 (SUT/new-product config
                                       org-id
                                       {:name "P2"
                                        :account-type :account-type-savings
                                        :balance-sheet-side
                                        :balance-sheet-side-liability})
                   p2-id (get-in p2 [:version :product-id])
                   {:keys [versions]} (SUT/get-products config org-id)
                   by-product (into {}
                                    (map (juxt :product-id identity) versions))
                   _ (is (= 2 (count versions)))
                   _ (is (= 2 (:version-number (by-product p1-id))))
                   _ (is (= :cash-account-product-version-status-draft
                            (:status (by-product p1-id))))
                   _ (is (= 1 (:version-number (by-product p2-id))))
                   _ (is (= :cash-account-product-version-status-draft
                            (:status (by-product p2-id))))])))))

(deftest get-published-version-test
  (with-test-system
   [sys "classpath:bank-cash-account-product/application-test.yml"]
   (let [config (fdb-config sys)
         org-id "org_test_03"]
     (testing "returns nil when no published version"
       (nom-test> [result (SUT/new-product config
                                           org-id
                                           {:name "Unpublished"
                                            :account-type :account-type-current
                                            :balance-sheet-side
                                            :balance-sheet-side-asset})
                   product-id (get-in result [:version :product-id])
                   published
                   (SUT/get-published-version config org-id product-id)
                   _ (is (nil? published))]))
     (testing "returns published v1, then published v2"
       (nom-test> [result (SUT/new-product config
                                           org-id
                                           {:name "Versioned Product"
                                            :account-type :account-type-current
                                            :balance-sheet-side
                                            :balance-sheet-side-liability})
                   product-id (get-in result [:version :product-id])
                   _ (SUT/publish config org-id product-id)
                   _ (SUT/upsert-draft config
                                       org-id
                                       product-id
                                       {:name "Versioned Product"
                                        :account-type :account-type-current
                                        :balance-sheet-side
                                        :balance-sheet-side-liability})
                   current (SUT/get-published-version config org-id product-id)
                   _ (is (= 1 (:version-number current)))
                   _ (is (= :cash-account-product-version-status-published
                            (:status current)))
                   _ (SUT/publish config org-id product-id)
                   current2 (SUT/get-published-version config org-id product-id)
                   _ (is (= 2 (:version-number current2)))
                   _ (is (= :cash-account-product-version-status-published
                            (:status current2)))])))))
