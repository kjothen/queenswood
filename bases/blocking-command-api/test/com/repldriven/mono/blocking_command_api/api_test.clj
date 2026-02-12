(ns com.repldriven.mono.blocking-command-api.api-test
  (:require
    com.repldriven.mono.mqtt.interface
    com.repldriven.mono.pulsar.interface
    com.repldriven.mono.server.interface
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.blocking-command-api.api :as api]
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.system.interface :as system]

    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing]]))

(deftest api-test
  (testing "API should accept and process commands"
    (let [sys (error/nom->
               (env/config "classpath:blocking-command-api/application-test.yml"
                           :test)
               :system
               system/definition
               (assoc-in [:system/defs :server :handler] (partial api/app))
               system/start)]
      (is (not (error/anomaly? sys)) "System should start")
      (is (system/system? sys) "System should be valid")
      (when (system/system? sys)
        (system/with-system sys
          (let [jetty (system/instance sys [:server :jetty-adapter])
                port (some-> jetty
                             .getConnectors
                             first
                             .getPort)
                base-url (str "http://localhost:" port)
                request {:method :post
                         :url (str base-url "/api/command")
                         :headers {"Content-Type" "application/json"}
                         :body (json/write-str {:data {:type "test-command"
                                                       :id "123"}})}
                result (error/let-nom [response (http/request request)
                                       body (http/res->edn response)]
                         {:status (:status response) :body body})]
            (is (not (error/anomaly? result))
                (str "HTTP request failed: " (pr-str result)))
            (when-not (error/anomaly? result)
              (is (= 200 (:status result)) "Should return 200 OK")
              (is (= {:data {:result "test-command/123"}} (:body result))
                  "Should return expected result"))))))))
