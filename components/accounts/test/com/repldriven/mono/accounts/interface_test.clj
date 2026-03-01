(ns ^:eftest/synchronized com.repldriven.mono.accounts.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    com.repldriven.mono.accounts.interface

    [com.repldriven.mono.processor.interface :as processor]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]
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

(defn- test-open-account
  [proc schemas]
  (testing "open-account creates account"
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
  (testing "close-account closes account"
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

(defn- test-reopen-account
  [proc schemas]
  (testing "reopen-account reopens closed account"
    (let [open-payload
          {:customer-id "cust-3" :name "Account to Reopen" :currency "USD"}]
      (nom-test> [opened (send-command proc schemas "open-account" open-payload)
                  account (decode-payload schemas "account" opened)
                  account-id (select-keys account [:account-id])
                  _ (send-command proc schemas "close-account" account-id)
                  result (send-command proc schemas "reopen-account" account-id)
                  _ (is (= "ACCEPTED" (:status result)))
                  decoded (decode-payload schemas "account" result)
                  _ (is (= open-payload
                           (select-keys decoded (keys open-payload))))]))))

(defn- test-suspend-account
  [proc schemas]
  (testing "suspend-account suspends account"
    (let [open-payload
          {:customer-id "cust-4" :name "Account to Suspend" :currency "EUR"}]
      (nom-test> [opened (send-command proc schemas "open-account" open-payload)
                  account (decode-payload schemas "account" opened)
                  account-id (select-keys account [:account-id])
                  result
                  (send-command proc schemas "suspend-account" account-id)
                  _ (is (= "ACCEPTED" (:status result)))
                  decoded (decode-payload schemas "account" result)
                  _ (is (= open-payload
                           (select-keys decoded (keys open-payload))))]))))

(defn- test-unsuspend-account
  [proc schemas]
  (testing "unsuspend-account unsuspends account"
    (let [open-payload
          {:customer-id "cust-5" :name "Account to Unsuspend" :currency "GBP"}]
      (nom-test> [opened (send-command proc schemas "open-account" open-payload)
                  account (decode-payload schemas "account" opened)
                  account-id (select-keys account [:account-id])
                  _ (send-command proc schemas "suspend-account" account-id)
                  result
                  (send-command proc schemas "unsuspend-account" account-id)
                  _ (is (= "ACCEPTED" (:status result)))
                  decoded (decode-payload schemas "account" result)
                  _ (is (= open-payload
                           (select-keys decoded (keys open-payload))))]))))

(defn- test-archive-account
  [proc schemas]
  (testing "archive-account archives account"
    (let [open-payload
          {:customer-id "cust-6" :name "Account to Archive" :currency "CAD"}]
      (nom-test> [opened (send-command proc schemas "open-account" open-payload)
                  account (decode-payload schemas "account" opened)
                  account-id (select-keys account [:account-id])
                  result
                  (send-command proc schemas "archive-account" account-id)
                  _ (is (= "ACCEPTED" (:status result)))
                  decoded (decode-payload schemas "account" result)
                  _ (is (= open-payload
                           (select-keys decoded (keys open-payload))))]))))

(defn- test-get-account-status
  [proc schemas]
  (testing "get-account-status returns account status"
    (let [open-payload
          {:customer-id "cust-7" :name "Status Account" :currency "USD"}]
      (nom-test> [opened (send-command proc schemas "open-account" open-payload)
                  account (decode-payload schemas "account" opened)
                  account-id (select-keys account [:account-id])
                  result
                  (send-command proc schemas "get-account-status" account-id)
                  _ (is (= "ACCEPTED" (:status result)))
                  decoded (decode-payload schemas "account-status" result)
                  _ (is (= (assoc account-id :status "open") decoded))]))))

(defn- test-close-missing-account
  [proc schemas]
  (testing "close-account rejects missing account"
    (is (= "REJECTED"
           (:status (send-command proc
                                  schemas
                                  "close-account"
                                  {:account-id "missing-id"}))))))

(defn- test-open-existing-account
  [proc schemas]
  (testing "open-account rejects duplicate customer"
    (let [open-payload
          {:customer-id "cust-dup" :name "Duplicate Account" :currency "USD"}]
      (nom-test> [_ (send-command proc schemas "open-account" open-payload)
                  result (send-command proc schemas "open-account" open-payload)
                  _ (is (= "REJECTED" (:status result)))]))))

(defn- test-unknown-command
  [proc schemas]
  (testing "unknown command returns rejection"
    (is (= "REJECTED"
           (:status (send-command proc
                                  schemas
                                  "unknown-command"
                                  {:account-id "acc-8"}))))))

(deftest process-accounts-test
  (with-test-system [sys "classpath:accounts/application-test.yml"]
                    (let [proc (system/instance sys [:accounts :processor])
                          schemas (system/instance sys [:avro :serde])]
                      (test-open-account proc schemas)
                      (test-close-account proc schemas)
                      (test-reopen-account proc schemas)
                      (test-suspend-account proc schemas)
                      (test-unsuspend-account proc schemas)
                      (test-archive-account proc schemas)
                      (test-get-account-status proc schemas)
                      (test-close-missing-account proc schemas)
                      (test-open-existing-account proc schemas)
                      (test-unknown-command proc schemas))))
