(ns com.repldriven.mono.blocking-command-api.api-test
  (:require
    com.repldriven.mono.mqtt.interface
    com.repldriven.mono.pulsar.interface
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.blocking-command-api.api :as api]

    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]

    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing]]))

(def ^:dynamic *base-url* "http://localhost:{PORT}")

(defn send-command
  [command-data]
  (http/request {:method :post
                 :url (str *base-url* "/api/command")
                 :headers {"Content-Type" "application/json"}
                 :body (json/write-str {:data command-data})}))

(deftest blocking-command-api
  (testing "blocking command API"
    (let [sys (error/nom->
               (env/config "classpath:blocking-command-api/application-test.yml"
                           :test)
               system/defs
               (assoc-in [:system/defs :server :handler] (partial api/app))
               system/start)]
      (is (not (error/anomaly? sys)) (str "System should start: " (pr-str sys)))
      (when (system/system? sys)
        (system/with-system sys
          (let [jetty (system/instance sys [:server :jetty-adapter])]
            (binding [*base-url* (server/http-local-url jetty)]
              (let [result
                    (error/let-nom
                      ; send test command
                      [res (send-command {:type "test-command" :id "123"})
                       _ (is (= 200 (:status res)))
                       body (http/res->edn res)
                       _ (is (= {:data {:result "test-command/123"}} body))]
                      :success)]
                (is (not (error/anomaly? result))
                    (str "API workflow failed: " (pr-str result)))))))))))
