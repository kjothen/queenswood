(ns ^:eftest/synchronized com.repldriven.mono.fdb.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.fdb.interface :as SUT]

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
        bob {:name "Bob" :id 2 :email "bob@example.com" :phones []}
        book {:people [alice bob]}
        db (system/instance sys [:fdb :db])]
    (testing "can store and retrieve Person and AddressBook records as raw KV"
      (nom-test> [_ (SUT/set-bytes db "person/1" (schema/Person->pb alice))
                  _ (SUT/set-bytes db "person/2" (schema/Person->pb bob))
                  _ (SUT/set-bytes db
                                   "addressbook/main"
                                   (schema/AddressBook->pb book))
                  retrieved-alice (error/nom-> (SUT/get-bytes db "person/1")
                                               schema/pb->Person)
                  _ (is (= alice (utility/record->map retrieved-alice)))
                  retrieved-book (error/nom-> (SUT/get-bytes db
                                                             "addressbook/main")
                                              schema/pb->AddressBook)
                  _ (is (= book (utility/record->map retrieved-book)))]))))

(defn- test-record-layer
  [sys store]
  (let [alice {:name "Alice" :id 1 :email "alice@example.com" :phones []}
        record-db (system/instance sys [:fdb :record-db])]
    (testing "can save and load Person records via FDB Record Layer"
      (nom-test> [_ (SUT/save-record record-db
                                     store
                                     "persons"
                                     (schema/Person->java alice))
                  retrieved (error/nom->
                             (SUT/load-record record-db store "persons" 1)
                             schema/pb->Person)
                  _ (is (= alice (utility/record->map retrieved)))]))))

(defn- test-record-layer-consumer
  [sys store]
  (let [alice {:account-id (str (utility/uuidv7))
               :customer-id "cust-1"
               :name "Alice"
               :currency "GBP"
               :status "open"}
        bob {:account-id (str (utility/uuidv7))
             :customer-id "cust-2"
             :name "Bob"
             :currency "USD"
             :status "open"}
        record-db (system/instance sys [:fdb :record-db])
        received (atom [])]
    (testing
      "consumer reads changelog entries and calls handler with record bytes"
      (nom-test> [_
                  (SUT/transact
                   record-db
                   (fn [ctx]
                     (let [fdb-store (store ctx "accounts")]
                       (SUT/store-save fdb-store (schema/Account->java alice))
                       (SUT/write-changelog ctx "accounts" (:account-id alice))
                       (SUT/store-save fdb-store (schema/Account->java bob))
                       (SUT/write-changelog ctx "accounts" (:account-id bob)))))
                  _ (SUT/process-changelog record-db
                                           store
                                           "test-consumer"
                                           "accounts"
                                           (fn [record]
                                             (swap! received conj record)))
                  _ (is (= 2 (count @received)))
                  retrieved-alice (error/nom-> (first @received)
                                               schema/pb->Account)
                  _ (is (= alice (utility/record->map retrieved-alice)))
                  retrieved-bob (error/nom-> (second @received)
                                             schema/pb->Account)
                  _ (is (= bob (utility/record->map retrieved-bob)))]))))

(defn- test-query-records
  [sys store]
  (let [alice {:name "Alice" :id 10 :email "alice@query.com" :phones []}
        bob {:name "Bob" :id 11 :email "bob@query.com" :phones []}
        record-db (system/instance sys [:fdb :record-db])]
    (testing "can query records by field value"
      (nom-test> [_ (SUT/save-record record-db
                                     store
                                     "persons"
                                     (schema/Person->java alice))
                  _ (SUT/save-record record-db
                                     store
                                     "persons"
                                     (schema/Person->java bob))
                  results (SUT/query-records record-db
                                             store
                                             "persons" "Person"
                                             "email" "alice@query.com")
                  _ (is (= 1 (count results)))
                  retrieved (error/nom-> (first results) schema/pb->Person)
                  _ (is (= alice (utility/record->map retrieved)))]))))


(deftest kv-test
  (with-test-system [sys "classpath:fdb/application-test.yml"]
                    (test-str-kv sys)
                    (test-proto-kv sys)))

(deftest store-test
  (with-test-system [sys "classpath:fdb/application-test.yml"]
                    (let [store (system/instance sys [:fdb :store])]
                      (test-record-layer sys store)
                      (test-query-records sys store)
                      (test-record-layer-consumer sys store))))

(deftest meta-store-test
  (with-test-system [sys "classpath:fdb/application-test.yml"]
                    (let [store (system/instance sys [:fdb :meta-store])]
                      (test-record-layer sys store)
                      (test-query-records sys store)
                      (test-record-layer-consumer sys store))))
