(ns ^:eftest/synchronized com.repldriven.mono.processor.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    com.repldriven.mono.migrator.interface

    [com.repldriven.mono.processor.interface :as SUT]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [com.repldriven.mono.json.interface :as json]

    [clojure.test :refer [deftest is testing]]))

(deftest process-open-account-test
  (testing "Processing open-account command should create account in database"
    (with-test-system
     [sys "classpath:processor/application-test.yml"]
     (let [processor (system/instance sys [:processor])]
       (nom-test> [result
                   (SUT/process processor
                                {"command" "open-account"
                                 "data" (json/write-str {"account-id" "acc-1"
                                                         "name" "Test Account"
                                                         "currency" "USD"})}) _
                   (is (= :ok (:status result))) _
                   (is (= "acc-1" (:account-id result))) status
                   (SUT/process processor
                                {"command" "get-account-status"
                                 "data" (json/write-str {"account-id"
                                                         "acc-1"})}) _
                   (is (= :ok (:status status))) _
                   (is (= "open" (:account-status status)))])))))

(deftest process-close-account-test
  (testing
    "Processing close-account command should update account status in database"
    (with-test-system
     [sys "classpath:processor/application-test.yml"]
     (let [processor (system/instance sys [:processor])]
       (nom-test>
        [_
         (SUT/process processor
                      {"command" "open-account"
                       "data" (json/write-str {"account-id" "acc-2"
                                               "name" "Account to Close"
                                               "currency" "USD"})}) result
         (SUT/process processor
                      {"command" "close-account"
                       "data" (json/write-str {"account-id" "acc-2"})}) _
         (is (= :ok (:status result))) _ (is (= "acc-2" (:account-id result)))
         status
         (SUT/process processor
                      {"command" "get-account-status"
                       "data" (json/write-str {"account-id" "acc-2"})}) _
         (is (= :ok (:status status))) _
         (is (= "closed" (:account-status status)))])))))

(deftest process-reopen-account-test
  (testing
    "Processing reopen-account command should update account status to open"
    (with-test-system
     [sys "classpath:processor/application-test.yml"]
     (let [processor (system/instance sys [:processor])]
       (nom-test>
        [_
         (SUT/process processor
                      {"command" "open-account"
                       "data" (json/write-str {"account-id" "acc-3"
                                               "name" "Account to Reopen"
                                               "currency" "USD"})}) _
         (SUT/process processor
                      {"command" "close-account"
                       "data" (json/write-str {"account-id" "acc-3"})}) result
         (SUT/process processor
                      {"command" "reopen-account"
                       "data" (json/write-str {"account-id" "acc-3"})}) _
         (is (= :ok (:status result))) _ (is (= "acc-3" (:account-id result)))
         status
         (SUT/process processor
                      {"command" "get-account-status"
                       "data" (json/write-str {"account-id" "acc-3"})}) _
         (is (= :ok (:status status))) _
         (is (= "open" (:account-status status)))])))))

(deftest process-suspend-account-test
  (testing
    "Processing suspend-account command should update account status to suspended"
    (with-test-system
     [sys "classpath:processor/application-test.yml"]
     (let [processor (system/instance sys [:processor])]
       (nom-test>
        [_
         (SUT/process processor
                      {"command" "open-account"
                       "data" (json/write-str {"account-id" "acc-4"
                                               "name" "Account to Suspend"
                                               "currency" "EUR"})}) result
         (SUT/process processor
                      {"command" "suspend-account"
                       "data" (json/write-str {"account-id" "acc-4"})}) _
         (is (= :ok (:status result))) _ (is (= "acc-4" (:account-id result)))
         status
         (SUT/process processor
                      {"command" "get-account-status"
                       "data" (json/write-str {"account-id" "acc-4"})}) _
         (is (= :ok (:status status))) _
         (is (= "suspended" (:account-status status)))])))))

(deftest process-unsuspend-account-test
  (testing
    "Processing unsuspend-account command should update account status to open"
    (with-test-system
     [sys "classpath:processor/application-test.yml"]
     (let [processor (system/instance sys [:processor])]
       (nom-test>
        [_
         (SUT/process processor
                      {"command" "open-account"
                       "data" (json/write-str {"account-id" "acc-5"
                                               "name" "Account to Unsuspend"
                                               "currency" "GBP"})}) _
         (SUT/process processor
                      {"command" "suspend-account"
                       "data" (json/write-str {"account-id" "acc-5"})}) result
         (SUT/process processor
                      {"command" "unsuspend-account"
                       "data" (json/write-str {"account-id" "acc-5"})}) _
         (is (= :ok (:status result))) _ (is (= "acc-5" (:account-id result)))
         status
         (SUT/process processor
                      {"command" "get-account-status"
                       "data" (json/write-str {"account-id" "acc-5"})}) _
         (is (= :ok (:status status))) _
         (is (= "open" (:account-status status)))])))))

(deftest process-archive-account-test
  (testing
    "Processing archive-account command should update account status to archived"
    (with-test-system
     [sys "classpath:processor/application-test.yml"]
     (let [processor (system/instance sys [:processor])]
       (nom-test>
        [_
         (SUT/process processor
                      {"command" "open-account"
                       "data" (json/write-str {"account-id" "acc-6"
                                               "name" "Account to Archive"
                                               "currency" "CAD"})}) result
         (SUT/process processor
                      {"command" "archive-account"
                       "data" (json/write-str {"account-id" "acc-6"})}) _
         (is (= :ok (:status result))) _ (is (= "acc-6" (:account-id result)))
         status
         (SUT/process processor
                      {"command" "get-account-status"
                       "data" (json/write-str {"account-id" "acc-6"})}) _
         (is (= :ok (:status status))) _
         (is (= "archived" (:account-status status)))])))))

(deftest process-get-account-status-test
  (testing "Processing get-account-status should return the account status"
    (with-test-system
     [sys "classpath:processor/application-test.yml"]
     (let [processor (system/instance sys [:processor])]
       (nom-test>
        [_
         (SUT/process processor
                      {"command" "open-account"
                       "data" (json/write-str {"account-id" "acc-7"
                                               "name" "Status Account"
                                               "currency" "USD"})}) result
         (SUT/process processor
                      {"command" "get-account-status"
                       "data" (json/write-str {"account-id" "acc-7"})}) _
         (is (= :ok (:status result))) _ (is (= "acc-7" (:account-id result))) _
         (is (= "open" (:account-status result)))])))))

(deftest process-unknown-command-test
  (testing "Processing unknown command should return anomaly"
    (with-test-system
     [sys "classpath:processor/application-test.yml"]
     (let [processor (system/instance sys [:processor])
           result (SUT/process processor {"command" "invalid-command"})]
       (is (error/anomaly? result))
       (is (= :accounts/process-command (error/kind result)))))))
