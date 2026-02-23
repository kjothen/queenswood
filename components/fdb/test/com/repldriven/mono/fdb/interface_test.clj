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

(deftest str-kv-test
  (testing "can store and retrieve string values as raw KV"
    (with-test-system [sys "classpath:fdb/application-test.yml"]
                      (let [db (system/instance sys [:fdb :db])]
                        (nom-test> [_ (SUT/set-str db "test-key" "test-value")
                                    result (SUT/get-str db "test-key")
                                    _ (is (= "test-value" result))])))))

(deftest proto-kv-test
  (testing "can store and retrieve Person and AddressBook records as raw KV"
    (let [alice {:name "Alice"
                 :id 1
                 :email "alice@example.com"
                 :phones [{:number "555-0100" :type :mobile}]}
          bob {:name "Bob" :id 2 :email "bob@example.com" :phones []}
          book {:people [alice bob]}]
      (with-test-system
       [sys "classpath:fdb/application-test.yml"]
       (let [db (system/instance sys [:fdb :db])]
         (nom-test> [_ (SUT/set-bytes db "person/1" (schema/Person->pb alice))
                     _ (SUT/set-bytes db "person/2" (schema/Person->pb bob))
                     _ (SUT/set-bytes db
                                      "addressbook/main"
                                      (schema/AddressBook->pb book))
                     retrieved-alice (error/nom-> (SUT/get-bytes db "person/1")
                                                  schema/pb->Person)
                     _ (is (= alice (utility/record->map retrieved-alice)))
                     retrieved-book (error/nom->
                                     (SUT/get-bytes db "addressbook/main")
                                     schema/pb->AddressBook)
                     _ (is (= book (utility/record->map retrieved-book)))]))))))

(deftest record-layer-test
  (testing "can save and load Person records via FDB Record Layer"
    (let [alice {:name "Alice" :id 1 :email "alice@example.com" :phones []}]
      (with-test-system
       [sys "classpath:fdb/application-test.yml"]
       (let [record-db (system/instance sys [:fdb :record-db])]
         (nom-test> [_ (SUT/save-record record-db
                                        "persons"
                                        (schema/Person->java alice))
                     retrieved (error/nom->
                                (SUT/load-record record-db "persons" 1)
                                schema/pb->Person)
                     _ (is (= alice (utility/record->map retrieved)))]))))))
