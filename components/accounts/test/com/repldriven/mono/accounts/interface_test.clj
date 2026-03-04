(ns ^:eftest/synchronized com.repldriven.mono.accounts.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    com.repldriven.mono.fdb.interface

    [com.repldriven.mono.accounts.interface :as SUT]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(defn- send-command
  [proc schemas command-name data]
  (let [payload (avro/serialize (get schemas command-name) data)]
    (if (error/anomaly? payload)
      payload
      (processor/process proc {:command command-name :payload payload}))))

(defn- decode-payload
  [schemas schema-name result]
  (avro/deserialize-same (get schemas schema-name) (:payload result)))

(defn- poll-status
  "Polls get-account until status matches expected, or
  times out after 5 s."
  [proc schemas account-id expected]
  (loop [attempts 50]
    (let [result
          (send-command proc schemas "get-account" {:account-id account-id})
          decoded (when (= "ACCEPTED" (:status result))
                    (decode-payload schemas "account" result))]
      (cond
       (= expected (:status decoded))
       decoded
       (pos? attempts)
       (do (Thread/sleep 100) (recur (dec attempts)))
       :else
       decoded))))

(defn- test-open-account
  [proc schemas]
  (testing "open-account creates account with opening status"
    (let [open-payload
          {:customer-id "cust-1" :name "Test Account" :currency "USD"}]
      (nom-test> [result (send-command proc schemas "open-account" open-payload)
                  _ (is (= "ACCEPTED" (:status result)))
                  decoded (decode-payload schemas "account" result)
                  _ (is (some? (:account-id decoded)))
                  _ (is (= open-payload
                           (select-keys decoded (keys open-payload))))]))))

(defn- test-close-account
  [proc schemas]
  (testing "close-account sets status to closing"
    (let [open-payload
          {:customer-id "cust-2" :name "Account to Close" :currency "USD"}]
      (nom-test> [opened (send-command proc schemas "open-account" open-payload)
                  account (decode-payload schemas "account" opened)
                  account-id (select-keys account [:account-id])
                  result (send-command proc schemas "close-account" account-id)
                  _ (is (= "ACCEPTED" (:status result)))
                  decoded (decode-payload schemas "account" result)
                  _ (is (= open-payload
                           (select-keys decoded (keys open-payload))))]))))

(defn- test-watcher-transitions
  [proc schemas]
  (testing "watcher transitions opening->opened and closing->closed"
    (let [open-payload
          {:customer-id "cust-watcher" :name "Watcher Account" :currency "GBP"}]
      (nom-test> [opened (send-command proc schemas "open-account" open-payload)
                  account (decode-payload schemas "account" opened)
                  account-id (:account-id account)
                  polled-opened (poll-status proc schemas account-id "opened")
                  _ (is (= "opened" (:status polled-opened)))
                  _ (send-command proc
                                  schemas
                                  "close-account"
                                  {:account-id account-id})
                  polled-closed (poll-status proc schemas account-id "closed")
                  _ (is (= "closed" (:status polled-closed)))]))))

(defn- test-get-account
  [proc schemas]
  (testing "get-account returns account"
    (let [open-payload
          {:customer-id "cust-7" :name "Status Account" :currency "USD"}]
      (nom-test> [opened (send-command proc schemas "open-account" open-payload)
                  account (decode-payload schemas "account" opened)
                  account-id (select-keys account [:account-id])
                  result (send-command proc schemas "get-account" account-id)
                  _ (is (= "ACCEPTED" (:status result)))
                  decoded (decode-payload schemas "account" result)
                  _ (is (= (:account-id account) (:account-id decoded)))
                  _ (is (= "opening" (:status decoded)))]))))

(defn- test-close-missing-account
  [proc schemas]
  (testing "close-account rejects missing account"
    (let [result (send-command proc
                               schemas
                               "close-account"
                               {:account-id "missing-id"})]
      (is (error/rejection? result))
      (is (= :account/not-found (error/kind result))))))

(defn- test-open-multiple-accounts
  [proc schemas]
  (testing "open-account allows multiple accounts per customer"
    (let [payload {:customer-id "cust-multi" :currency "USD"}]
      (nom-test> [r1 (send-command proc
                                   schemas
                                   "open-account"
                                   (assoc payload :name "Account A"))
                  _ (is (= "ACCEPTED" (:status r1)))
                  r2 (send-command proc
                                   schemas
                                   "open-account"
                                   (assoc payload :name "Account B"))
                  _ (is (= "ACCEPTED" (:status r2)))
                  a1 (decode-payload schemas "account" r1)
                  a2 (decode-payload schemas "account" r2)
                  _ (is (not= (:account-id a1) (:account-id a2)))]))))

(defn- test-unknown-command
  [proc schemas]
  (testing "unknown command returns rejection"
    (let [result
          (send-command proc schemas "unknown-command" {:account-id "acc-8"})]
      (is (error/rejection? result))
      (is (= :accounts/unknown-command (error/kind result))))))

(deftest process-accounts-test
  (with-test-system [sys
                     ["classpath:accounts/application-test.yml"
                      #(assoc-in %
                        [:system/defs :accounts :watcher-handler]
                        #'SUT/handle-changelog-change)]]
                    (let [proc (system/instance sys [:accounts :processor])
                          schemas (system/instance sys [:avro :serde])]
                      (test-open-account proc schemas)
                      (test-close-account proc schemas)
                      (test-watcher-transitions proc schemas)
                      (test-get-account proc schemas)
                      (test-close-missing-account proc schemas)
                      (test-open-multiple-accounts proc schemas)
                      (test-unknown-command proc schemas))))
