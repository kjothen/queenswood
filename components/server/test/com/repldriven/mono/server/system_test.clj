(ns com.repldriven.mono.server.system-test
  (:require
   [com.repldriven.mono.server.interface :as server]

   [com.repldriven.mono.http-client.interface :as http-client]
   [com.repldriven.mono.system.interface :as system]
   [com.repldriven.mono.test-system.interface :as test-system]

   [reitit.http :as http]
   [reitit.ring :as ring]

   [clojure.test :refer [deftest is testing use-fixtures]]
   [clojure.walk :as walk]))

(use-fixtures :once
  (test-system/fixture "classpath:com/repldriven/mono/server/application-test.yml" :test))

(deftest system-test
  (testing "System configuration and lifecycle"
    (is (system/system? test-system/*sysdef*))
    (let [sys-config (update-in test-system/*sysdef* [:system/defs :server] dissoc :jetty-adapter)]
      (system/with-*sys* sys-config
        (is (some? system/*sys*))))))

(deftest interceptors-test
  (testing
   "Ring interceptors MUST be inserted"
    (let [data {:got "me" :this "time"}
          handler
          (fn [req] {:status 200 :body (select-keys req (keys data))})
          routes (fn [ctx] ["/api" {:interceptors (:interceptors ctx)}
                            ["/interceptors" {:get {:handler handler}}]])
          app (fn [ctx]
                (http/ring-handler (http/router (routes ctx)
                                                server/standard-router-data)
                                   (ring/create-default-handler)
                                   server/standard-executor))
          sys-config (assoc-in test-system/*sysdef* [:system/defs :server :handler] app)]
      (system/with-*sys* sys-config
        (let [server (system/instance system/*sys* [:server :jetty-adapter])
              port (.getLocalPort (first (.getConnectors server)))
              url (str "http://localhost:" port "/api/interceptors")
              res (http-client/request {:url url :method :get})
              body (walk/keywordize-keys (http-client/res->body res))]
          (is (= body data)))))))
