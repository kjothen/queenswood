(ns com.repldriven.mono.testcontainers.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]

    [clojure.test :as test :refer [deftest is testing]]))

(deftest testcontainers-test
  (testing "Testcontainers should start and provide mapped ports"
    (let [sys (error/nom->
               (env/config "classpath:testcontainers/test-application.yml" :test)
               system/defs
               system/start)]
      (is (not (error/anomaly? sys)) (str "System should start: " (pr-str sys)))
      (when (system/system? sys)
        (system/with-system sys
          (is (= [8080 8081]
                 (keys (system/instance sys [:helloworld
                                             :container-mapped-ports]))))
          (is (= (system/instance sys [:helloworld
                                       :container-mapped-exposed-port])
                 (get (system/instance sys [:helloworld
                                            :container-mapped-ports])
                      8080))))))))
