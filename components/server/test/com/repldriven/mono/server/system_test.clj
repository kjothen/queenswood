(ns com.repldriven.mono.server.system-test
  (:require
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http-client]
    [com.repldriven.mono.server.interface :as server]
    [com.repldriven.mono.system.interface :as system]

    [reitit.http :as http]
    [reitit.ring :as ring]

    [clojure.test :refer [deftest is testing]]
    [clojure.walk :as walk]))

(deftest server-component-test
  (testing "Server component system configuration and lifecycle"
    (let [sys (error/nom->
               (env/config
                "classpath:com/repldriven/mono/server/application-test.yml"
                :test)
               system/defs
               (update-in [:system/defs :server] dissoc :jetty-adapter)
               system/start)]
      (is (not (error/anomaly? sys)) (str "System should start: " (pr-str sys)))
      (when (system/system? sys) (system/with-system sys (is (some? sys)))))))

(deftest interceptors-test
  (testing "Ring interceptors MUST be inserted"
    (let [data {:got "me" :this "time"}
          handler (fn [req] {:status 200 :body (select-keys req (keys data))})
          routes (fn [ctx] ["/api" {:interceptors (:interceptors ctx)}
                            ["/interceptors" {:get {:handler handler}}]])
          app (fn [ctx]
                (http/ring-handler (http/router (routes ctx)
                                                server/standard-router-data)
                                   (ring/create-default-handler)
                                   server/standard-executor))
          sys (error/nom->
               (env/config
                "classpath:com/repldriven/mono/server/application-test.yml"
                :test)
               system/defs
               (assoc-in [:system/defs :server :handler] app)
               system/start)]
      (is (not (error/anomaly? sys)) (str "System should start: " (pr-str sys)))
      (when (system/system? sys)
        (system/with-system sys
          (let [jetty (system/instance sys [:server :jetty-adapter])
                base-url (server/http-local-url jetty)
                url (str base-url "/api/interceptors")
                res (http-client/request {:url url :method :get})
                body (walk/keywordize-keys (http-client/res->body res))]
            (is (= body data))))))))
