(ns com.repldriven.mono.iam-api.system-test
  (:require
   com.repldriven.mono.db.interface
   com.repldriven.mono.server.interface
   com.repldriven.mono.testcontainers.interface

   [com.repldriven.mono.system.interface :as system]
   [com.repldriven.mono.test-system.interface :as test-system]

   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once
  (test-system/fixture "classpath:iam-api/test-application.yml" :test))

(defn minimal-app
  [_ctx]
  (fn [_request]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body "OK"}))

(deftest system-test
  (testing "System configuration and lifecycle"
    (is (system/system? test-system/*sysdef*))
    (let [sys-config (assoc-in test-system/*sysdef* [:system/defs :server :jetty-adapter :system/config :handler] minimal-app)]
      (system/with-*sys* sys-config
        (is (some? system/*sys*))))))

