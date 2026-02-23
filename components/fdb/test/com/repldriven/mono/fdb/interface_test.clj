(ns ^:eftest/synchronized com.repldriven.mono.fdb.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.fdb.interface :as SUT]

    [com.repldriven.mono.schema.interface :as schema]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [com.repldriven.mono.utility.interface :as utility]

    [clojure.test :refer [deftest is testing]]))

;; NOTE: The FDB Java client requires libfdb_c to be installed on the host —
;; the testcontainer runs FDB but the JNI bridge still needs the native library
;; locally. On macOS the Nix flake provides it; without Nix install via
;; `brew install foundationdb`. On Linux install foundationdb-clients from the
;; FDB GitHub releases.

(deftest integration-test
  (testing "FDB container starts and can execute transactions"
    (with-test-system
     [sys "classpath:fdb/application-test.yml"]
     (let [db (system/instance sys [:fdb :db])]
       (nom-test> [_ (SUT/set db "test-key" "test-value")
                   result (SUT/get db "test-key")
                   _ (is (= "test-value" result)
                         "Should be able to write and read values from FDB")])))))

(deftest proto-kv-test
  (testing "can store and retrieve Person and AddressBook records as raw KV"
    (let [alice {:name "Alice"
                 :id 1
                 :email "alice@example.com"
                 :phones [{:number "555-0100" :type :mobile}]}
          bob {:name "Bob" :id 2 :email "bob@example.com"}]
      (with-test-system
       [sys "classpath:fdb/application-test.yml"]
       (let [db (system/instance sys [:fdb :db])]
         (nom-test> [_ (SUT/set-bytes db "person/1" (schema/Person->pb alice))
                     _ (SUT/set-bytes db "person/2" (schema/Person->pb bob))
                     _ (SUT/set-bytes db
                                      "addressbook/main"
                                      (schema/AddressBook->pb {:people [alice
                                                                        bob]}))
                     alice-bytes (SUT/get-bytes db "person/1")
                     retrieved-alice (schema/pb->Person alice-bytes)
                     _ (is (= alice (utility/record->map retrieved-alice)))
                     book-bytes (SUT/get-bytes db "addressbook/main")
                     retrieved-book (schema/pb->AddressBook book-bytes)
                     _ (is (= 2 (count (:people retrieved-book))))]))))))

(deftest record-layer-test
  (testing "can save and load Person records via FDB Record Layer"
    (let [alice {:name "Alice" :id 1 :email "alice@example.com" :phones []}]
      (with-test-system
       [sys "classpath:fdb/application-test.yml"]
       (let [record-db (system/instance sys [:fdb :record-db])]
         (nom-test> [_ (SUT/save-record! record-db
                                         "persons"
                                         (schema/Person->java alice)
                                         (byte-array 0))
                     loaded (SUT/load-record record-db "persons" 1)
                     retrieved (schema/pb->Person (.toByteArray loaded))
                     _ (is (= alice (utility/record->map retrieved)))]))))))
