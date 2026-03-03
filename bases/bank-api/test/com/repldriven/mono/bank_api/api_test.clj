(ns com.repldriven.mono.bank-api.api-test
  (:require
    com.repldriven.mono.avro.interface

    [com.repldriven.mono.bank-api.api :as api]

    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.telemetry.interface :as telemetry]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [com.repldriven.mono.utility.interface :as util]

    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing]]))

(def ^:dynamic *base-url* "http://localhost:{PORT}")

(defn- open-account-request
  [customer-id name currency]
  (http/request {:method :post
                 :url (str *base-url* "/v1/accounts")
                 :headers {"Content-Type" "application/json"
                           "Idempotency-Key" (str (util/uuidv7))}
                 :body (json/write-str {"customer-id" customer-id
                                        "name" name
                                        "currency" currency})}))

(defn- command-processor
  "Simulates Processor — receives command envelopes and
  replies via message-bus"
  [sys]
  (let [bus (system/instance sys [:message-bus :bus])]
    (message-bus/subscribe
     bus
     :command
     (fn [data]
       (let [parent-ctx (telemetry/extract-parent-context data)]
         (telemetry/with-span-parent
          "process-command"
          parent-ctx
          (select-keys data [:id :command :correlation-id :causation-id])
          (fn []
            (message-bus/send bus
                              :command-response
                              (command/command-response data
                                                        {:status "ACCEPTED"
                                                         :payload nil})))))))
    {:stop (fn [] (message-bus/unsubscribe bus :command))}))

(deftest open-account-test
  (testing "POST /v1/accounts sends open-account command"
    (with-test-system
     [sys
      ["classpath:bank-api/application-test.yml"
       #(assoc-in % [:system/defs :server :handler] api/app)]]
     (let [jetty (system/instance sys [:server :jetty-adapter])
           {:keys [stop]} (command-processor sys)]
       (binding [*base-url* (server/http-local-url jetty)]
         (telemetry/with-span-tests
          [_ ["process-command"]]
          (nom-test> [res (open-account-request "acc-test" "Test Account" "GBP")
                      _ (is (= 200 (:status res)))
                      actual (http/res->body res)
                      _ (is (= "ACCEPTED" (get actual "status")))])))
       (stop)))))

(deftest openapi-spec-test
  (testing "GET /openapi.json returns valid OpenAPI spec"
    (with-test-system
     [sys
      ["classpath:bank-api/application-test.yml"
       #(assoc-in % [:system/defs :server :handler] api/app)]]
     (let [jetty (system/instance sys [:server :jetty-adapter])]
       (binding [*base-url* (server/http-local-url jetty)]
         (nom-test> [res (http/request {:method :get
                                        :url (str *base-url* "/openapi.json")})
                     _ (is (= 200 (:status res)))
                     spec (http/res->body res)
                     _ (is (= "3.1.0" (get spec "openapi")))
                     _ (is (= "Bank API" (get-in spec ["info" "title"])))
                     _ (is (some? (get-in spec
                                          ["components"
                                           "securitySchemes"
                                           "adminAuth"])))
                     _ (is (some? (get-in spec
                                          ["components"
                                           "securitySchemes"
                                           "orgAuth"])))
                     paths (get spec "paths")
                     _ (is (some? (get paths "/v1/accounts")))
                     _ (is (some? (get paths
                                       "/v1/accounts/{account-id}/close")))
                     _ (is (some? (get paths "/v1/organizations")))]))))))
