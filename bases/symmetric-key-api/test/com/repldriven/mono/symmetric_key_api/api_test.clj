(ns com.repldriven.mono.symmetric-key-api.api-test
  (:require
    com.repldriven.mono.server.interface

    [com.repldriven.mono.symmetric-key-api.api :as api]

    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]

    [clojure.test :refer [deftest is testing]]))

(deftest api-test
  (testing "API should respond to symmetric key requests"
    (let [sys (error/nom->
               (env/config "classpath:symmetric-key-api/test-application.yml"
                           :test)
               system/defs
               (assoc-in [:system/defs :server :handler] (partial api/app))
               system/start)]
      (is (not (error/anomaly? sys)) "System should start")
      (is (system/system? sys) "System should be valid")
      (when (system/system? sys)
        (system/with-system sys
          (let [jetty (system/instance sys [:server :jetty-adapter])
                base-url (server/http-local-url jetty)
                identity-id "test-identity-123"
                key-id "test-key-456"]
            ;; Test list keys endpoint
            (let [list-request {:method :get
                                :url (str base-url "/api/identities/" identity-id
                                          "/keys")
                                :headers {"Accept" "application/json"}}
                  list-result (error/let-nom [response (http/request
                                                        list-request)
                                              body (http/res->edn response)]
                                {:status (:status response) :body body})]
              (is (not (error/anomaly? list-result))
                  (str "List keys request failed: " (pr-str list-result)))
              (when-not (error/anomaly? list-result)
                (is (= 200 (:status list-result)) "Should return 200 OK")
                (is (= {:data []} (:body list-result))
                    "Should return empty list of keys")))
            ;; Test get key endpoint
            (let [get-request {:method :get
                               :url (str base-url "/api/identities/" identity-id
                                         "/keys/" key-id)
                               :headers {"Accept" "application/json"}}
                  get-result (error/let-nom [response (http/request get-request)
                                             body (http/res->edn response)]
                               {:status (:status response) :body body})]
              (is (not (error/anomaly? get-result))
                  (str "Get key request failed: " (pr-str get-result)))
              (when-not (error/anomaly? get-result)
                (is (= 200 (:status get-result)) "Should return 200 OK")
                (is (= {:data {}} (:body get-result))
                    "Should return empty key data")))))))))
