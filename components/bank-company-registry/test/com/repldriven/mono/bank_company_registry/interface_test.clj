(ns ^:eftest/synchronized
  com.repldriven.mono.bank-company-registry.interface-test
  (:require
    [com.repldriven.mono.bank-company-registry.interface :as company-registry]

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

(def ^:private registry "uk-companies-house")

(defn- ->plain
  "Recursively convert protojure records to plain maps so equality
  comparisons against literal maps work in tests."
  [x]
  (walk/postwalk (fn [n] (if (record? n) (into {} n) n)) x))

(defn- lookup-config
  [sys]
  {:companies-house-url (server/http-local-url
                         (system/instance sys [:server :jetty-adapter]))
   :record-db (system/instance sys [:fdb :record-db])
   :record-store (system/instance sys [:fdb :meta-store])})

(deftest lookup-company-test
  (with-test-system
   [sys
    ["classpath:bank-company-registry/application-test.yml"
     #(assoc-in % [:system/defs :server :handler] api/app)]]
   (let [config (lookup-config sys)]
     (testing "fetches a known company and persists it under company_number"
       (nom-test> [company (company-registry/lookup-company
                            config registry "SC998137")
                   _ (is (= "SC998137" (:company-number company)))
                   _ (is (= "SIRIUS CYBERNETICS CORPORATION LTD"
                            (:company-name company)))
                   _ (is (= "active" (:company-status company)))
                   _ (is (= "United Kingdom"
                            (get-in company
                                    [:registered-office-address :country])))
                   stored (company-registry/get-company
                           config registry "SC998137")
                   _ (is (= company (->plain stored)))]))
     (testing "company-not-found anomaly for unknown numbers"
       (let [result (company-registry/lookup-company
                     config registry "99999999")]
         (is (error/anomaly? result))
         (is (= :company-registry/company-not-found (error/kind result)))
         (is (nil? (company-registry/get-company config registry "99999999")))))
     (testing "rejects an unsupported registry id"
       (let [result (company-registry/lookup-company
                     config "fr-infogreffe" "SC998137")]
         (is (error/anomaly? result))
         (is (= :company-registry/registry-not-found (error/kind result))))))))
