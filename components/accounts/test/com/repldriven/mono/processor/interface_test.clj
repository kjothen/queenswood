(ns ^:eftest/synchronized com.repldriven.mono.processor.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.processor.interface :as SUT]

    [com.repldriven.mono.db.interface :as sql]
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.migrator.interface :as migrator]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test.interface :as test]
    [com.repldriven.mono.json.interface :as json]

    [clojure.test :refer [deftest is testing]]))

(defn- test-system
  []
  (error/nom-> (env/config "classpath:processor/application-test.yml" :test)
               system/defs
               system/start))

(defn- migrate-db
  [sys]
  (test/refute-anomaly
   (error/nom-> (sql/get-datasource (system/instance sys [:db :datasource]))
                (migrator/migrate "accounts/init-changelog.sql"))))

(deftest process-open-account-test
  (testing "Processing open-account command should create account in database"
    (system/with-system [sys (test-system)]
      (let [processor (system/instance sys [:processor])]
        (migrate-db sys)
        (error/nom-let> [result (SUT/process processor
                                             {"command" "open-account"
                                              "data" (json/write-str
                                                      {"account-id" "acc-1"
                                                       "name" "Test Account"
                                                       "currency" "USD"})})
                         _ (is (= :ok (:status result)))
                         _ (is (= "acc-1" (:account-id result)))
                         status (SUT/process processor
                                             {"command" "get-account-status"
                                              "data" (json/write-str
                                                      {"account-id" "acc-1"})})
                         _ (is (= :ok (:status status)))
                         _ (is (= "open" (:account-status status)))]
          test/refute-anomaly)))))

(deftest process-close-account-test
  (testing
    "Processing close-account command should update account status in database"
    (system/with-system [sys (test-system)]
      (let [processor (system/instance sys [:processor])]
        (migrate-db sys)
        (error/nom-let> [_ (SUT/process processor
                                        {"command" "open-account"
                                         "data" (json/write-str
                                                 {"account-id" "acc-2"
                                                  "name" "Account to Close"
                                                  "currency" "USD"})})
                         result (SUT/process processor
                                             {"command" "close-account"
                                              "data" (json/write-str
                                                      {"account-id" "acc-2"})})
                         _ (is (= :ok (:status result)))
                         _ (is (= "acc-2" (:account-id result)))
                         status (SUT/process processor
                                             {"command" "get-account-status"
                                              "data" (json/write-str
                                                      {"account-id" "acc-2"})})
                         _ (is (= :ok (:status status)))
                         _ (is (= "closed" (:account-status status)))]
          test/refute-anomaly)))))

(deftest process-reopen-account-test
  (testing
    "Processing reopen-account command should update account status to open"
    (system/with-system [sys (test-system)]
      (let [processor (system/instance sys [:processor])]
        (migrate-db sys)
        (error/nom-let> [_ (SUT/process processor
                                        {"command" "open-account"
                                         "data" (json/write-str
                                                 {"account-id" "acc-3"
                                                  "name" "Account to Reopen"
                                                  "currency" "USD"})})
                         _ (SUT/process processor
                                        {"command" "close-account"
                                         "data" (json/write-str {"account-id"
                                                                 "acc-3"})})
                         result (SUT/process processor
                                             {"command" "reopen-account"
                                              "data" (json/write-str
                                                      {"account-id" "acc-3"})})
                         _ (is (= :ok (:status result)))
                         _ (is (= "acc-3" (:account-id result)))
                         status (SUT/process processor
                                             {"command" "get-account-status"
                                              "data" (json/write-str
                                                      {"account-id" "acc-3"})})
                         _ (is (= :ok (:status status)))
                         _ (is (= "open" (:account-status status)))]
          test/refute-anomaly)))))

(deftest process-suspend-account-test
  (testing
    "Processing suspend-account command should update account status to suspended"
    (system/with-system [sys (test-system)]
      (let [processor (system/instance sys [:processor])]
        (migrate-db sys)
        (error/nom-let> [_ (SUT/process processor
                                        {"command" "open-account"
                                         "data" (json/write-str
                                                 {"account-id" "acc-4"
                                                  "name" "Account to Suspend"
                                                  "currency" "EUR"})})
                         result (SUT/process processor
                                             {"command" "suspend-account"
                                              "data" (json/write-str
                                                      {"account-id" "acc-4"})})
                         _ (is (= :ok (:status result)))
                         _ (is (= "acc-4" (:account-id result)))
                         status (SUT/process processor
                                             {"command" "get-account-status"
                                              "data" (json/write-str
                                                      {"account-id" "acc-4"})})
                         _ (is (= :ok (:status status)))
                         _ (is (= "suspended" (:account-status status)))]
          test/refute-anomaly)))))

(deftest process-unsuspend-account-test
  (testing
    "Processing unsuspend-account command should update account status to open"
    (system/with-system [sys (test-system)]
      (let [processor (system/instance sys [:processor])]
        (migrate-db sys)
        (error/nom-let> [_ (SUT/process processor
                                        {"command" "open-account"
                                         "data" (json/write-str
                                                 {"account-id" "acc-5"
                                                  "name" "Account to Unsuspend"
                                                  "currency" "GBP"})})
                         _ (SUT/process processor
                                        {"command" "suspend-account"
                                         "data" (json/write-str {"account-id"
                                                                 "acc-5"})})
                         result (SUT/process processor
                                             {"command" "unsuspend-account"
                                              "data" (json/write-str
                                                      {"account-id" "acc-5"})})
                         _ (is (= :ok (:status result)))
                         _ (is (= "acc-5" (:account-id result)))
                         status (SUT/process processor
                                             {"command" "get-account-status"
                                              "data" (json/write-str
                                                      {"account-id" "acc-5"})})
                         _ (is (= :ok (:status status)))
                         _ (is (= "open" (:account-status status)))]
          test/refute-anomaly)))))

(deftest process-archive-account-test
  (testing
    "Processing archive-account command should update account status to archived"
    (system/with-system [sys (test-system)]
      (let [processor (system/instance sys [:processor])]
        (migrate-db sys)
        (error/nom-let> [_ (SUT/process processor
                                        {"command" "open-account"
                                         "data" (json/write-str
                                                 {"account-id" "acc-6"
                                                  "name" "Account to Archive"
                                                  "currency" "CAD"})})
                         result (SUT/process processor
                                             {"command" "archive-account"
                                              "data" (json/write-str
                                                      {"account-id" "acc-6"})})
                         _ (is (= :ok (:status result)))
                         _ (is (= "acc-6" (:account-id result)))
                         status (SUT/process processor
                                             {"command" "get-account-status"
                                              "data" (json/write-str
                                                      {"account-id" "acc-6"})})
                         _ (is (= :ok (:status status)))
                         _ (is (= "archived" (:account-status status)))]
          test/refute-anomaly)))))

(deftest process-get-account-status-test
  (testing "Processing get-account-status should return the account status"
    (system/with-system [sys (test-system)]
      (let [processor (system/instance sys [:processor])]
        (migrate-db sys)
        (error/nom-let> [_ (SUT/process processor
                                        {"command" "open-account"
                                         "data" (json/write-str
                                                 {"account-id" "acc-7"
                                                  "name" "Status Account"
                                                  "currency" "USD"})})
                         result (SUT/process processor
                                             {"command" "get-account-status"
                                              "data" (json/write-str
                                                      {"account-id" "acc-7"})})
                         _ (is (= :ok (:status result)))
                         _ (is (= "acc-7" (:account-id result)))
                         _ (is (= "open" (:account-status result)))]
          test/refute-anomaly)))))

(deftest process-unknown-command-test
  (testing "Processing unknown command should return anomaly"
    (system/with-system [sys (test-system)]
      (let [processor (system/instance sys [:processor])]
        (migrate-db sys)
        (let [command {"command" "invalid-command"}
              result (SUT/process processor command)]
          (is (error/anomaly? result))
          (is (= :accounts/process-command (error/kind result))))))))
