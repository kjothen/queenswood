(ns com.repldriven.mono.testcontainers.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]

    [clojure.test :as test :refer [deftest is testing]]))

(defn- test-system
  []
  (error/nom-> (env/config "classpath:testcontainers/test-application.yml"
                           :test)
               system/defs
               system/start))

(deftest testcontainers-test
  (testing "Testcontainers should start and provide mapped ports"
    (system/with-system [sys (test-system)]
      (is (= [8080 8081]
             (keys (system/instance sys
                                    [:helloworld :container-mapped-ports]))))
      (is (= (system/instance sys [:helloworld :container-mapped-exposed-port])
             (get (system/instance sys [:helloworld :container-mapped-ports])
                  8080))))))
