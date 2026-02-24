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

(defn- test-relay-batch
  [sys store]
  (let [alice {:name "Alice" :id 1 :email "alice@example.com" :phones []}
        record-db (system/instance sys [:fdb :record-db])
        received (atom [])]
    (testing "relay-batch delivers outbox entries to handler-fn"
      (nom-test> [_ (SUT/outbox-record record-db
                                       store
                                       "persons"
                                       (schema/Person->java alice)
                                       (schema/Person->pb alice))
                  _ (SUT/relay-batch record-db
                                     "persons"
                                     (fn [_k v] (swap! received conj v)))
                  _ (is (= 1 (count @received)))
                  retrieved (error/nom-> (first @received) schema/pb->Person)
                  _ (is (= alice (utility/record->map retrieved)))]))))

(deftest interface-test
  (with-test-system [sys "classpath:fdb/application-test.yml"]
                    (let [store (system/instance sys [:fdb :store])]
                      (test-str-kv sys)
                      (test-proto-kv sys)
                      (test-record-layer sys store)
                      (test-relay-batch sys store))))
