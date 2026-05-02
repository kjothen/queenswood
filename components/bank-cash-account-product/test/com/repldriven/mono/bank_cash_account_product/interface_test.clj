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

(def ^:private draft-data
  {:name "Current Account"
   :product-type :product-type-current
   :balance-sheet-side :balance-sheet-side-liability
   :allowed-currencies ["GBP" "USD"]})

(def ^:private asset-draft-data
  (assoc draft-data :balance-sheet-side :balance-sheet-side-asset))

;; new-product-test removed — happy-path "creates draft v1" is
;; covered end-to-end by every `:create-product` /
;; `:create-savings-product` step in the scenario-runner suite.

;; The 409 lifecycle rejections (version-immutable, draft-already-
;; exists) are pinned in domain_test.clj as pure-function tests.
;; Multi-version state-machine happy paths (open-draft v2 after
;; publish/discard, two consecutive publishes, draft → discarded
;; with `:discarded-at`) are exercised on every property-test trial
;; via :open-draft / :discard-draft / :publish-product. What stays
;; here are the 404 paths that genuinely round-trip through the
;; FDB store.

(deftest update-draft-404-test
  (with-test-system [sys
                     "classpath:bank-cash-account-product/application-test.yml"]
                    (let [config (fdb-config sys)
                          org-id "org_test_update_404"]
                      (testing "404 when the version-id is unknown"
                        (let [v1 (SUT/new-product config org-id draft-data)
                              rejected (SUT/update-draft config
                                                         org-id
                                                         (:product-id v1)
                                                         "prv.unknown"
                                                         draft-data)]
                          (is (error/rejection? rejected))
                          (is (= :cash-account-product/version-not-found
                                 (error/kind rejected))))))))

(deftest publish-404-test
  (with-test-system
   [sys "classpath:bank-cash-account-product/application-test.yml"]
   (let [config (fdb-config sys)
         org-id "org_test_publish_404"]
     (testing "404 when the version-id is unknown"
       (let [v1 (SUT/new-product config org-id draft-data)
             rejected
             (SUT/publish config org-id (:product-id v1) "prv.unknown")]
         (is (error/rejection? rejected))
         (is (= :cash-account-product/version-not-found
                (error/kind rejected))))))))

(deftest get-product-test
  (with-test-system
   [sys "classpath:bank-cash-account-product/application-test.yml"]
   (let [config (fdb-config sys)
         org-id "org_test_get_product"]
     (testing "returns aggregate with versions sorted newest-first"
       (nom-test> [v1 (SUT/new-product config org-id draft-data)
                   _
                   (SUT/publish config org-id (:product-id v1) (:version-id v1))
                   _ (SUT/open-draft config org-id (:product-id v1) draft-data)
                   aggregate (SUT/get-product config org-id (:product-id v1))
                   versions (:versions aggregate)
                   _ (is (= (:product-id v1) (:product-id aggregate)))
                   _ (is (= 2 (count versions)))
                   _ (is (= [2 1] (mapv :version-number versions)))]))
     (testing "404 when product-id is unknown"
       (let [rejected (SUT/get-product config org-id "prd.unknown")]
         (is (error/rejection? rejected))
         (is (= :cash-account-product/product-not-found
                (error/kind rejected))))))))

(deftest get-products-test
  (with-test-system
   [sys "classpath:bank-cash-account-product/application-test.yml"]
   (let [config (fdb-config sys)
         org-id "org_test_get_products"]
     (testing "returns one aggregate per product, newest-first versions"
       (nom-test> [p1 (SUT/new-product config org-id draft-data)
                   _
                   (SUT/publish config org-id (:product-id p1) (:version-id p1))
                   _ (SUT/open-draft config org-id (:product-id p1) draft-data)
                   p2 (SUT/new-product config org-id asset-draft-data)
                   {:keys [items]} (SUT/get-products config org-id)
                   by-product (into {} (map (juxt :product-id identity)) items)
                   p1-agg (by-product (:product-id p1))
                   p2-agg (by-product (:product-id p2))
                   _ (is (= 2 (count items)))
                   _ (is (= 2 (count (:versions p1-agg))))
                   _ (is (= [2 1] (mapv :version-number (:versions p1-agg))))
                   _ (is (= 1 (count (:versions p2-agg))))])))))

(deftest get-version-404-test
  ;; Happy-path PK lookup is exercised indirectly on every
  ;; :add-account in the scenario runner (cash-accounts/new-account
  ;; resolves the published version via get-version). What's
  ;; unique here is the 404 anomaly kind.
  (with-test-system
   [sys "classpath:bank-cash-account-product/application-test.yml"]
   (let [config (fdb-config sys)
         org-id "org_test_get_version_404"]
     (testing "404 when the version-id is unknown"
       (let [v1 (SUT/new-product config org-id draft-data)
             rejected
             (SUT/get-version config org-id (:product-id v1) "prv.unknown")]
         (is (error/rejection? rejected))
         (is (= :cash-account-product/version-not-found
                (error/kind rejected))))))))
