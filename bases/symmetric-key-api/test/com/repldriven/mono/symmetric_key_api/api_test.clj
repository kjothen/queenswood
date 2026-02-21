(ns com.repldriven.mono.symmetric-key-api.api-test
  (:require
    [com.repldriven.mono.symmetric-key-api.api :as api]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer [with-test-system]]

    [clojure.test :refer [deftest is testing]]))

(def ^:dynamic *base-url* "http://localhost:{PORT}")

(defn list-keys
  [identity-id]
  (http/request {:url (str *base-url* "/api/identities/" identity-id "/keys")
                 :method :get}))

(defn get-key
  [identity-id key-id]
  (http/request {:url
                 (str *base-url* "/api/identities/" identity-id "/keys/" key-id)
                 :method :get}))

(deftest symmetric-keys-api
  (testing "symmetric keys API"
    (with-test-system
     [sys
      ["classpath:symmetric-key-api/application-test.yml"
       #(assoc-in % [:system/defs :server :handler] api/app)]]
     (let [jetty (system/instance sys [:server :jetty-adapter])]
       (binding [*base-url* (server/http-local-url jetty)]
         (let [identity-id "test-identity-123"
               key-id "test-key-456"
               result (error/let-nom
                        ; list keys for identity
                        [list-res (list-keys identity-id)
                         _ (is (= 200 (:status list-res)))
                         list-body (http/res->body list-res)
                         _ (is (= {"data" []} list-body))
                         ; get specific key
                         get-res (get-key identity-id key-id)
                         _ (is (= 200 (:status get-res)))
                         get-body (http/res->body get-res)
                         _ (is (= {"data" {}} get-body))]
                        :success)]
           (is (not (error/anomaly? result))
               (str "API workflow failed: " (pr-str result)))))))))
