(ns ^:eftest/synchronized com.repldriven.mono.bank-api.list-parties-test
  (:require
    com.repldriven.mono.testcontainers.interface
    [com.repldriven.mono.bank-api.api :as api]
    [com.repldriven.mono.bank-api.cursor :as cursor]
    [com.repldriven.mono.bank-organization.interface :as organizations]
    [com.repldriven.mono.bank-party.interface :as parties]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [clojure.test :refer [deftest is testing]]))

(defn- list-parties-request
  [base-url token & [query-string]]
  (let [url (cond-> (str base-url "/v1/parties")
                    query-string
                    (str "?" query-string))]
    (http/request {:method :get
                   :url url
                   :headers {"Authorization" (str "Bearer " token)}})))

(defn- get-party-request
  [base-url token party-id]
  (http/request {:method :get
                 :url (str base-url "/v1/parties/" party-id)
                 :headers {"Authorization" (str "Bearer " token)}}))

(deftest list-parties-test
  (with-test-system
   [sys
    ["classpath:bank-api/list-parties-test.yml"
     #(assoc-in % [:system/defs :server :handler] api/app)]]
   (let [config {:record-db (system/instance sys [:fdb :record-db])
                 :record-store (system/instance sys [:fdb :store])}
         base-url (server/http-local-url
                   (system/instance sys [:server :jetty-adapter]))
         org (organizations/new-organization config
                                             "Parties Test Org"
                                             :organization-type-customer
                                             :tier-type-micro ["GBP"])
         _ (assert (not (error/anomaly? org)) (str "setup failed: " org))
         org-id (get-in org [:organization :organization-id])
         token (:key-secret org)
         p2 (parties/new-party config
                               {:organization-id org-id
                                :type :party-type-internal
                                :display-name "Party 2"})
         _ (assert (not (error/anomaly? p2)) (str "setup failed: " p2))
         p3 (parties/new-party config
                               {:organization-id org-id
                                :type :party-type-internal
                                :display-name "Party 3"})
         _ (assert (not (error/anomaly? p3)) (str "setup failed: " p3))
         first-party-id (get-in org [:organization :party :party-id])
         ids (sort [first-party-id (:party-id p2) (:party-id p3)])]
     (testing "lists all parties"
       (nom-test> [res (list-parties-request base-url token)
                   _ (is (= 200 (:status res)))
                   body (http/res->body res)
                   _ (is (= 3 (count (get body "parties"))))]))
     (testing "paginates with page[size]"
       (nom-test> [res (list-parties-request base-url token "page[size]=2")
                   _ (is (= 200 (:status res)))
                   body (http/res->body res)
                   _ (is (= 2 (count (get body "parties"))))
                   _ (is (some? (get-in body ["links" "next"])))
                   _ (is (nil? (get-in body ["links" "prev"])))]))
     (testing "paginates forward with page[after]"
       (let [after-cursor (cursor/encode (first ids))]
         (nom-test> [res (list-parties-request base-url
                                               token
                                               (str "page[after]="
                                                    after-cursor))
                     _ (is (= 200 (:status res)))
                     body (http/res->body res)
                     _ (is (= 2 (count (get body "parties"))))
                     _ (is (some? (get-in body ["links" "prev"])))])))
     (testing "paginates backward with page[before]"
       (let [before-cursor (cursor/encode (last ids))]
         (nom-test> [res (list-parties-request base-url
                                               token
                                               (str "page[before]="
                                                    before-cursor))
                     _ (is (= 200 (:status res)))
                     body (http/res->body res)
                     _ (is (= 2 (count (get body "parties"))))])))
     (testing "get party by id"
       (nom-test> [res (get-party-request base-url token (first ids))
                   _ (is (= 200 (:status res)))
                   body (http/res->body res)
                   _ (is (= (first ids) (get body "party-id")))]))
     (testing "get party returns 404 for unknown id"
       (nom-test> [res (get-party-request base-url token "py-unknown")
                   _ (is (= 404 (:status res)))])))))
