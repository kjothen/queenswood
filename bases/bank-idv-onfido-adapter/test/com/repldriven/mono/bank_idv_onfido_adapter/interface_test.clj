(ns ^:eftest/synchronized
    com.repldriven.mono.bank-idv-onfido-adapter.interface-test
  (:require
    [com.repldriven.mono.bank-idv-onfido-adapter.api :as api]

    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing]]))

(def ^:dynamic *base-url* "http://localhost:{PORT}")

(defn- post
  [path body]
  (http/request {:method :post
                 :url (str *base-url* path)
                 :headers {"Content-Type" "application/json"}
                 :body (json/write-str body)}))

(deftest check-completed-test
  (with-test-system
   [sys
    ["classpath:bank-idv-onfido-adapter/application-test.yml"
     #(assoc-in % [:system/defs :server :handler] api/app)]]
   (let [jetty (system/instance sys [:server :jetty-adapter])]
     (binding [*base-url* (server/http-local-url jetty)]
       (testing "POST /webhooks/onfido/check-completed acknowledges 200"
         (nom-test> [res (post "/webhooks/onfido/check-completed"
                               {:payload {:resource_type "check"
                                          :action "check.completed"
                                          :object {:id "ch.test-001"
                                                   :status "complete"
                                                   :result "clear"
                                                   :completed_at_iso8601
                                                   "2026-05-02T12:00:00Z"
                                                   :external_id
                                                   "iv.test-001"}}})
                     _ (is (= 200 (:status res)))
                     body (http/res->edn res)
                     _ (is (true? (:received body)))]))))))
