(ns ^:eftest/synchronized com.repldriven.mono.bank-company-check.interface-test
  (:require
    [com.repldriven.mono.bank-company-check.interface :as company-check]

    com.repldriven.mono.bank-uk-companies-house-simulator.system

    [com.repldriven.mono.bank-uk-companies-house-simulator.api :as api]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.testcontainers.interface]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]
    [clojure.walk :as walk]))

(defn- ->plain
  "Recursively convert protojure records to plain maps so equality
  comparisons against literal maps work in tests."
  [x]
  (walk/postwalk (fn [n] (if (record? n) (into {} n) n)) x))

(defn- check-config
  [sys]
  {:api-url (server/http-local-url
             (system/instance sys [:server :jetty-adapter]))
   :record-db (system/instance sys [:fdb :record-db])
   :record-store (system/instance sys [:fdb :meta-store])})

(deftest check-company-test
  (with-test-system
   [sys
    ["classpath:bank-company-check/application-test.yml"
     #(assoc-in % [:system/defs :server :handler] api/app)]]
   (let [config (check-config sys)]
     (testing "fetches a known company and persists it under company_number"
       (nom-test> [company (company-check/check-company config "00006400")
                   _ (is (= "00006400" (:company-number company)))
                   _ (is (= "TESCO PLC" (:company-name company)))
                   _ (is (= "active" (:company-status company)))
                   _ (is (= "United Kingdom"
                            (get-in company
                                    [:registered-office-address :country])))
                   stored (company-check/get-company config "00006400")
                   _ (is (= company (->plain stored)))]))
     (testing "returns a :company-check/not-found anomaly for unknown numbers"
       (let [result (company-check/check-company config "99999999")]
         (is (error/anomaly? result))
         (is (= :company-check/not-found (error/kind result)))
         (is (nil? (company-check/get-company config "99999999"))))))))
