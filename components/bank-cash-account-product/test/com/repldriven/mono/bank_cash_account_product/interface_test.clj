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

(deftest new-product-test
  (with-test-system
   [sys "classpath:bank-cash-account-product/application-test.yml"]
   (let [config (fdb-config sys)
         org-id "org_test_new_product"]
     (testing "creates product as initial draft v1"
       (nom-test> [v (SUT/new-product config org-id draft-data)
                   _ (is (= "Current Account" (:name v)))
                   _ (is (string? (:product-id v)))
                   _ (is (string? (:version-id v)))
                   _ (is (= org-id (:organization-id v)))
                   _ (is (= 1 (:version-number v)))
                   _ (is (= :cash-account-product-status-draft (:status v)))
                   _ (is (pos? (:created-at v)))])))))

(deftest open-draft-test
  (with-test-system
   [sys "classpath:bank-cash-account-product/application-test.yml"]
   (let [config (fdb-config sys)
         org-id "org_test_open_draft"]
     (testing "409 when a draft already exists"
       (let [v1 (SUT/new-product config org-id draft-data)
             rejected
             (SUT/open-draft config org-id (:product-id v1) draft-data)]
         (is (error/rejection? rejected))
         (is (= :cash-account-product/draft-already-exists
                (error/kind rejected)))))
     (testing "opens v2 after v1 is published"
       (nom-test> [v1 (SUT/new-product config org-id draft-data)
                   _
                   (SUT/publish config org-id (:product-id v1) (:version-id v1))
                   v2 (SUT/open-draft config org-id (:product-id v1) draft-data)
                   _ (is (= 2 (:version-number v2)))
                   _ (is (not= (:version-id v1) (:version-id v2)))
                   _ (is (= :cash-account-product-status-draft (:status v2)))]))
     (testing "opens v2 after v1 is discarded"
       (nom-test> [v1 (SUT/new-product config org-id asset-draft-data)
                   _ (SUT/discard-draft config
                                        org-id
                                        (:product-id v1)
                                        (:version-id v1))
                   v2 (SUT/open-draft config
                                      org-id
                                      (:product-id v1)
                                      asset-draft-data)
                   _ (is (= 2 (:version-number v2)))
                   _ (is (= :cash-account-product-status-draft (:status v2)))])))))

(deftest update-draft-test
  (with-test-system
   [sys "classpath:bank-cash-account-product/application-test.yml"]
   (let [config (fdb-config sys)
         org-id "org_test_update_draft"]
     (testing "updates existing draft in place, preserving vid and number"
       (nom-test> [v1 (SUT/new-product config org-id draft-data)
                   updated (SUT/update-draft config
                                             org-id
                                             (:product-id v1)
                                             (:version-id v1)
                                             (assoc draft-data
                                                    :name "Renamed"
                                                    :valid-from "2025-02-01"))
                   _ (is (= (:version-id v1) (:version-id updated)))
                   _ (is (= 1 (:version-number updated)))
                   _ (is (= "Renamed" (:name updated)))
                   _ (is (= "2025-02-01" (:valid-from updated)))
                   _ (is (= :cash-account-product-status-draft
                            (:status updated)))]))
     (testing "404 when the version-id is unknown"
       (let [v1 (SUT/new-product config org-id draft-data)
             rejected (SUT/update-draft config
                                        org-id
                                        (:product-id v1)
                                        "prv.unknown"
                                        draft-data)]
         (is (error/rejection? rejected))
         (is (= :cash-account-product/version-not-found
                (error/kind rejected)))))
     (testing "409 when the version is published"
       (let [v1 (SUT/new-product config org-id draft-data)
             _ (SUT/publish config org-id (:product-id v1) (:version-id v1))
             rejected (SUT/update-draft config
                                        org-id
                                        (:product-id v1)
                                        (:version-id v1)
                                        draft-data)]
         (is (error/rejection? rejected))
         (is (= :cash-account-product/version-immutable
                (error/kind rejected)))))
     (testing "409 when the version is discarded"
       (let [v1 (SUT/new-product config org-id asset-draft-data)
             _
             (SUT/discard-draft config org-id (:product-id v1) (:version-id v1))
             rejected (SUT/update-draft config
                                        org-id
                                        (:product-id v1)
                                        (:version-id v1)
                                        asset-draft-data)]
         (is (error/rejection? rejected))
         (is (= :cash-account-product/version-immutable
                (error/kind rejected))))))))

(deftest discard-draft-test
  (with-test-system
   [sys "classpath:bank-cash-account-product/application-test.yml"]
   (let [config (fdb-config sys)
         org-id "org_test_discard_draft"]
     (testing "transitions draft to discarded and stamps :discarded-at"
       (nom-test> [v1 (SUT/new-product config org-id draft-data)
                   discarded (SUT/discard-draft config
                                                org-id
                                                (:product-id v1)
                                                (:version-id v1))
                   _ (is (= :cash-account-product-status-discarded
                            (:status discarded)))
                   _ (is (pos? (:discarded-at discarded)))
                   reloaded (SUT/get-version config
                                             org-id
                                             (:product-id v1)
                                             (:version-id v1))
                   _ (is (= :cash-account-product-status-discarded
                            (:status reloaded)))]))
     (testing "409 when the version is published"
       (let [v1 (SUT/new-product config org-id draft-data)
             _ (SUT/publish config org-id (:product-id v1) (:version-id v1))
             rejected (SUT/discard-draft config
                                         org-id
                                         (:product-id v1)
                                         (:version-id v1))]
         (is (error/rejection? rejected))
         (is (= :cash-account-product/version-immutable
                (error/kind rejected))))))))

(deftest publish-test
  (with-test-system
   [sys "classpath:bank-cash-account-product/application-test.yml"]
   (let [config (fdb-config sys)
         org-id "org_test_publish"]
     (testing "transitions draft to published"
       (nom-test> [v1 (SUT/new-product config org-id draft-data)
                   published
                   (SUT/publish config org-id (:product-id v1) (:version-id v1))
                   _ (is (= :cash-account-product-status-published
                            (:status published)))]))
     (testing "404 when the version-id is unknown"
       (let [v1 (SUT/new-product config org-id draft-data)
             rejected
             (SUT/publish config org-id (:product-id v1) "prv.unknown")]
         (is (error/rejection? rejected))
         (is (= :cash-account-product/version-not-found
                (error/kind rejected)))))
     (testing "409 republishing the same version"
       (let [v1 (SUT/new-product config org-id draft-data)
             _ (SUT/publish config org-id (:product-id v1) (:version-id v1))
             rejected
             (SUT/publish config org-id (:product-id v1) (:version-id v1))]
         (is (error/rejection? rejected))
         (is (= :cash-account-product/version-immutable
                (error/kind rejected)))))
     (testing "two consecutive publishes produce two published versions"
       (nom-test> [v1 (SUT/new-product config org-id asset-draft-data)
                   _
                   (SUT/publish config org-id (:product-id v1) (:version-id v1))
                   v2 (SUT/open-draft config
                                      org-id
                                      (:product-id v1)
                                      asset-draft-data)
                   _
                   (SUT/publish config org-id (:product-id v2) (:version-id v2))
                   {:keys [versions]}
                   (SUT/get-product config org-id (:product-id v1))
                   published (filterv (fn [v]
                                        (=
                                         :cash-account-product-status-published
                                         (:status v)))
                                      versions)
                   _ (is (= 2 (count published)))])))))

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

(deftest get-version-test
  (with-test-system
   [sys "classpath:bank-cash-account-product/application-test.yml"]
   (let [config (fdb-config sys)
         org-id "org_test_get_version"]
     (testing "returns the version by composite PK"
       (nom-test> [v1 (SUT/new-product config org-id draft-data)
                   loaded (SUT/get-version config
                                           org-id
                                           (:product-id v1)
                                           (:version-id v1))
                   _ (is (= (:version-id v1) (:version-id loaded)))
                   _ (is (= 1 (:version-number loaded)))]))
     (testing "404 when the version-id is unknown"
       (let [v1 (SUT/new-product config org-id draft-data)
             rejected
             (SUT/get-version config org-id (:product-id v1) "prv.unknown")]
         (is (error/rejection? rejected))
         (is (= :cash-account-product/version-not-found
                (error/kind rejected))))))))
