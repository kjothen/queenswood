(ns com.repldriven.mono.bank-api.api-test
  (:require
    [com.repldriven.mono.bank-api.api :as api]
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.message-bus.interface :as message-bus]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [com.repldriven.mono.utility.interface :as util]
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing]]))

(def ^:dynamic *base-url* "http://localhost:{PORT}")

(def ^:private test-account-id "acc.01kprbmgcj35ptc8npmybhh4s8")
(def ^:private test-party-id "pty.01kprbmgcj35ptc8npmybhh4s9")

(def ^:private test-account-open
  {:organization-id "org.01kprbmgcj35ptc8npmybhh4s7"
   :account-id test-account-id
   :party-id test-party-id
   :name "Test Account"
   :currency "GBP"
   :product-id "prd.01kprbmgcj35ptc8npmybhh4se"
   :version-id "prv.01kprbmgcj35ptc8npmybhh4sf"
   :product-type :product-type-current
   :account-type :account-type-personal
   :payment-addresses []
   :balances []
   :account-status :cash-account-status-opened
   :created-at 1700000000000
   :updated-at 1700000000000})

(def ^:private test-account-close
  (assoc test-account-open :account-status :cash-account-status-closing))

(defn- command-processor
  "Simulates Processor — subscribes to cash-accounts-command
  messages and replies with a fixed account payload."
  [sys account]
  (let [bus (system/instance sys [:message-bus :bus])
        schemas (system/instance sys [:avro :serde])
        payload (avro/serialize (get schemas "cash-account") account)]
    (message-bus/subscribe bus
                           :cash-accounts-command
                           (fn [data]
                             (message-bus/send bus
                                               :cash-accounts-command-response
                                               (command/command-response
                                                data
                                                {:status "ACCEPTED"
                                                 :payload payload}))))
    {:stop (fn [] (message-bus/unsubscribe bus :cash-accounts-command))}))

(defn- open-account-request
  [party-id name currency product-id]
  (http/request {:method :post
                 :url (str *base-url* "/v1/cash-accounts")
                 :headers {"Content-Type" "application/json"
                           "Idempotency-Key" (str (util/uuidv7))}
                 :body (json/write-str {"party-id" party-id
                                        "name" name
                                        "currency" currency
                                        "product-id" product-id})}))

(defn- close-account-request
  [account-id]
  (http/request {:method :post
                 :url (str *base-url* "/v1/cash-accounts/" account-id "/close")
                 :headers {"Idempotency-Key" (str (util/uuidv7))}}))

(defn- test-open-account
  [sys]
  (let [{:keys [stop]} (command-processor sys test-account-open)]
    (nom-test>
      [res (open-account-request test-party-id
                                 "Test Account"
                                 "GBP"
                                 "prd.01kprbmgcj35ptc8npmybhh4se")
       _ (is (= 200 (:status res)))
       body (http/res->edn res)
       _ (is (= {:account-id test-account-id
                 :party-id test-party-id
                 :name "Test Account"
                 :currency "GBP"
                 :account-status "opened"}
                (select-keys body
                             [:account-id :party-id :name :currency
                              :account-status])))])
    (stop)))

(defn- test-close-account
  [sys]
  (let [{:keys [stop]} (command-processor sys test-account-close)]
    (nom-test> [res (close-account-request test-account-id)
                _ (is (= 200 (:status res)))
                body (http/res->edn res)
                _ (is (= {:account-id test-account-id
                          :party-id test-party-id
                          :name "Test Account"
                          :currency "GBP"
                          :account-status "closing"}
                         (select-keys body
                                      [:account-id :party-id :name :currency
                                       :account-status])))])
    (stop)))

(defn- test-open-api-spec
  []
  (nom-test>
    [res (http/request {:method :get :url (str *base-url* "/openapi.json")})
     _ (is (= 200 (:status res)))
     spec (http/res->edn res)
     _ (is (= "3.1.0" (:openapi spec)))]))

(deftest account-commands-test
  (with-test-system
   [sys
    ["classpath:bank-api/application-test.yml"
     #(assoc-in % [:system/defs :server :handler] api/app)]]
   (let [jetty (system/instance sys [:server :jetty-adapter])]
     (binding [*base-url* (server/http-local-url jetty)]
       (testing "GET /openapi.json returns valid OpenAPI spec"
         (test-open-api-spec))
       (testing "POST /v1/cash-accounts sends open-cash-account command"
         (test-open-account sys))
       (testing
         "POST /v1/cash-accounts/{id}/close sends close-cash-account command"
         (test-close-account sys))))))
