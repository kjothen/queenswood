(ns ^:eftest/synchronized com.repldriven.mono.processor.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    com.repldriven.mono.migrator.interface

    [com.repldriven.mono.processor.interface :as SUT]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(defn- serialize-payload
  [schemas command-name data]
  (avro/serialize (get schemas command-name) data))

(defn- send-command
  [processor schemas command-name data]
  (let [payload (serialize-payload schemas command-name data)]
    (if (error/anomaly? payload)
      payload
      (SUT/process processor {"command" command-name "payload" payload}))))

(deftest process-open-account-test
  (testing "Processing open-account command should create account"
    (with-test-system
     [sys "classpath:processor/application-test.yml"]
     (let [processor (system/instance sys [:processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [result (send-command processor
                                        schemas
                                        "open-account"
                                        {"account_id" "acc-1"
                                         "name" "Test Account"
                                         "currency" "USD"})
                   _ (is (= "acc-1" (get result "record_id")))])))))

(deftest process-close-account-test
  (testing "Processing close-account command should close account"
    (with-test-system
     [sys "classpath:processor/application-test.yml"]
     (let [processor (system/instance sys [:processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [_ (send-command processor
                                   schemas
                                   "open-account"
                                   {"account_id" "acc-2"
                                    "name" "Account to Close"
                                    "currency" "USD"})
                   result (send-command processor
                                        schemas
                                        "close-account"
                                        {"account_id" "acc-2"})
                   _ (is (= "acc-2" (get result "record_id")))])))))

(deftest process-reopen-account-test
  (testing "Processing reopen-account command should reopen account"
    (with-test-system
     [sys "classpath:processor/application-test.yml"]
     (let [processor (system/instance sys [:processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [_ (send-command processor
                                   schemas
                                   "open-account"
                                   {"account_id" "acc-3"
                                    "name" "Account to Reopen"
                                    "currency" "USD"})
                   _ (send-command processor
                                   schemas
                                   "close-account"
                                   {"account_id" "acc-3"})
                   result (send-command processor
                                        schemas
                                        "reopen-account"
                                        {"account_id" "acc-3"})
                   _ (is (= "acc-3" (get result "record_id")))])))))

(deftest process-suspend-account-test
  (testing "Processing suspend-account command should suspend account"
    (with-test-system
     [sys "classpath:processor/application-test.yml"]
     (let [processor (system/instance sys [:processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [_ (send-command processor
                                   schemas
                                   "open-account"
                                   {"account_id" "acc-4"
                                    "name" "Account to Suspend"
                                    "currency" "EUR"})
                   result (send-command processor
                                        schemas
                                        "suspend-account"
                                        {"account_id" "acc-4"})
                   _ (is (= "acc-4" (get result "record_id")))])))))

(deftest process-unsuspend-account-test
  (testing "Processing unsuspend-account command should unsuspend"
    (with-test-system
     [sys "classpath:processor/application-test.yml"]
     (let [processor (system/instance sys [:processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [_ (send-command processor
                                   schemas
                                   "open-account"
                                   {"account_id" "acc-5"
                                    "name" "Account to Unsuspend"
                                    "currency" "GBP"})
                   _ (send-command processor
                                   schemas
                                   "suspend-account"
                                   {"account_id" "acc-5"})
                   result (send-command processor
                                        schemas
                                        "unsuspend-account"
                                        {"account_id" "acc-5"})
                   _ (is (= "acc-5" (get result "record_id")))])))))

(deftest process-archive-account-test
  (testing "Processing archive-account command should archive account"
    (with-test-system
     [sys "classpath:processor/application-test.yml"]
     (let [processor (system/instance sys [:processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [_ (send-command processor
                                   schemas
                                   "open-account"
                                   {"account_id" "acc-6"
                                    "name" "Account to Archive"
                                    "currency" "CAD"})
                   result (send-command processor
                                        schemas
                                        "archive-account"
                                        {"account_id" "acc-6"})
                   _ (is (= "acc-6" (get result "record_id")))])))))

(deftest process-get-account-status-test
  (testing "Processing get-account-status should return record_id"
    (with-test-system
     [sys "classpath:processor/application-test.yml"]
     (let [processor (system/instance sys [:processor])
           schemas (system/instance sys [:avro :serde])]
       (nom-test> [_ (send-command processor
                                   schemas
                                   "open-account"
                                   {"account_id" "acc-7"
                                    "name" "Status Account"
                                    "currency" "USD"})
                   result (send-command processor
                                        schemas
                                        "get-account-status"
                                        {"account_id" "acc-7"})
                   _ (is (= "acc-7" (get result "record_id")))])))))

(deftest process-unknown-command-test
  (testing "Processing unknown command should return anomaly"
    (with-test-system
     [sys "classpath:processor/application-test.yml"]
     (let [processor (system/instance sys [:processor])
           result (SUT/process processor
                               {"command" "invalid-command" "payload" nil})]
       (is (error/anomaly? result))
       (is (= :accounts/process-command (error/kind result)))))))
