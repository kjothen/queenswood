(ns ^:eftest/synchronized com.repldriven.mono.fdb.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]

    [clojure.test :refer [deftest is testing]])
  (:import
    (com.apple.foundationdb Database
                            Transaction)
    (java.util.function Function)))

(defn- test-system
  []
  (error/nom-> (env/config "classpath:fdb/application-test.yml" :test)
               system/defs
               system/start))

(deftest fdb-test
  (system/with-system [sys (test-system)]
    (let [^Database db (system/instance sys [:fdb :database])]
      (testing "FDB database instance is created"
        (is (some? db))
        (is (instance? Database db)))
      (testing "FDB database can perform basic key-value operations"
        (try (let [test-key (.getBytes "test-key")
                   test-value (.getBytes "test-value")
                   txn-fn (reify
                           Function
                             (apply [_ tr]
                               (let [^Transaction t tr]
                                 (.set t test-key test-value)
                                 (.get t test-key))))
                   result (.run db txn-fn)]
               (is (some? result))
               (is (= "test-value" (String. (.join result)))))
             (catch Exception e
               (is false
                   (str "Exception during FDB operation: "
                        (.getMessage e)))))))))
