(ns ^:eftest/synchronized com.repldriven.mono.fdb.interface-test
  (:require
    com.repldriven.mono.fdb.interface

    [clojure.test :refer [deftest is testing]]))

;; NOTE: FDB testcontainers don't work on macOS due to Docker Desktop
;; networking limitations. Container IPs (172.17.0.x) are not routable from
;; the macOS host, and binding to 0.0.0.0 inside the container doesn't help.
;;
;; The reference implementation (aleris/testcontainers-foundationdb) solves
;; this with a Socat proxy container, which adds significant complexity.
;;
;; For now, we verify the component structure loads correctly. For full
;; integration testing on macOS:
;; 1. Install FDB natively: download from
;; https://github.com/apple/foundationdb/releases/tag/7.3.27
;; 2. Start fdbserver manually
;; 3. Configure test with native cluster file path
;;
;; On Linux, the testcontainer should work as container IPs are directly
;; routable.

(deftest fdb-component-structure-test
  (testing "FDB component namespaces load without errors"
    (is (find-ns 'com.repldriven.mono.fdb.interface))
    (is (find-ns 'com.repldriven.mono.fdb.fdb.client))
    (is (find-ns 'com.repldriven.mono.fdb.system.components))
    (is (find-ns 'com.repldriven.mono.fdb.system.core))))
