(ns ^:eftest/synchronized com.repldriven.mono.bank-api.list-cash-accounts-test
  (:require
    com.repldriven.mono.testcontainers.interface
    [com.repldriven.mono.bank-api.api :as api]
    [com.repldriven.mono.bank-api.cursor :as cursor]
    [com.repldriven.mono.bank-organization.interface :as organizations]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [clojure.test :refer [deftest is testing]]))

(defn- list-cash-accounts-request
  [base-url token & [query-string]]
  (let [url (cond-> (str base-url "/v1/cash-accounts")
                    query-string
                    (str "?" query-string))]
    (http/request {:method :get
                   :url url
                   :headers {"Authorization" (str "Bearer " token)}})))

(deftest list-cash-accounts-test
  (with-test-system
   [sys
    ["classpath:bank-api/list-cash-accounts-test.yml"
     #(assoc-in % [:system/defs :server :handler] api/app)]]
   (let [config {:record-db (system/instance sys [:fdb :record-db])
                 :record-store (system/instance sys [:fdb :store])}
         tier-id (:tier-id (system/instance sys [:tiers :standard]))
         base-url (server/http-local-url
                   (system/instance sys [:server :jetty-adapter]))
         org (organizations/new-organization config
                                             "Cash Accounts Test Org"
                                             :organization-type-customer
                                             :organization-status-test
                                             tier-id
                                             ["GBP" "USD" "EUR"])
         _ (assert (not (error/anomaly? org)) (str "setup failed: " org))
         token (:key-secret org)
         accounts (get-in org [:organization :accounts])
         ids (sort (map :account-id accounts))]
     (testing "lists all accounts"
       (nom-test> [res (list-cash-accounts-request base-url token)
                   _ (is (= 200 (:status res)))
                   body (http/res->body res)
                   _ (is (= 3 (count (get body "cash-accounts"))))
                   _ (is (nil? (get-in body ["links" "prev"])))
                   _ (is (nil? (get-in body ["links" "next"])))]))
     (testing "paginates with page[size]"
       (nom-test> [res
                   (list-cash-accounts-request base-url token "page[size]=2")
                   _ (is (= 200 (:status res)))
                   body (http/res->body res)
                   _ (is (= 2 (count (get body "cash-accounts"))))
                   _ (is (some? (get-in body ["links" "next"])))
                   _ (is (nil? (get-in body ["links" "prev"])))]))
     (testing "paginates forward with page[after]"
       (let [after-cursor (cursor/encode (first ids))]
         (nom-test> [res (list-cash-accounts-request base-url
                                                     token
                                                     (str "page[after]="
                                                          after-cursor))
                     _ (is (= 200 (:status res)))
                     body (http/res->body res)
                     _ (is (= 2 (count (get body "cash-accounts"))))
                     _ (is (some? (get-in body ["links" "prev"])))])))
     (testing "paginates backward with page[before]"
       (let [before-cursor (cursor/encode (last ids))]
         (nom-test> [res (list-cash-accounts-request base-url
                                                     token
                                                     (str "page[before]="
                                                          before-cursor))
                     _ (is (= 200 (:status res)))
                     body (http/res->body res)
                     _ (is (= 2 (count (get body "cash-accounts"))))])))
     (testing "returns empty when no accounts match"
       (let [after-cursor (cursor/encode "acct-999")]
         (nom-test> [res (list-cash-accounts-request base-url
                                                     token
                                                     (str "page[after]="
                                                          after-cursor))
                     _ (is (= 200 (:status res)))
                     body (http/res->body res)
                     _ (is (= 0 (count (get body "cash-accounts"))))]))))))
