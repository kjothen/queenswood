(ns com.repldriven.mono.server.system-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clojure.walk :as walk]
   [com.repldriven.mono.http-client.interface :as http-client]
   [com.repldriven.mono.server.interface]
   [com.repldriven.mono.system.interface :as system]
   [com.repldriven.mono.test-system.interface :as test-system]
   [muuntaja.core]
   [reitit.ring :as ring]
   [reitit.http :as http]
   [reitit.http.coercion :as coercion]
   [reitit.http.interceptors.muuntaja :as muuntaja]
   [reitit.interceptor.sieppari :as sieppari]
   [ring.adapter.jetty9]))

(use-fixtures :once
  (test-system/fixture "classpath:server/application-test.yml" :test))

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
          router-data {:muuntaja muuntaja.core/instance
                       :interceptors [(muuntaja/format-response-interceptor)
                                      (coercion/coerce-response-interceptor)]}
          app (fn [ctx]
                (http/ring-handler (http/router (routes ctx) {:data router-data})
                                   (ring/create-default-handler)
                                   {:executor sieppari/executor}))
          sys-config (assoc-in test-system/*sysdef* [:system/defs :server :handler] app)]
      (system/with-*sys* sys-config
        (let [server (system/instance system/*sys* [:server :jetty-adapter])
              port (.getLocalPort (first (.getConnectors server)))
              url (str "http://localhost:" port "/api/interceptors")
              res (http-client/request {:url url :method :get})
              body (walk/keywordize-keys (http-client/res->body res))]
          (is (= body data)))))))
