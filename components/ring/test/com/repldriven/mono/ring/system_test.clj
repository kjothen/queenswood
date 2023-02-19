(ns com.repldriven.mono.ring.system-test
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [com.repldriven.mono.ring.system.core :as SUT]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.system.interface :as system :refer
             [with-system]]
            [muuntaja.core]
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
  (testing
   "Ring interceptors MUST be inserted"
   (let [data {:got "me" :this "time"}
         handler (fn [req] {:status 200 :body (select-keys req (keys data))})
         routes (fn [ctx] ["/api" {:interceptors (:interceptors ctx)}
                           ["/interceptors" {:get {:handler handler}}]])
         router-data {:muuntaja muuntaja.core/instance
                      :interceptors [(muuntaja/format-response-interceptor)
                                     (coercion/coerce-response-interceptor)]}
         app (fn [ctx]
               (http/ring-handler (http/router (routes ctx) {:data router-data})
                                  (ring/create-default-handler)
                                  {:executor sieppari/executor}))
         system-config (-> @env/env
                           (assoc-in [:system :ring :jetty-adapter :handler]
                                     app)
                           (assoc-in [:system :ring :interceptors] data))]
     (with-system
      [sys (SUT/configure (get-in system-config [:system :ring]))]
      (let [exposed-port (get-in system-config
                                 [:system :ring :jetty-adapter :options :port])
            url (str "http://localhost:" exposed-port "/api/interceptors")
            res @(httpkit/get url)]
        (is (= (json/read-str (:body res) :key-fn keyword) data)))))))
