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
  (testing "Processing open-account command should succeed"
    (system/with-system [sys (test-system)]
      (let [spec (db-spec sys)
            processor (system/instance sys [:processor])]
        ;; Run migrations
        (migrator/migrate spec "accounts/init-changelog.sql")

        ;; Test the processor
        (let [command {:type "open-account" :id "cmd-1" :data {:account-id "acc-1"}}]
          (error/with-let-anomaly?
            [result (SUT/process processor command)
             _ (is (= :ok (:status result)))
             _ (is (= "cmd-1" (:command-id result)))]
            test/refute-anomaly))))))

(deftest process-close-account-test
  (testing "Processing close-account command should succeed"
    (system/with-system [sys (test-system)]
      (let [spec (db-spec sys)
            processor (system/instance sys [:processor])]
        ;; Run migrations
        (migrator/migrate spec "accounts/init-changelog.sql")

        ;; Test the processor
        (let [command {:type "close-account" :id "cmd-2" :data {:account-id "acc-1"}}]
          (error/with-let-anomaly?
            [result (SUT/process processor command)
             _ (is (= :ok (:status result)))
             _ (is (= "cmd-2" (:command-id result)))]
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
