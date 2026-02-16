(ns ^:eftest/synchronized com.repldriven.mono.processor.interface-test
  (:require
   com.repldriven.mono.testcontainers.interface

   [com.repldriven.mono.processor.interface :as SUT]
   [com.repldriven.mono.processor.commands.account-lifecycle :as account-lifecycle]
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

(defn- db-spec
  [sys]
  (let [datasource (system/instance sys [:db :datasource])]
    (sql/get-datasource datasource)))

(deftest process-open-account-test
  (testing "Processing open-account command should create account in database"
    (system/with-system [sys (test-system)]
      (let [spec (db-spec sys)
            processor (system/instance sys [:processor])]
        ;; Run migrations
        (migrator/migrate spec "accounts/init-changelog.sql")

        ;; Test the processor
        (let [command {"type" "open-account"
                       "id" "cmd-1"
                       "data" (json/write-str {"account-id" "acc-1"
                                               "name" "Test Account"
                                               "currency" "USD"})}]
          (error/with-let-anomaly?
            [result (SUT/process processor command)
             _ (is (= :ok (:status result)))
             _ (is (= "acc-1" (:account-id result)))
             ;; Verify account was created in database
             account (account-lifecycle/get processor "acc-1")
             _ (is (some? account))
             _ (is (= "acc-1" (:account_id account)))
             _ (is (= "Test Account" (:name account)))
             _ (is (= "open" (:status account)))
             _ (is (= "USD" (:currency account)))]
            test/refute-anomaly))))))

(deftest process-close-account-test
  (testing "Processing close-account command should update account status in database"
    (system/with-system [sys (test-system)]
      (let [spec (db-spec sys)
            processor (system/instance sys [:processor])]
        ;; Run migrations
        (migrator/migrate spec "accounts/init-changelog.sql")

        ;; First create an account
        (let [open-command {"type" "open-account"
                            "id" "cmd-1"
                            "data" (json/write-str {"account-id" "acc-2"
                                                    "name" "Account to Close"
                                                    "currency" "USD"})}]
          (error/with-let-anomaly?
            [_ (SUT/process processor open-command)
             ;; Now close the account
             close-command {"type" "close-account"
                            "id" "cmd-2"
                            "data" (json/write-str {"account-id" "acc-2"})}
             result (SUT/process processor close-command)
             _ (is (= :ok (:status result)))
             _ (is (= "acc-2" (:account-id result)))
             ;; Verify account status was updated to closed
             account (account-lifecycle/get processor "acc-2")
             _ (is (some? account))
             _ (is (= "acc-2" (:account_id account)))
             _ (is (= "closed" (:status account)))]
            test/refute-anomaly))))))

(deftest process-reopen-account-test
  (testing "Processing reopen-account command should update account status to open"
    (system/with-system [sys (test-system)]
      (let [spec (db-spec sys)
            processor (system/instance sys [:processor])]
        ;; Run migrations
        (migrator/migrate spec "accounts/init-changelog.sql")

        ;; Create and close an account first
        (error/with-let-anomaly?
          [_ (SUT/process processor {"type" "open-account"
                                     "id" "cmd-1"
                                     "data" (json/write-str {"account-id" "acc-3"
                                                             "name" "Account to Reopen"
                                                             "currency" "USD"})})
           _ (SUT/process processor {"type" "close-account"
                                     "id" "cmd-2"
                                     "data" (json/write-str {"account-id" "acc-3"})})
           ;; Now reopen the account
           reopen-command {"type" "reopen-account"
                           "id" "cmd-3"
                           "data" (json/write-str {"account-id" "acc-3"})}
           result (SUT/process processor reopen-command)
           _ (is (= :ok (:status result)))
           _ (is (= "acc-3" (:account-id result)))
           ;; Verify account status was updated to open
           account (account-lifecycle/get processor "acc-3")
           _ (is (some? account))
           _ (is (= "acc-3" (:account_id account)))
           _ (is (= "open" (:status account)))]
          test/refute-anomaly)))))

(deftest process-suspend-account-test
  (testing "Processing suspend-account command should update account status to suspended"
    (system/with-system [sys (test-system)]
      (let [spec (db-spec sys)
            processor (system/instance sys [:processor])]
        ;; Run migrations
        (migrator/migrate spec "accounts/init-changelog.sql")

        ;; Create an account first
        (error/with-let-anomaly?
          [_ (SUT/process processor {"type" "open-account"
                                     "id" "cmd-1"
                                     "data" (json/write-str {"account-id" "acc-4"
                                                             "name" "Account to Suspend"
                                                             "currency" "EUR"})})
           ;; Now suspend the account
           suspend-command {"type" "suspend-account"
                            "id" "cmd-2"
                            "data" (json/write-str {"account-id" "acc-4"})}
           result (SUT/process processor suspend-command)
           _ (is (= :ok (:status result)))
           _ (is (= "acc-4" (:account-id result)))
           ;; Verify account status was updated to suspended
           account (account-lifecycle/get processor "acc-4")
           _ (is (some? account))
           _ (is (= "acc-4" (:account_id account)))
           _ (is (= "suspended" (:status account)))]
          test/refute-anomaly)))))

(deftest process-unsuspend-account-test
  (testing "Processing unsuspend-account command should update account status to open"
    (system/with-system [sys (test-system)]
      (let [spec (db-spec sys)
            processor (system/instance sys [:processor])]
        ;; Run migrations
        (migrator/migrate spec "accounts/init-changelog.sql")

        ;; Create and suspend an account first
        (error/with-let-anomaly?
          [_ (SUT/process processor {"type" "open-account"
                                     "id" "cmd-1"
                                     "data" (json/write-str {"account-id" "acc-5"
                                                             "name" "Account to Unsuspend"
                                                             "currency" "GBP"})})
           _ (SUT/process processor {"type" "suspend-account"
                                     "id" "cmd-2"
                                     "data" (json/write-str {"account-id" "acc-5"})})
           ;; Now unsuspend the account
           unsuspend-command {"type" "unsuspend-account"
                              "id" "cmd-3"
                              "data" (json/write-str {"account-id" "acc-5"})}
           result (SUT/process processor unsuspend-command)
           _ (is (= :ok (:status result)))
           _ (is (= "acc-5" (:account-id result)))
           ;; Verify account status was updated to open
           account (account-lifecycle/get processor "acc-5")
           _ (is (some? account))
           _ (is (= "acc-5" (:account_id account)))
           _ (is (= "open" (:status account)))]
          test/refute-anomaly)))))

(deftest process-archive-account-test
  (testing "Processing archive-account command should update account status and set deleted_at"
    (system/with-system [sys (test-system)]
      (let [spec (db-spec sys)
            processor (system/instance sys [:processor])]
        ;; Run migrations
        (migrator/migrate spec "accounts/init-changelog.sql")

        ;; Create an account first
        (error/with-let-anomaly?
          [_ (SUT/process processor {"type" "open-account"
                                     "id" "cmd-1"
                                     "data" (json/write-str {"account-id" "acc-6"
                                                             "name" "Account to Archive"
                                                             "currency" "CAD"})})
           ;; Now archive the account
           archive-command {"type" "archive-account"
                            "id" "cmd-2"
                            "data" (json/write-str {"account-id" "acc-6"})}
           result (SUT/process processor archive-command)
           _ (is (= :ok (:status result)))
           _ (is (= "acc-6" (:account-id result)))
           ;; Verify account status was updated to archived
           account (account-lifecycle/get processor "acc-6")
           _ (is (some? account))
           _ (is (= "acc-6" (:account_id account)))
           _ (is (= "archived" (:status account)))
           _ (is (some? (:deleted_at account)))]
          test/refute-anomaly)))))

(deftest process-unknown-command-test
  (testing "Processing unknown command should return anomaly"
    (system/with-system [sys (test-system)]
      (let [spec (db-spec sys)
            processor (system/instance sys [:processor])]
        ;; Run migrations
        (migrator/migrate spec "accounts/init-changelog.sql")

        ;; Test the processor
        (let [command {"type" "invalid-command" "id" "cmd-3"}
              result (SUT/process processor command)]
          (is (error/anomaly? result))
          (is (= :accounts/unknown-command (error/kind result))))))))
