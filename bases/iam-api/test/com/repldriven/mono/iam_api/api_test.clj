(ns com.repldriven.mono.iam-api.api-test
  (:refer-clojure :exclude [name])
  (:require [clojure.test :as test :refer [deftest is testing use-fixtures]]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.log.interface :as log]
            [com.repldriven.mono.iam-api.main :as main]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [org.httpkit.client :as http]))

;;;; Fixtures
;;;;

(def ^:dynamic *base-url* "http://localhost:{PORT}")
(def project-id "prj-test")

(defn start-system
  []
  (main/-main "-c" (io/as-file (io/resource "iam-api/test-application.yml"))
              "-p" "test"))
(defn stop-system [] (main/stop!))
(defn system-fixture
  [f]
  (start-system)
  (let [port (get-in @env/env [:system :ring :jetty-adapter :options :port])]
    (binding [*base-url* (str "http://localhost:" port)] (f)))
  (stop-system))

(use-fixtures :once system-fixture)

;;;; Test helpers
;;;;

(defn json->edn [res] (json/read-str (:body res) {:key-fn keyword}))

;;;; service-account
;;;;

(def service-account-create-body
  {:account-id "sa-test"
   :service-account {:display-name "sa-test-zzz-d-shared"
                     :description "Test service account for all dev projects"}})

(defn create-service-account
  []
  @(http/post (str *base-url* "/v1/projects/" project-id "/serviceAccounts")
              {:headers {"Content-Type" "application/json"}
               :body (json/write-str service-account-create-body)}))

(defn list-service-accounts
  []
  @(http/get (str *base-url* "/v1/projects/" project-id "/serviceAccounts")))

(defn get-service-account [name] @(http/get (str *base-url* "/v1/" name)))

(defn disable-service-account
  [name]
  @(http/post (str *base-url* "/v1/" name ":disable")))

(defn enable-service-account
  [name]
  @(http/post (str *base-url* "/v1/" name ":enable")))

(defn delete-service-account [name] @(http/delete (str *base-url* "/v1/" name)))

(defn undelete-service-account
  [name]
  @(http/post (str *base-url* "/v1/" name ":undelete")))

(deftest service-accounts-api
  (testing "serviceAccounts API"
           ; create service account
           (let [res (create-service-account)
                 service-account (json->edn res)]
             (is (= 201 (:status res)))
             (is (= false (:disabled service-account)))
             ; get created service account
             (let [res (get-service-account (:name service-account))]
               (is (= 200 (:status res)))
               (is (= service-account (json->edn res))))
             ; list service accounts and check for created service account
             (let [res (list-service-accounts)
                   service-accounts (json->edn res)]
               (is (= 200 (:status res)))
               (is (= 1 (count (:accounts service-accounts))))
               (is (= service-account (first (:accounts (json->edn res))))))
             ; disable created service account and check it's disabled
             (let [res (disable-service-account (:name service-account))]
               (is (= 204 (:status res)))
               (is (or (nil? (:body res)) (empty? (:body res))))
               (let [res (get-service-account (:name service-account))]
                 (is (= 200 (:status res)))
                 (is (= true (:disabled (json->edn res))))))
             ; enable created service account and check it's enabled
             (let [res (enable-service-account (:name service-account))]
               (is (= 204 (:status res)))
               (is (or (nil? (:body res)) (empty? (:body res))))
               (let [res (get-service-account (:name service-account))]
                 (is (= 200 (:status res)))
                 (is (= false (:disabled (json->edn res))))))
             ; delete created service account and check it's not listed
             (let [res (delete-service-account (:name service-account))]
               (is (= 204 (:status res)))
               (is (or (nil? (:body res)) (empty? (:body res))))
               (let [res (list-service-accounts)
                     service-accounts (json->edn res)]
                 (is (= 200 (:status res)))
                 (is (zero? (count (:accounts service-accounts))))))
             ; undelete created service account and check it's listed
             (let [res (undelete-service-account (:name service-account))]
               (is (= 204 (:status res)))
               (is (or (nil? (:body res)) (empty? (:body res))))
               (let [res (list-service-accounts)
                     service-accounts (json->edn res)]
                 (is (= 200 (:status res)))
                 (is (= 1 (count (:accounts service-accounts)))))))))
