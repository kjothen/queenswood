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

(def ^:dynamic *base-url* "http://localhost:{PORT}")

(defn list-keys
  [identity-id]
  (http/request {:url (str *base-url* "/api/identities/" identity-id "/keys")
                 :method :get}))

(defn get-key
  [identity-id key-id]
  (http/request {:url (str *base-url* "/api/identities/" identity-id "/keys/"
                           key-id)
                 :method :get}))

(deftest symmetric-keys-api
  (testing "symmetric keys API"
    (let [sys (error/nom->
               (env/config "classpath:symmetric-key-api/test-application.yml"
                           :test)
               system/defs
               (assoc-in [:system/defs :server :handler] (partial api/app))
               system/start)]
      (is (not (error/anomaly? sys)) (str "System should start: " (pr-str sys)))
      (when (system/system? sys)
        (system/with-system sys
          (let [jetty (system/instance sys [:server :jetty-adapter])]
            (binding [*base-url* (server/http-local-url jetty)]
              (let [identity-id "test-identity-123"
                    key-id "test-key-456"
                    result
                    (error/let-nom
                      ; list keys for identity
                      [list-res (list-keys identity-id)
                       _ (is (= 200 (:status list-res)))
                       list-body (http/res->edn list-res)
                       _ (is (= {:data []} list-body))
                       ; get specific key
                       get-res (get-key identity-id key-id)
                       _ (is (= 200 (:status get-res)))
                       get-body (http/res->edn get-res)
                       _ (is (= {:data {}} get-body))]
                      :success)]
                (is (not (error/anomaly? result))
                    (str "API workflow failed: " (pr-str result)))))))))))
