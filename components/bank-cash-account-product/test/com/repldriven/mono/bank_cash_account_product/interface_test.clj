(ns ^:eftest/synchronized
    com.repldriven.mono.bank-cash-account-product.interface-test
  "Last surviving integration tests for cash-account-product. Both
  pin newest-first ordering on the read paths — the projection
  sorts canonically before comparing, so it can't catch ordering
  regressions in `get-product` / `get-products` from the model-
  equality check alone.

  Lifecycle behaviours are covered elsewhere:
    - happy paths and version state transitions → property test
      via :create-product, :publish-product, :open-draft,
      :discard-draft, plus the multi-version projection.
    - 409 rejections (version-immutable, draft-already-exists) →
      domain_test.clj as pure-function tests.
    - 404 rejections (version-not-found, product-not-found) →
      cash-account-product-not-found.edn scenario, which uses the
      runner's :try-product-op-with-bogus-id verb to drive each
      operation and pins the contractual anomaly kind."
  (:require
    [com.repldriven.mono.bank-cash-account-product.interface :as SUT]

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

(deftest get-product-ordering-test
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
                   _ (is (= [2 1] (mapv :version-number versions)))])))))

(deftest get-products-ordering-test
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
