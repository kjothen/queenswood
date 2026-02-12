(ns com.repldriven.mono.iam-api.system-test
  (:require
   com.repldriven.mono.db.interface
   com.repldriven.mono.server.interface
   com.repldriven.mono.testcontainers.interface

   [com.repldriven.mono.env.interface :as env]
   [com.repldriven.mono.error.interface :as error]
   [com.repldriven.mono.system.interface :as system]

   [clojure.test :refer [deftest is testing]]))

(defn minimal-app
  [_ctx]
  (fn [_request]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body "OK"}))

(deftest system-test
  (testing "System configuration and lifecycle"
    (let [sys (error/nom-> (env/config "classpath:iam-api/test-application.yml" :test)
                           system/definition
                           (assoc-in [:system/defs :server :handler] minimal-app)
                           system/start)]
      (is (not (error/anomaly? sys)) "System should start")
      (is (system/system? sys) "System should be valid")
      (when (system/system? sys)
        (system/with-system sys
          (is (some? (system/instance sys [:server :jetty-adapter]))
              "Server should be running"))
        (is (not (error/anomaly? (system/stop sys)))
            "System should stop cleanly")))))

