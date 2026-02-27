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

(deftest process-open-account-test
  (testing "open-account creates account"
    (with-test-system
     [sys "classpath:accounts/application-test.yml"]
     (let [proc (system/instance sys [:accounts :processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [result (send-command proc
                                        schemas
                                        "open-account"
                                        {"account_id" "acc-1"
                                         "name" "Test Account"
                                         "currency" "USD"})
                   _ (is (= "acc-1" (get result "record_id")))])))))

(deftest process-close-account-test
  (testing "close-account closes account"
    (with-test-system
     [sys "classpath:accounts/application-test.yml"]
     (let [proc (system/instance sys [:accounts :processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [_ (send-command proc
                                   schemas
                                   "open-account"
                                   {"account_id" "acc-2"
                                    "name" "Account to Close"
                                    "currency" "USD"})
                   result (send-command proc
                                        schemas
                                        "close-account"
                                        {"account_id" "acc-2"})
                   _ (is (= "acc-2" (get result "record_id")))])))))

(deftest process-reopen-account-test
  (testing "reopen-account reopens closed account"
    (with-test-system
     [sys "classpath:accounts/application-test.yml"]
     (let [proc (system/instance sys [:accounts :processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [_ (send-command proc
                                   schemas
                                   "open-account"
                                   {"account_id" "acc-3"
                                    "name" "Account to Reopen"
                                    "currency" "USD"})
                   _ (send-command proc
                                   schemas
                                   "close-account"
                                   {"account_id" "acc-3"})
                   result (send-command proc
                                        schemas
                                        "reopen-account"
                                        {"account_id" "acc-3"})
                   _ (is (= "acc-3" (get result "record_id")))])))))

(deftest process-suspend-account-test
  (testing "suspend-account suspends account"
    (with-test-system
     [sys "classpath:accounts/application-test.yml"]
     (let [proc (system/instance sys [:accounts :processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [_ (send-command proc
                                   schemas
                                   "open-account"
                                   {"account_id" "acc-4"
                                    "name" "Account to Suspend"
                                    "currency" "EUR"})
                   result (send-command proc
                                        schemas
                                        "suspend-account"
                                        {"account_id" "acc-4"})
                   _ (is (= "acc-4" (get result "record_id")))])))))

(deftest process-unsuspend-account-test
  (testing "unsuspend-account unsuspends account"
    (with-test-system
     [sys "classpath:accounts/application-test.yml"]
     (let [proc (system/instance sys [:accounts :processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [_ (send-command proc
                                   schemas
                                   "open-account"
                                   {"account_id" "acc-5"
                                    "name" "Account to Unsuspend"
                                    "currency" "GBP"})
                   _ (send-command proc
                                   schemas
                                   "suspend-account"
                                   {"account_id" "acc-5"})
                   result (send-command proc
                                        schemas
                                        "unsuspend-account"
                                        {"account_id" "acc-5"})
                   _ (is (= "acc-5" (get result "record_id")))])))))

(deftest process-archive-account-test
  (testing "archive-account archives account"
    (with-test-system
     [sys "classpath:accounts/application-test.yml"]
     (let [proc (system/instance sys [:accounts :processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [_ (send-command proc
                                   schemas
                                   "open-account"
                                   {"account_id" "acc-6"
                                    "name" "Account to Archive"
                                    "currency" "CAD"})
                   result (send-command proc
                                        schemas
                                        "archive-account"
                                        {"account_id" "acc-6"})
                   _ (is (= "acc-6" (get result "record_id")))])))))

(deftest process-get-account-status-test
  (testing "get-account-status returns record_id"
    (with-test-system
     [sys "classpath:accounts/application-test.yml"]
     (let [proc (system/instance sys [:accounts :processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [_ (send-command proc
                                   schemas
                                   "open-account"
                                   {"account_id" "acc-7"
                                    "name" "Status Account"
                                    "currency" "USD"})
                   result (send-command proc
                                        schemas
                                        "get-account-status"
                                        {"account_id" "acc-7"})
                   _ (is (= "acc-7" (get result "record_id")))])))))

(deftest process-unknown-command-test
  (testing "unknown command returns anomaly"
    (with-test-system
     [sys "classpath:accounts/application-test.yml"]
     (let [proc (system/instance sys [:accounts :processor])
           result
           (processor/process proc {"command" "invalid-command" "payload" nil})]
       (is (error/anomaly? result))
       (is (= :accounts/process-command (error/kind result)))))))
