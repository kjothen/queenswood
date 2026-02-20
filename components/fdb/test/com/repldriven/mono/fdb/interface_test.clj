(ns ^:eftest/synchronized com.repldriven.mono.fdb.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface
    com.repldriven.mono.fdb.interface

    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]

    [clojure.test :refer [deftest is testing]]))

;; NOTE: The FDB Java client requires libfdb_c to be installed on the host —
;; the testcontainer runs FDB but the JNI bridge still needs the native library
;; locally. On macOS the Nix flake provides it; without Nix install via
;; `brew install foundationdb`. On Linux install foundationdb-clients from the
;; FDB GitHub releases.

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
