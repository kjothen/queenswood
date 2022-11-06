(ns com.repldriven.mono.ring.system-test
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [com.repldriven.mono.ring.system.core :as SUT]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.system.interface :as system
             :refer [with-system]]
            [muuntaja.core :as m]
            [reitit.ring :as ring]
            [reitit.http :as http]
            [reitit.http.coercion :as coercion]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.interceptor.sieppari :as sieppari]
            [org.httpkit.client :as httpkit]))

(defn env-fixture
  [f]
  (env/set-env! (io/resource "ring/test-env.edn") :test)
  (f))

(deftest system-config-test
  (testing "System configuration MUST be valid"
    (is (= true (system/system? (SUT/configure (:system @env/env)))))))

(deftest interceptors-test
  (testing "Ring interceptors MUST be inserted"
    (let [test-data {:got "me" :this "time"}
          app
          (fn [ctx]
            (http/ring-handler
              (http/router
                ["/api"
                 {:interceptors (:interceptors ctx)}
                 ["/interceptors"
                  {:get {:handler
                         (fn [req]
                           {:status 200
                            :body (select-keys req (keys test-data))})}}]]
                {:data
                 {:muuntaja m/instance
                  :interceptors [(muuntaja/format-response-interceptor)
                                 (coercion/coerce-response-interceptor)]}})
              (ring/create-default-handler)
              {:executor sieppari/executor}))
          system-config
          (-> @env/env
            (assoc-in [:system :ring :jetty-adapter :handler] app)
            (assoc-in [:system :ring :interceptors] test-data))]
      (with-system [sys (SUT/configure (get-in system-config [:system :ring]))]
        (let [exposed-port (get-in (system/config sys :ring :jetty-adapter)
                             [:options :port])
              url (str "http://localhost:" exposed-port "/api/interceptors")
              res @(httpkit/get url)]
          (is (= (json/read-str (:body res) :key-fn keyword)
                test-data)))))))
