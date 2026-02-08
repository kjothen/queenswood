(ns com.repldriven.mono.iam-api.api-test
  (:refer-clojure :exclude [name])
  (:require
   com.repldriven.mono.testcontainers.interface

   [com.repldriven.mono.iam-api.api :as api]

   [com.repldriven.mono.db.interface :as db]
   [com.repldriven.mono.http-client.interface :as http]
   [com.repldriven.mono.iam.interface :as iam]
   [com.repldriven.mono.system.interface :as system]
   [com.repldriven.mono.test-system.interface :as test-system]

   [clojure.data.json :as json]
   [clojure.test :as test :refer [deftest is testing use-fixtures]])

  (:import
   (org.eclipse.jetty.server Server)))

;;;; Fixtures
;;;;

(def ^:dynamic *base-url* "http://localhost:{PORT}")
(def project-id "prj-test")

(defn db-spec
  [sys]
  (let [datasource (system/instance sys [:db :datasource])]
    (db/get-datasource datasource)))

(use-fixtures :once
  (test-system/fixture "classpath:iam-api/test-application.yml" :test))

;;;; service-account
;;;;

(def service-account-create-body
  {:account-id "sa-test"
   :service-account {:display-name "sa-test-zzz-d-shared"
                     :description "Test service account for all dev projects"}})

(defn create-service-account
  []
  (http/request {:url (str *base-url* "/v1/projects/" project-id "/serviceAccounts")
                 :method :post
                 :headers {"Content-Type" "application/json"}
                 :body (json/write-str service-account-create-body)}))

(defn list-service-accounts
  []
  (http/request {:url (str *base-url* "/v1/projects/" project-id "/serviceAccounts")
                 :method :get}))

(defn get-service-account
  [name]
  (http/request {:url (str *base-url* "/v1/" name)
                 :method :get}))

(defn disable-service-account
  [name]
  (http/request {:url (str *base-url* "/v1/" name ":disable")
                 :method :post}))

(defn enable-service-account
  [name]
  (http/request {:url (str *base-url* "/v1/" name ":enable")
                 :method :post}))

(defn delete-service-account
  [name]
  (http/request {:url (str *base-url* "/v1/" name)
                 :method :delete}))

(defn undelete-service-account
  [name]
  (http/request {:url (str *base-url* "/v1/" name ":undelete")
                 :method :post}))

(deftest service-accounts-api
  (testing "serviceAccounts API"
    (let [sys-config (assoc-in test-system/*sysdef* [:system/defs :server :handler] (partial api/app))]
      (system/with-*sys* sys-config
        (iam/migrate (db-spec system/*sys*))
        (let [^Server server (system/instance system/*sys* [:server :jetty-adapter])
              port (.getLocalPort (first (.getConnectors server)))]
          (binding [*base-url* (str "http://localhost:" port)]
            ; create service account
            (let [res (create-service-account)
                  service-account (http/res->edn res)]
              (is (= 201 (:status res)))
              (is (= false (:disabled service-account)))
             ; get created service account
              (let [res (get-service-account (:name service-account))]
                (is (= 200 (:status res)))
                (is (= service-account (http/res->edn res))))
             ; list service accounts and check for created service account
              (let [res (list-service-accounts)
                    service-accounts (http/res->edn res)]
                (is (= 200 (:status res)))
                (is (= 1 (count (:accounts service-accounts))))
                (is (= service-account (first (:accounts (http/res->edn res))))))
             ; disable created service account and check it's disabled
              (let [res (disable-service-account (:name service-account))]
                (is (= 204 (:status res)))
                (is (or (nil? (:body res)) (empty? (:body res))))
                (let [res (get-service-account (:name service-account))]
                  (is (= 200 (:status res)))
                  (is (= true (:disabled (http/res->edn res))))))
             ; enable created service account and check it's enabled
              (let [res (enable-service-account (:name service-account))]
                (is (= 204 (:status res)))
                (is (or (nil? (:body res)) (empty? (:body res))))
                (let [res (get-service-account (:name service-account))]
                  (is (= 200 (:status res)))
                  (is (= false (:disabled (http/res->edn res))))))
             ; delete created service account and check it's not listed
              (let [res (delete-service-account (:name service-account))]
                (is (= 204 (:status res)))
                (is (or (nil? (:body res)) (empty? (:body res))))
                (let [res (list-service-accounts)
                      service-accounts (http/res->edn res)]
                  (is (= 200 (:status res)))
                  (is (zero? (count (:accounts service-accounts))))))
             ; undelete created service account and check it's listed
              (let [res (undelete-service-account (:name service-account))]
                (is (= 204 (:status res)))
                (is (or (nil? (:body res)) (empty? (:body res))))
                (let [res (list-service-accounts)
                      service-accounts (http/res->edn res)]
                  (is (= 200 (:status res)))
                  (is (= 1 (count (:accounts service-accounts)))))))))))))
