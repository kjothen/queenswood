(ns ^:eftest/synchronized com.repldriven.mono.fdb.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.fdb.interface :as SUT]

    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.schema.interface :as schema]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [com.repldriven.mono.utility.interface :as utility]

    [clojure.test :refer [deftest is testing]]))

(defn- test-str-kv
  [sys]
  (let [db (system/instance sys [:fdb :db])]
    (testing "can store and retrieve string values as raw KV"
      (nom-test> [_ (SUT/set-str db "test-key" "test-value")
                  result (SUT/get-str db "test-key")
                  _ (is (= "test-value" result))]))))

(defn- test-proto-kv
  [sys]
  (let [alice {:name "Alice"
               :id 1
               :email "alice@example.com"
               :phones [{:number "555-0100" :type :mobile}]}
        db (system/instance sys [:fdb :db])]
    (testing "can store and retrieve Person records as raw KV"
      (nom-test> [_ (SUT/set-bytes db "person/1" (schema/Person->pb alice))
                  retrieved-alice (error/nom-> (SUT/get-bytes db "person/1")
                                               schema/pb->Person)
                  _ (is (= alice (utility/record->map retrieved-alice)))]))))

(defn- test-record-layer
  [sys record-store]
  (let [alice {:name "Alice" :id 1 :email "alice@example.com" :phones []}
        record-db (system/instance sys [:fdb :record-db])]
    (testing "can save and load Person records via FDB Record Layer"
      (nom-test> [_ (SUT/transact
                     record-db
                     record-store
                     "persons"
                     (fn [store]
                       (SUT/save-record store (schema/Person->java alice))))
                  retrieved (error/nom-> (SUT/transact record-db
                                                       record-store
                                                       "persons"
                                                       (fn [store]
                                                         (SUT/load-record
                                                          store
                                                          1)))
                                         schema/pb->Person)
                  _ (is (= alice (utility/record->map retrieved)))]))))

(defn- test-record-layer-consumer
  [sys record-store]
  (let [alice {:account-id (encryption/generate-id "ba")
               :customer-id "cust-1"
               :name "Alice"
               :currency "GBP"
               :payment-addresses []
               :status "open"
               :created-at-ms 0
               :updated-at-ms 0}
        bob {:account-id (encryption/generate-id "ba")
             :customer-id "cust-2"
             :name "Bob"
             :currency "USD"
             :payment-addresses []
             :status "open"
             :created-at-ms 0
             :updated-at-ms 0}
        record-db (system/instance sys [:fdb :record-db])
        received (atom [])]
    (testing
      "consumer reads changelog entries and calls handler with record bytes"
      (nom-test> [_
                  (SUT/transact
                   record-db
                   record-store
                   "accounts"
                   (fn [store]
                     (SUT/save-record store (schema/Account->java alice))
                     (SUT/write-changelog store "accounts" (:account-id alice))
                     (SUT/save-record store (schema/Account->java bob))
                     (SUT/write-changelog store "accounts" (:account-id bob))))
                  _ (SUT/process-changelog record-db
                                           record-store
                                           "test-consumer"
                                           "accounts"
                                           (fn [_store record]
                                             (swap! received conj record)))
                  _ (is (= 2 (count @received)))
                  retrieved-alice (error/nom-> (first @received)
                                               schema/pb->Account)
                  _ (is (= alice (utility/record->map retrieved-alice)))
                  retrieved-bob (error/nom-> (second @received)
                                             schema/pb->Account)
                  _ (is (= bob (utility/record->map retrieved-bob)))]))))

(defn- test-query-records
  [sys record-store]
  (let [alice {:name "Alice" :id 10 :email "alice@query.com" :phones []}
        bob {:name "Bob" :id 11 :email "bob@query.com" :phones []}
        record-db (system/instance sys [:fdb :record-db])]
    (testing "can query records by field value"
      (nom-test> [_ (SUT/transact
                     record-db
                     record-store
                     "persons"
                     (fn [store]
                       (SUT/save-record store (schema/Person->java alice))
                       (SUT/save-record store (schema/Person->java bob))))
                  results (SUT/transact record-db
                                        record-store
                                        "persons"
                                        (fn [store]
                                          (SUT/query-records
                                           store
                                           "Person"
                                           "email"
                                           "alice@query.com")))
                  _ (is (= 1 (count results)))
                  retrieved (error/nom-> (first results) schema/pb->Person)
                  _ (is (= alice (utility/record->map retrieved)))]))))

(deftest kv-test
  (with-test-system [sys "classpath:fdb/application-test.yml"]
                    (test-str-kv sys)
                    (test-proto-kv sys)))

(deftest store-test
  (with-test-system [sys "classpath:fdb/application-test.yml"]
                    (let [record-store (system/instance sys [:fdb :store])]
                      (test-record-layer sys record-store)
                      (test-query-records sys record-store)
                      (test-record-layer-consumer sys record-store))))

(deftest meta-store-test
  (with-test-system [sys "classpath:fdb/application-test.yml"]
                    (let [record-store (system/instance sys [:fdb :meta-store])]
                      (test-record-layer sys record-store)
                      (test-query-records sys record-store)
                      (test-record-layer-consumer sys record-store))))
