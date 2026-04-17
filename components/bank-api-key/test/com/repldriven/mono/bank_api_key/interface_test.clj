(ns ^:eftest/synchronized com.repldriven.mono.bank-api-key.interface-test
  (:require
    [com.repldriven.mono.bank-api-key.interface :as SUT]

    [com.repldriven.mono.bank-organization.interface :as organizations]

    [com.repldriven.mono.bank-tier.interface]
    [com.repldriven.mono.encryption.interface :as encryption]
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

(deftest get-api-key-test
  (with-test-system
   [sys "classpath:bank-api-key/application-test.yml"]
   (let [config (fdb-config sys)]
     (testing "finds key by hash computed from key-secret"
       (nom-test> [result (organizations/new-organization
                           config
                           "Hash Org" :organization-type-customer
                           :tier-type-micro ["GBP"])
                   org-id (get-in result
                                  [:organization
                                   :organization-id])
                   key-hash (encryption/hash-token (:key-secret result))
                   found (SUT/get-api-key config key-hash)
                   _ (is (some? found))
                   _ (is (= org-id (:organization-id found)))
                   _ (is (nil? (:key-hash found)))])))))

(deftest get-api-keys-test
  (with-test-system
   [sys "classpath:bank-api-key/application-test.yml"]
   (let [config (fdb-config sys)]
     (testing "lists api keys for an organization"
       (nom-test> [result (organizations/new-organization
                           config
                           "List Org" :organization-type-customer
                           :tier-type-micro ["GBP"])
                   org-id (:organization-id (:organization result))
                   keys (SUT/get-api-keys config org-id)
                   _ (is (= 1 (count keys)))
                   k (first keys)
                   _ (is (= org-id (:organization-id k)))
                   _ (is (= "default" (:name k)))
                   _ (is (string? (:key-prefix k)))
                   _ (is (nil? (:key-hash k)))])))))
