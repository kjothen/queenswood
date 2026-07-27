(ns ^:eftest/synchronized com.repldriven.queenswood.fdb.interface-test
  "Ported from mono when this component moved here. The record fixture
  is a queenswood `Bank` rather than mono's test-schema `Pet`, because
  `test-schema` carries a `:deps/prep-lib` and is deliberately not
  exported to consumers of mono-lib."
  (:require
    [com.repldriven.queenswood.fdb.interface :as SUT]

    [com.repldriven.queenswood.schema.interface :as schema]
    [com.repldriven.mono.error.interface :refer [nom->]]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.queenswood.testcontainers.interface]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [com.repldriven.mono.utility.interface :as utility]

    [clojure.test :refer [deftest is testing]]))

(def ^:private store-name "banks")

(defn- bank
  [bank-id sort-code]
  {:bank-id bank-id
   :status :bank-status-test
   :name (str "Bank " bank-id)
   :created-at 1700000000000
   :updated-at 1700000000000
   :sort-code sort-code})

(defn- test-str-kv
  [sys]
  (let [db (system/instance sys [:fdb :db])]
    (testing "can store and retrieve string values as raw KV"
      (nom-test> [_ (SUT/set-str db "test-key" "test-value")
                  result (SUT/get-str db "test-key")
                  _ (is (= "test-value" result))]))))

(defn- test-proto-kv
  [sys]
  (let [acme (bank "bnk.kv.1" "000001")
        db (system/instance sys [:fdb :db])]
    (testing "can store and retrieve records as raw KV"
      (nom-test> [_ (SUT/set-bytes db "bank/1" (schema/Bank->pb acme))
                  retrieved (nom-> (SUT/get-bytes db "bank/1") schema/pb->Bank)
                  _ (is (= acme
                           (select-keys (utility/record->map retrieved)
                                        (keys acme))))]))))

(defn- test-record-layer
  [sys store]
  (let [acme (bank "bnk.rl.1" "100001")
        config {:record-db (system/instance sys [:fdb :record-db])
                :record-store store}]
    (testing "can save and load records via FDB Record Layer"
      (nom-test> [_ (SUT/transact config
                                  (fn [txn]
                                    (SUT/save-record (SUT/open txn store-name)
                                                     (schema/Bank->java acme))))
                  retrieved
                  (nom-> (SUT/transact config
                                       (fn [txn]
                                         (SUT/load-record (SUT/open txn
                                                                    store-name)
                                                          "bnk.rl.1")))
                         schema/pb->Bank)
                  _ (is (= acme
                           (select-keys (utility/record->map retrieved)
                                        (keys acme))))]))))

(defn- test-record-layer-consumer
  [sys store]
  (let [acme (bank "bnk.cl.1" "200001")
        zenith (bank "bnk.cl.2" "200002")
        config {:record-db (system/instance sys [:fdb :record-db])
                :record-store store}
        record-db (system/instance sys [:fdb :record-db])
        received (atom [])]
    (testing
      "consumer reads changelog entries and calls handler with
       record bytes"
      (nom-test>
        [_ (SUT/transact config
                         (fn [txn]
                           (let [s (SUT/open txn store-name)]
                             (SUT/save-record s (schema/Bank->java acme))
                             (SUT/write-changelog s
                                                  store-name
                                                  (:bank-id acme)
                                                  (.getBytes "acme-data"))
                             (SUT/save-record s (schema/Bank->java zenith))
                             (SUT/write-changelog s
                                                  store-name
                                                  (:bank-id zenith)
                                                  (.getBytes "zenith-data")))))
         _ (SUT/process-changelog record-db
                                  "fdb-interface-test-consumer"
                                  store-name
                                  (fn [_ctx changelog-bytes]
                                    (swap! received conj changelog-bytes)))
         _ (is (= 2 (count @received)))
         _ (is (= "acme-data" (String. ^bytes (first @received))))
         _ (is (= "zenith-data" (String. ^bytes (second @received))))]))))

(defn- test-query-records
  [sys store]
  (let [acme (bank "bnk.q.1" "300001")
        zenith (bank "bnk.q.2" "300002")
        config {:record-db (system/instance sys [:fdb :record-db])
                :record-store store}]
    (testing "can query records by field value"
      (nom-test>
        [_ (SUT/transact config
                         (fn [txn]
                           (let [s (SUT/open txn store-name)]
                             (SUT/save-record s (schema/Bank->java acme))
                             (SUT/save-record s (schema/Bank->java zenith)))))
         results (SUT/transact config
                               (fn [txn]
                                 (SUT/query-records (SUT/open txn store-name)
                                                    "Bank"
                                                    "sort_code"
                                                    "300001")))
         _ (is (= 1 (count results)))
         retrieved (nom-> (first results) schema/pb->Bank)
         _ (is (= acme
                  (select-keys (utility/record->map retrieved)
                               (keys acme))))]))))

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
