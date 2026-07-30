(ns com.repldriven.queenswood.fdb.changelog-test
  "Golden bytes for the changelog key encodings. An unprefixed key that
  shifts by one byte strands every deployment's changelog and — worse,
  because it fails silently and then republishes history — every
  consumer checkpoint."
  (:require
    [com.repldriven.queenswood.fdb.changelog :as changelog]

    [clojure.test :refer [deftest is testing]])
  (:import
    (com.apple.foundationdb.tuple Tuple)))

(defn- packed
  [parts]
  (.pack (Tuple/from (into-array Object parts))))

(deftest unprefixed-keys-are-unchanged-test
  (testing "checkpoint key keeps its historical encoding"
    (is (= (seq (packed ["mono" "checkpoint" "idv-party-watcher" "parties"]))
           (seq
            (#'changelog/checkpoint-key nil "idv-party-watcher" "parties")))))
  (testing "sentinel key keeps its historical encoding"
    (is (= (seq (packed ["mono" "sentinel" "parties"]))
           (seq (#'changelog/sentinel-key nil "parties")))))
  (testing "changelog subspace keeps its historical encoding"
    (is (= (seq (packed ["mono" "changelog" "parties"]))
           (seq (.pack (#'changelog/changelog-subspace nil "parties")))))))

(deftest a-blank-prefix-is-treated-as-none-test
  (testing
    "an empty string must not qualify anything — an unset env var
           reads as \"\" often enough that it has to be inert"
    (is (= (seq (#'changelog/sentinel-key nil "parties"))
           (seq (#'changelog/sentinel-key "" "parties"))))))

(deftest prefixed-keys-are-disjoint-test
  (testing "a prefix moves the changelog into its own subtree"
    (is (= (seq (packed ["rig-a" "mono" "changelog" "parties"]))
           (seq (.pack (#'changelog/changelog-subspace "rig-a" "parties"))))))
  (testing
    "two rigs over one store share no changelog and no cursor,
           which is what makes a shared testcontainer safe"
    (is (not= (seq (.pack (#'changelog/changelog-subspace "rig-a" "parties")))
              (seq (.pack (#'changelog/changelog-subspace "rig-b" "parties")))))
    (is (not= (seq (#'changelog/checkpoint-key "rig-a" "c" "parties"))
              (seq (#'changelog/checkpoint-key "rig-b" "c" "parties"))))))
