(ns com.repldriven.queenswood.fdb.keyspace-test
  "Pure key-encoding tests. The prefix is the only thing standing
  between two systems sharing an FDB cluster and silently sharing each
  other's records, changelog and cursors — and the unprefixed encoding
  is what every existing deployment's data is already keyed by."
  (:require
    [com.repldriven.queenswood.fdb.keyspace :as SUT]

    [clojure.test :refer [deftest is testing]]))

(deftest unprefixed-names-are-unchanged-test
  (testing
    "no prefix leaves the name byte-identical — a deployment that
           never set one must keep reading its own data"
    (is (= "parties" (SUT/scoped nil "parties")))
    (is (= "parties" (SUT/scoped "" "parties")))))

(deftest prefixed-names-are-qualified-test
  (testing "a prefix qualifies the name"
    (is (= "rig-a.parties" (SUT/scoped "rig-a" "parties")))))

(deftest distinct-prefixes-do-not-collide-test
  (testing
    "two prefixes over the same store name stay distinct, which is
           the whole point"
    (is (not= (SUT/scoped "rig-a" "parties") (SUT/scoped "rig-b" "parties")))
    (is (not= (SUT/scoped "rig-a" "parties") (SUT/scoped nil "parties")))))
