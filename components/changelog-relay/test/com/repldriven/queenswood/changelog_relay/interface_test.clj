(ns ^:eftest/synchronized
    com.repldriven.queenswood.changelog-relay.interface-test
  (:require
    [com.repldriven.queenswood.testcontainers.interface]

    [com.repldriven.queenswood.changelog-relay.interface :as SUT]

    [com.repldriven.queenswood.fdb.interface :as fdb]

    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer [with-test-system]]
    [com.repldriven.mono.utility.interface :as utility]

    [clojure.test :refer [deftest is testing]]))

(def ^:private store-name "clearbank-outbox")

(defn- write-entry
  [config record-id payload]
  (fdb/transact config
                (fn [txn]
                  (fdb/write-changelog txn
                                       store-name
                                       record-id
                                       (.getBytes ^String payload)))))

(defn- wait-for
  [pred deadline-ms]
  (let [deadline (+ (utility/now) deadline-ms)]
    (loop []
      (cond
       (pred)
       true

       (>= (utility/now) deadline)
       false

       :else
       (do (Thread/sleep 25) (recur))))))

(deftest relays-every-entry-test
  (with-test-system
   [sys "classpath:changelog-relay/application-test.yml"]
   (let [config {:record-db (system/instance sys [:fdb :record-db])
                 :record-store (system/instance sys [:fdb :store])}
         ;; The runner has to be given the same prefix the writer used —
         ;; it only gets a record-db, so it cannot discover one. A
         ;; mismatch reads an empty changelog rather than erroring.
         prefix (system/instance sys [:fdb :keyspace-prefix])
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
                                        :handler handler
                                        :keyspace-prefix prefix})]
         (try (is (wait-for #(= 2 (count (mine))) 5000)
                  "both changelog entries must be relayed")
              (is (= ["relay-test:opening" "relay-test:opened"] (mine))
                  "entries must arrive in commit order")
              (finally (stop))))))))

(deftest stops-cleanly-test
  (with-test-system [sys "classpath:changelog-relay/application-test.yml"]
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
