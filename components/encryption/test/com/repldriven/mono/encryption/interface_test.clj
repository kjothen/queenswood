(ns com.repldriven.mono.encryption.interface-test
  (:require
   [com.repldriven.mono.encryption.interface :as SUT]

   [clojure.test :as test :refer [deftest is testing]])

  (:import
   (clojure.lang ExceptionInfo)))

(deftest symmetric-key-test
  (testing
   "A encrypted string can only be decrypted with the key used during its encryption"
    (let [plain-text "Hello World"
          encryption-key (SUT/create-aes-256-key)
          unused-key (SUT/create-aes-256-key)
          encrypted
          (SUT/encrypt-str plain-text encryption-key :aes128-cbc-hmac-sha256)
          decrypted (SUT/decrypt-str encrypted encryption-key)]
      (is (= plain-text decrypted))
      (is (thrown? ExceptionInfo (SUT/decrypt-str encrypted unused-key))))))
