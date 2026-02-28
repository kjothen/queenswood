(ns ^:eftest/synchronized com.repldriven.mono.accounts.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    com.repldriven.mono.migrator.interface
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
      (processor/process proc {"command" command-name "payload" payload}))))

(defn- decode-payload
  [schemas schema-name result]
  (avro/deserialize-same (get schemas schema-name) (:payload result)))

(deftest process-open-account-test
  (testing "open-account creates account"
    (with-test-system
     [sys "classpath:accounts/application-test.yml"]
     (let [proc (system/instance sys [:accounts :processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [result (send-command proc
                                        schemas
                                        "open-account"
                                        {"customer_id" "cust-1"
                                         "name" "Test Account"
                                         "currency" "USD"})
                   _ (is (= "ACCEPTED" (:status result)))
                   decoded (decode-payload schemas "account" result)
                   _ (is (some? (get decoded "account_id")))])))))

(deftest process-close-account-test
  (testing "close-account closes account"
    (with-test-system
     [sys "classpath:accounts/application-test.yml"]
     (let [proc (system/instance sys [:accounts :processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [opened (send-command proc
                                        schemas
                                        "open-account"
                                        {"customer_id" "cust-2"
                                         "name" "Account to Close"
                                         "currency" "USD"})
                   account (decode-payload schemas "account" opened)
                   result (send-command proc
                                        schemas
                                        "close-account"
                                        {"account_id" (get account
                                                           "account_id")})
                   _ (is (= "ACCEPTED" (:status result)))
                   decoded (decode-payload schemas "account" result)
                   _ (is (= (get account "account_id")
                            (get decoded "account_id")))])))))

(deftest process-reopen-account-test
  (testing "reopen-account reopens closed account"
    (with-test-system
     [sys "classpath:accounts/application-test.yml"]
     (let [proc (system/instance sys [:accounts :processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [opened (send-command proc
                                        schemas
                                        "open-account"
                                        {"customer_id" "cust-3"
                                         "name" "Account to Reopen"
                                         "currency" "USD"})
                   account (decode-payload schemas "account" opened)
                   _ (send-command proc
                                   schemas
                                   "close-account"
                                   {"account_id" (get account "account_id")})
                   result (send-command proc
                                        schemas
                                        "reopen-account"
                                        {"account_id" (get account
                                                           "account_id")})
                   _ (is (= "ACCEPTED" (:status result)))
                   decoded (decode-payload schemas "account" result)
                   _ (is (= (get account "account_id")
                            (get decoded "account_id")))])))))

(deftest process-suspend-account-test
  (testing "suspend-account suspends account"
    (with-test-system
     [sys "classpath:accounts/application-test.yml"]
     (let [proc (system/instance sys [:accounts :processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [opened (send-command proc
                                        schemas
                                        "open-account"
                                        {"customer_id" "cust-4"
                                         "name" "Account to Suspend"
                                         "currency" "EUR"})
                   account (decode-payload schemas "account" opened)
                   result (send-command proc
                                        schemas
                                        "suspend-account"
                                        {"account_id" (get account
                                                           "account_id")})
                   _ (is (= "ACCEPTED" (:status result)))
                   decoded (decode-payload schemas "account" result)
                   _ (is (= (get account "account_id")
                            (get decoded "account_id")))])))))

(deftest process-unsuspend-account-test
  (testing "unsuspend-account unsuspends account"
    (with-test-system
     [sys "classpath:accounts/application-test.yml"]
     (let [proc (system/instance sys [:accounts :processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [opened (send-command proc
                                        schemas
                                        "open-account"
                                        {"customer_id" "cust-5"
                                         "name" "Account to Unsuspend"
                                         "currency" "GBP"})
                   account (decode-payload schemas "account" opened)
                   _ (send-command proc
                                   schemas
                                   "suspend-account"
                                   {"account_id" (get account "account_id")})
                   result (send-command proc
                                        schemas
                                        "unsuspend-account"
                                        {"account_id" (get account
                                                           "account_id")})
                   _ (is (= "ACCEPTED" (:status result)))
                   decoded (decode-payload schemas "account" result)
                   _ (is (= (get account "account_id")
                            (get decoded "account_id")))])))))

(deftest process-archive-account-test
  (testing "archive-account archives account"
    (with-test-system
     [sys "classpath:accounts/application-test.yml"]
     (let [proc (system/instance sys [:accounts :processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [opened (send-command proc
                                        schemas
                                        "open-account"
                                        {"customer_id" "cust-6"
                                         "name" "Account to Archive"
                                         "currency" "CAD"})
                   account (decode-payload schemas "account" opened)
                   result (send-command proc
                                        schemas
                                        "archive-account"
                                        {"account_id" (get account
                                                           "account_id")})
                   _ (is (= "ACCEPTED" (:status result)))
                   decoded (decode-payload schemas "account" result)
                   _ (is (= (get account "account_id")
                            (get decoded "account_id")))])))))

(deftest process-get-account-status-test
  (testing "get-account-status returns account status"
    (with-test-system
     [sys "classpath:accounts/application-test.yml"]
     (let [proc (system/instance sys [:accounts :processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [opened (send-command proc
                                        schemas
                                        "open-account"
                                        {"customer_id" "cust-7"
                                         "name" "Status Account"
                                         "currency" "USD"})
                   account (decode-payload schemas "account" opened)
                   result (send-command proc
                                        schemas
                                        "get-account-status"
                                        {"account_id" (get account
                                                           "account_id")})
                   _ (is (= "ACCEPTED" (:status result)))
                   decoded (decode-payload schemas "account-status" result)
                   _ (is (= (get account "account_id")
                            (get decoded "account_id")))
                   _ (is (= "open" (get decoded "account_status")))])))))

(deftest process-unknown-command-test
  (testing "unknown command returns rejection"
    (with-test-system
     [sys "classpath:accounts/application-test.yml"]
     (let [proc (system/instance sys [:accounts :processor])
           schemas (system/instance sys [:avro :serde])
           result
           (send-command proc schemas "unknown-command" {"account_id" "acc-8"})]
       (is (= "REJECTED" (:status result)))))))
