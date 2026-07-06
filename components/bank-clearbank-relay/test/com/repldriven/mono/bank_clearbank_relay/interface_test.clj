(ns ^:eftest/synchronized
    com.repldriven.mono.bank-clearbank-relay.interface-test
  (:require
    [com.repldriven.mono.bank-clearbank-relay.interface :as SUT]
    [com.repldriven.mono.bank-clearbank-relay.relay :as relay]

    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.testcontainers.interface]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [com.repldriven.mono.utility.interface :as utility]

    [clojure.test :refer [deftest is testing]]))

(defn- event
  [outbox-id dedup-key]
  {:outbox-id outbox-id
   :dedup-key dedup-key
   :event-name "transaction-settled"
   :payload (.getBytes "avro-payload-bytes")
   :correlation-id "corr-1"
   :causation-id "caus-1"
   :created-at (utility/now)})

(deftest outbox-dedup-and-relay-test
  (with-test-system
   [sys "classpath:bank-clearbank-relay/application-test.yml"]
   (let [config {:record-db (system/instance sys [:fdb :record-db])
                 :record-store (system/instance sys [:fdb :store])}
         bus (system/instance sys [:message-bus :bus])]
     (testing "a duplicate dedup-key is rejected by the unique index"
       (nom-test> [_ (SUT/save-event config (event "obx.1" "e2e-1:settled"))])
       (let [dup (SUT/save-event config (event "obx.2" "e2e-1:settled"))]
         (is (SUT/uniqueness-violation? dup)
             "a second save reusing the dedup-key must violate")))
     (testing "the relay publishes a stored event to the bus"
       (let [received (promise)
             handler (relay/->handler {:bus bus
                                       :event-channel :schemes-payments-event})]
         (message-bus/subscribe bus
                                :schemes-payments-event
                                (fn [e] (deliver received e)))
         (nom-test> [_ (SUT/save-event config (event "obx.3" "e2e-2:settled"))])
         (fdb/process-changelog (:record-db config)
                                "test-relay"
                                "clearbank-outbox"
                                handler)
         (let [e (deref received 5000 ::timeout)]
           (is (not= ::timeout e) "relay must publish the stored event")
           (when (not= ::timeout e)
             (is (= "transaction-settled" (:event e)))
             (is (= "corr-1" (:correlation-id e))))))))))
