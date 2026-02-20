(ns ^:eftest/synchronized com.repldriven.mono.fdb.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    com.repldriven.mono.fdb.interface

    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]

    [clojure.test :refer [deftest is testing]]))

;; NOTE: The testcontainers-foundationdb library (v1.1.0) has a packaging issue
;; on macOS where its bundled native library references a hardcoded build-time
;; path. This test will fail on macOS but works on Linux/CI environments. For
;; local macOS development, use a native FDB installation.

(defn- test-system
  []
  (error/nom-> (env/config "classpath:fdb/application-test.yml" :test)
               system/defs
               system/start))

(deftest fdb-testcontainer-integration-test
  (testing "FDB container starts and can execute transactions"
    (system/with-system [sys (test-system)]
      (let [db (system/instance sys [:fdb :database])]
        (is (some? db) "Database instance should be created")
        (when db
          (testing "Can write and read a value"
            (let [test-key (.getBytes "test-key")
                  test-value (.getBytes "test-value")]
              ;; Write a value
              (.run db
                    (reify
                     java.util.function.Function
                       (apply [_ tr] (.set tr test-key test-value) nil)))
              ;; Read the value back
              (let [result (.run db
                                 (reify
                                  java.util.function.Function
                                    (apply [_ tr]
                                      (String. (.join (.get tr test-key))))))]
                (is (= "test-value" result)
                    "Should be able to write and read values from FDB")))))))))
