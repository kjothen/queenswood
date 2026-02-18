(ns com.repldriven.mono.iam-api.api-test
  (:refer-clojure :exclude [name test])
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.iam-api.api :as api]
    [com.repldriven.mono.iam-api.database :as database]

    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test.interface :as test]

    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing]]))

(def ^:dynamic *base-url* "http://localhost:{PORT}")
(def project-id "prj-test")

(def service-account-create-body
  {"account-id" "sa-test"
   "service-account" {"display-name" "sa-test-zzz-d-shared"
                      "description"
                      "Test service account for all dev projects"}})

(defn- test-system
  []
  (error/nom-> (env/config "classpath:iam-api/application-test.yml" :test)
               system/defs
               (assoc-in [:system/defs :server :handler] api/app)
               system/start
               database/migrate))

(defn create-service-account
  []
  (http/request {:url
                 (str *base-url* "/v1/projects/" project-id "/serviceAccounts")
                 :method :post
                 :headers {"Content-Type" "application/json"}
                 :body (json/write-str service-account-create-body)}))

(defn list-service-accounts
  []
  (http/request {:url
                 (str *base-url* "/v1/projects/" project-id "/serviceAccounts")
                 :method :get}))

(defn get-service-account
  [name]
  (http/request {:url (str *base-url* "/v1/" name) :method :get}))

(defn disable-service-account
  [name]
  (http/request {:url (str *base-url* "/v1/" name ":disable") :method :post}))

(defn enable-service-account
  [name]
  (http/request {:url (str *base-url* "/v1/" name ":enable") :method :post}))

(defn delete-service-account
  [name]
  (http/request {:url (str *base-url* "/v1/" name) :method :delete}))

(defn undelete-service-account
  [name]
  (http/request {:url (str *base-url* "/v1/" name ":undelete") :method :post}))

(deftest service-accounts-api
  (testing "serviceAccounts API"
    (system/with-system [sys (test-system)]
      (let [jetty (system/instance sys [:server :jetty-adapter])]
        (binding [*base-url* (server/http-local-url jetty)]
          (error/with-let-anomaly?
            ; create service account
            [res (create-service-account)
             _ (is (= 201 (:status res)))
             service-account (http/res->body res)
             _ (is (= false (get service-account "disabled")))
             ; get created service account
             get-res (get-service-account (get service-account "name"))
             _ (is (= 200 (:status get-res)))
             _ (is (= service-account (http/res->body get-res)))
             ; list service accounts
             list-res (list-service-accounts)
             _ (is (= 200 (:status list-res)))
             service-accounts (http/res->body list-res)
             _ (is (= 1 (count (get service-accounts "accounts"))))
             _ (is (= service-account
                      (first (get service-accounts "accounts"))))
             ; disable service account
             disable-res (disable-service-account (get service-account "name"))
             _ (is (= 204 (:status disable-res)))
             _ (is (or (nil? (:body disable-res)) (empty? (:body disable-res))))
             disabled-get-res (get-service-account (get service-account "name"))
             _ (is (= 200 (:status disabled-get-res)))
             _ (is (= true (get (http/res->body disabled-get-res) "disabled")))
             ; enable service account
             enable-res (enable-service-account (get service-account "name"))
             _ (is (= 204 (:status enable-res)))
             _ (is (or (nil? (:body enable-res)) (empty? (:body enable-res))))
             enabled-get-res (get-service-account (get service-account "name"))
             _ (is (= 200 (:status enabled-get-res)))
             _ (is (= false (get (http/res->body enabled-get-res) "disabled")))
             ; delete service account
             delete-res (delete-service-account (get service-account "name"))
             _ (is (= 204 (:status delete-res)))
             _ (is (or (nil? (:body delete-res)) (empty? (:body delete-res))))
             after-delete-list (list-service-accounts)
             _ (is (= 200 (:status after-delete-list)))
             after-delete-accounts (http/res->body after-delete-list)
             _ (is (zero? (count (get after-delete-accounts "accounts"))))
             ; undelete service account
             undelete-res (undelete-service-account (get service-account
                                                         "name"))
             _ (is (= 204 (:status undelete-res)))
             _ (is (or (nil? (:body undelete-res))
                       (empty? (:body undelete-res))))
             after-undelete-list (list-service-accounts)
             _ (is (= 200 (:status after-undelete-list)))
             after-undelete-accounts (http/res->body after-undelete-list)
             _ (is (= 1 (count (get after-undelete-accounts "accounts"))))]
            test/refute-anomaly))))))
