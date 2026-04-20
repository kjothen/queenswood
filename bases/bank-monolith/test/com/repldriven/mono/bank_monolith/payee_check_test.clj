(ns ^:eftest/synchronized com.repldriven.mono.bank-monolith.payee-check-test
  (:require
    com.repldriven.mono.bank-monolith.system

    [com.repldriven.mono.bank-api.api :as api]
    [com.repldriven.mono.bank-clearbank-adapter.api :as adapter]
    [com.repldriven.mono.bank-clearbank-simulator.api
     :as simulator]

    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing]]))

(defn- post-check
  [base-url creditor-name sort-code account-number account-type]
  (http/request
   {:method :post
    :url (str base-url "/v1/payee-checks")
    :headers {"Content-Type" "application/json"}
    :body (json/write-str
           {"creditor-name" creditor-name
            "account" {"sort-code" sort-code
                       "account-number" account-number}
            "account-type" account-type})}))

(defn- get-check
  [base-url check-id]
  (http/request
   {:method :get
    :url (str base-url "/v1/payee-checks/" check-id)}))

(defn- list-checks
  [base-url]
  (http/request
   {:method :get
    :url (str base-url "/v1/payee-checks")}))

(defn- patch-handlers
  [defs]
  (-> defs
      (assoc-in [:system/defs :clearbank-simulator-server :handler]
                simulator/app)
      (assoc-in [:system/defs :clearbank-adapter-server :handler]
                adapter/app)
      (assoc-in [:system/defs :server :handler] api/app)
      (assoc-in [:system/defs :server :interceptors
                 :system/config :auth]
                {:role :org
                 :organization-id "org_test_cop"})))

(deftest payee-check-test
  (with-test-system
   [sys
    ["classpath:bank-monolith/application-test.yml"
     patch-handlers]]
   (let [jetty (system/instance sys [:server :jetty-adapter])
         base-url (server/http-local-url jetty)]
     (testing "POST /v1/payee-checks returns match"
       (nom-test> [res (post-check base-url
                                   "Arthur Dent" "040062"
                                   "12345678" "personal")
                   _ (is (= 201 (:status res)))
                   body (http/res->edn res)
                   _ (is (= "match" (get-in body [:result :match-result])))
                   _ (is (string? (:check-id body)))]))
     (testing "POST /v1/payee-checks returns close-match"
       (nom-test> [res (post-check base-url
                                   "COP_CLOSEMATCH Jane" "040062"
                                   "12345678" "personal")
                   _ (is (= 201 (:status res)))
                   body (http/res->edn res)
                   _ (is (= "close-match"
                            (get-in body [:result :match-result])))]))
     (testing "POST /v1/payee-checks returns no-match"
       (nom-test> [res (post-check base-url
                                   "COP_NOMATCH" "040062"
                                   "12345678" "personal")
                   _ (is (= 201 (:status res)))
                   body (http/res->edn res)
                   _ (is (= "no-match" (get-in body [:result :match-result])))]))
     (testing "GET /v1/payee-checks/{check-id} retrieves check"
       (nom-test> [create-res (post-check base-url
                                          "Arthur Dent" "040062"
                                          "12345678" "personal")
                   _ (is (= 201 (:status create-res)))
                   create-body (http/res->edn create-res)
                   check-id (:check-id create-body)
                   get-res (get-check base-url check-id)
                   _ (is (= 200 (:status get-res)))
                   get-body (http/res->edn get-res)
                   _ (is (= check-id (:check-id get-body)))]))
     (testing "GET /v1/payee-checks lists checks"
       (nom-test> [res (list-checks base-url)
                   _ (is (= 200 (:status res)))
                   body (http/res->edn res)
                   _ (is (vector? (:items body)))
                   _ (is (pos? (count (:items body))))])))))
