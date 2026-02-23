(ns ^:eftest/synchronized com.repldriven.mono.fdb.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.fdb.interface :as SUT]

    [com.repldriven.mono.schema.interface :as schema]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

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
       (nom-test> [_ (SUT/set db "test-key" "test-value") result
                   (SUT/get db "test-key") _
                   (is (= "test-value" result)
                       "Should be able to write and read values from FDB")])))))

(deftest record-layer-test
  (testing "can store and retrieve Person and AddressBook records"
    (with-test-system
     [sys "classpath:fdb/application-test.yml"]
     (let [db (system/instance sys [:fdb :db])]
       (nom-test>
        [_
         (SUT/set-bytes db
                        "person/1"
                        (schema/Person->pb {:name "Alice"
                                            :id 1
                                            :email "alice@example.com"
                                            :phones [{:number "555-0100"
                                                      :type :mobile}]})) _
         (SUT/set-bytes db
                        "person/2"
                        (schema/Person->pb
                         {:name "Bob" :id 2 :email "bob@example.com"})) _
         (SUT/set-bytes db
                        "addressbook/main"
                        (schema/AddressBook->pb
                         {:people
                          [{:name "Alice"
                            :id 1
                            :email "alice@example.com"
                            :phones [{:number "555-0100" :type :mobile}]}
                           {:name "Bob" :id 2 :email "bob@example.com"}]}))
         alice-bytes (SUT/get-bytes db "person/1") retrieved-alice
         (schema/pb->Person alice-bytes) _
         (is (= "Alice" (:name retrieved-alice))) _
         (is (= 1 (:id retrieved-alice))) _
         (is (= "alice@example.com" (:email retrieved-alice))) _
         (is (= 1 (count (:phones retrieved-alice)))) book-bytes
         (SUT/get-bytes db "addressbook/main") retrieved-book
         (schema/pb->AddressBook book-bytes) _
         (is (= 2 (count (:people retrieved-book))))])))))
