(ns ^:eftest/synchronized
    com.repldriven.queenswood.changelog-relay.interface-test
  (:require
    [com.repldriven.queenswood.changelog-relay.consumer :as consumer]
    [com.repldriven.queenswood.changelog-relay.interface :as SUT]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.queenswood.fdb.interface :as fdb]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.queenswood.testcontainers.interface]
    [com.repldriven.mono.test-system.interface :refer [with-test-system]]

    [clojure.test :refer [deftest is testing]]))

(def ^:private store-name "clearbank-outbox")

(defn- write-entry
  [config record-id payload]
  (fdb/transact config
                (fn [txn]
                  (let [store (fdb/open txn store-name)]
                    (fdb/write-changelog store
                                         store-name
                                         record-id
                                         (.getBytes ^String payload))))))

(defn- wait-for
  [pred deadline-ms]
  (let [deadline (+ (System/currentTimeMillis) deadline-ms)]
    (loop []
      (cond
       (pred)
       true

       (>= (System/currentTimeMillis) deadline)
       false

       :else
       (do (Thread/sleep 25) (recur))))))

(deftest relays-every-entry-test
  (with-test-system
   [sys "classpath:bank-changelog-relay/application-test.yml"]
   (let [config {:record-db (system/instance sys [:fdb :record-db])
                 :record-store (system/instance sys [:fdb :store])}
         seen (atom [])
         handler (fn [_ctx changelog-bytes]
                   (swap! seen conj (String. ^bytes changelog-bytes)))]
     (testing
       "two transitions on one record-id both relay — the relay carries
       transitions, so collapsing to the latest per record-id would drop
       an event"
       ;; Both entries are committed before the runner starts, so its
       ;; first pass scans them as one batch — the case where
       ;; latest-per-record-id dedup would silently drop "opening".
       (write-entry config "rec.1" "relay-test:opening")
       (write-entry config "rec.1" "relay-test:opened")
       (let [mine #(filterv (fn [s] (.startsWith ^String s "relay-test:"))
                            @seen)
             {:keys [stop]} (SUT/start {:record-db (:record-db config)
                                        :consumer-id "changelog-relay-test"
                                        :store-name store-name
                                        :handler handler})]
         (try (is (wait-for #(= 2 (count (mine))) 5000)
                  "both changelog entries must be relayed")
              (is (= ["relay-test:opening" "relay-test:opened"] (mine))
                  "entries must arrive in commit order")
              (finally (stop))))))))

(deftest stops-cleanly-test
  (with-test-system [sys "classpath:bank-changelog-relay/application-test.yml"]
                    (let [record-db (system/instance sys [:fdb :record-db])
                          calls (atom 0)
                          {:keys [stop]}
                          (SUT/start {:record-db record-db
                                      :consumer-id "changelog-relay-stop-test"
                                      :store-name store-name
                                      :handler (fn [_ _] (swap! calls inc))})]
                      (stop)
                      (testing "a stopped runner makes no further passes"
                        (let [after-stop @calls]
                          (Thread/sleep 300)
                          (is (= after-stop @calls)
                              "the daemon thread must not run after stop"))))))

(defrecord StubProcessor [result]
  processor/Processor
    (process [_ _message] result))

(deftest failed-event-asks-for-redelivery-test
  (let [handler (consumer/->handler
                 {:event-channel :relay-test-event
                  :processor (->StubProcessor (error/fail :test/boom
                                                          "handler failed"))})]
    (testing
      "an anomaly from the processor throws, so the consumer loop
              negative-acks and the broker redelivers — returning it
              would ack and lose the event"
      (is (thrown? clojure.lang.ExceptionInfo (handler {:event "x"}))))))

(deftest successful-event-is-acked-test
  (let [handler (consumer/->handler {:event-channel :relay-test-event
                                     :processor (->StubProcessor :ok)})]
    (testing "a clean result returns normally so the offset commits"
      (is (= :ok (handler {:event "x"}))))))
