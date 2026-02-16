(ns ^:eftest/synchronized com.repldriven.mono.processor.interface-test
  (:require
   com.repldriven.mono.testcontainers.interface

   [com.repldriven.mono.processor.interface :as SUT]
   [com.repldriven.mono.processor.commands.account :as account]
   [com.repldriven.mono.db.interface :as sql]
   [com.repldriven.mono.env.interface :as env]
   [com.repldriven.mono.error.interface :as error]
   [com.repldriven.mono.migrator.interface :as migrator]
   [com.repldriven.mono.system.interface :as system]
   [com.repldriven.mono.test.interface :as test]

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
        (let [command {:type "open-account"
                       :id "cmd-1"
                       :data {:account-id "acc-1"
                              :name "Test Account"
                              :currency "USD"}}]
          (error/with-let-anomaly?
            [result (SUT/process processor command)
             _ (is (= :ok (:status result)))
             _ (is (= "acc-1" (:account-id result)))
             ;; Verify account was created in database
             account (account/get processor "acc-1")
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
        (let [open-command {:type "open-account"
                            :id "cmd-1"
                            :data {:account-id "acc-2"
                                   :name "Account to Close"
                                   :currency "USD"}}]
          (error/with-let-anomaly?
            [_ (SUT/process processor open-command)
             ;; Now close the account
             close-command {:type "close-account"
                            :id "cmd-2"
                            :data {:account-id "acc-2"}}
             result (SUT/process processor close-command)
             _ (is (= :ok (:status result)))
             _ (is (= "acc-2" (:account-id result)))
             ;; Verify account status was updated to closed
             account (account/get processor "acc-2")
             _ (is (some? account))
             _ (is (= "acc-2" (:account_id account)))
             _ (is (= "closed" (:status account)))]
            test/refute-anomaly))))))

(deftest process-unknown-command-test
  (testing "Processing unknown command should return anomaly"
    (system/with-system [sys (test-system)]
      (let [spec (db-spec sys)
            processor (system/instance sys [:processor])]
        ;; Run migrations
        (migrator/migrate spec "accounts/init-changelog.sql")

        ;; Test the processor
        (let [command {:type "invalid-command" :id "cmd-3"}
              result (SUT/process processor command)]
          (is (error/anomaly? result))
          (is (= :accounts/unknown-command (error/kind result))))))))
