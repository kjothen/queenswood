(ns ^:eftest/synchronized
    com.repldriven.mono.bank-chart-of-accounts.interface-test
  (:require
    [com.repldriven.mono.bank-chart-of-accounts.interface :as SUT]

    [clojure.test :refer [deftest is testing]]))

(deftest template-test
  (testing "exactly the seven canonical GL accounts"
    (is (= ["1100" "1200" "2100" "2200" "2300" "2400" "2500"]
           (mapv :gl-code SUT/template))))
  (testing "every row carries the GL discriminator fields"
    (doseq [row SUT/template]
      (is (string? (:gl-code row)))
      (is (string? (:name row)))
      (is (#{:gl-account-type-asset :gl-account-type-liability}
           (:gl-account-type row)))
      (is (#{:gl-account-class-detail :gl-account-class-control}
           (:gl-account-class row)))
      (is (= :required-mandatory (:required row)))))
  (testing "2100/2200/2300 are control accounts; others are detail"
    (let [by-code
          (into {} (map (juxt :gl-code :gl-account-class)) SUT/template)]
      (is (= :gl-account-class-control (by-code "2100")))
      (is (= :gl-account-class-control (by-code "2200")))
      (is (= :gl-account-class-control (by-code "2300")))
      (is (= :gl-account-class-detail (by-code "1100")))
      (is (= :gl-account-class-detail (by-code "1200")))
      (is (= :gl-account-class-detail (by-code "2400")))
      (is (= :gl-account-class-detail (by-code "2500"))))))

(deftest control-code-for-product-type-test
  (is (= "2100"
         (SUT/control-code-for-product-type :product-type-sub-ledger-current)))
  (is (= "2200"
         (SUT/control-code-for-product-type :product-type-sub-ledger-savings)))
  (is (= "2300"
         (SUT/control-code-for-product-type
          :product-type-sub-ledger-term-deposit)))
  (testing "non-customer product types have no control"
    (is (nil? (SUT/control-code-for-product-type :product-type-general-ledger)))
    (is (nil? (SUT/control-code-for-product-type :product-type-unknown)))))

(deftest mandatory?-test
  (testing "every seeded gl-code is mandatory in this wave"
    (doseq [code ["1100" "1200" "2100" "2200" "2300" "2400" "2500"]]
      (is (true? (SUT/mandatory? code)) (str code " should be mandatory"))))
  (testing "unknown gl-codes are not mandatory"
    (is (false? (SUT/mandatory? "9999")))
    (is (false? (SUT/mandatory? nil)))
    (is (false? (SUT/mandatory? "")))))
