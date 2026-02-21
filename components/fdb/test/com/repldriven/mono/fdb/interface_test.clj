(ns ^:eftest/synchronized com.repldriven.mono.fdb.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.fdb.interface :as SUT]

    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

;; NOTE: The FDB Java client requires libfdb_c to be installed on the host —
;; the testcontainer runs FDB but the JNI bridge still needs the native library
;; locally. On macOS the Nix flake provides it; without Nix install via
;; `brew install foundationdb`. On Linux install foundationdb-clients from the
;; FDB GitHub releases.

(deftest integration-test
  (testing "FDB container starts and can execute transactions"
    (with-test-system
     [sys "classpath:fdb/application-test.yml"]
     (let [db (system/instance sys [:fdb :db])]
       (nom-test> [_ (SUT/set db "test-key" "test-value") result
                   (SUT/get db "test-key") _
                   (is (= "test-value" result)
                       "Should be able to write and read values from FDB")])))))
