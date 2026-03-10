(ns com.repldriven.mono.encryption.interface-test
  (:require
    [com.repldriven.mono.encryption.interface :as SUT]

    [clojure.test :refer [deftest is testing]]))

(deftest generate-id-test
  (testing "generates prefixed base64url ID"
    (let [id (SUT/generate-id "acc")]
      (is (string? id))
      (is (.startsWith id "acc.")))))

(deftest generate-api-key-test
  (testing "generates prefixed API key"
    (let [key (SUT/generate-api-key "sk_live_")]
      (is (string? key))
      (is (.startsWith key "sk_live_")))))

(deftest hash-api-key-test
  (testing "returns consistent hex SHA-256 hash"
    (let [key "sk_live_test123"
          h1 (SUT/hash-api-key key)
          h2 (SUT/hash-api-key key)]
      (is (string? h1))
      (is (= 64 (count h1)))
      (is (= h1 h2)))))

(deftest create-key-pair-test
  (testing "generates RSA 512-bit key pair"
    (let [kp (SUT/create-key-pair {:algorithm "RSA" :key-size 512})]
      (is (some? (:private-key kp)))
      (is (some? (:public-key kp)))
      (is (= "RSA" (:algorithm kp))))))
