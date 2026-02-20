(ns ^:eftest/synchronized com.repldriven.mono.fdb.interface-test
  (:require
    com.repldriven.mono.fdb.fdb.client
    com.repldriven.mono.fdb.system.components
    com.repldriven.mono.fdb.system.core

    [clojure.test :refer [deftest is testing]]))

;; NOTE: Full FDB integration tests with testcontainers don't work reliably
;; on macOS due to Docker networking limitations (canonical port mismatch).
;; For now, we just verify the component structure is correct.
;; To run full integration tests, install FDB natively and configure a
;; local cluster file.

(deftest fdb-component-structure-test
  (testing "FDB component namespaces load without errors"
    (is (find-ns 'com.repldriven.mono.fdb.interface))
    (is (find-ns 'com.repldriven.mono.fdb.fdb.client))
    (is (find-ns 'com.repldriven.mono.fdb.system.components))
    (is (find-ns 'com.repldriven.mono.fdb.system.core))))
