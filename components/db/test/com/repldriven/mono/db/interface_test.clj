(ns com.repldriven.mono.db.interface-test
  (:require
    com.repldriven.mono.db.interface
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.system.interface :as system]

    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]

    [clojure.test :as test :refer [deftest is testing]]))

(deftest db-component-test
  (testing "DB component should provide valid connections"
    (let [sys (error/nom-> (env/config "classpath:db/test-application.yml"
                                       :test)
                           system/defs
                           system/start)]
      (is (not (error/anomaly? sys)) (str "System should start: " (pr-str sys)))
      (when (system/system? sys)
        (system/with-system sys
          (let [datasource (system/instance sys [:db :datasource])]
            (is (= [{:?column? 1}]
                   (jdbc/execute! datasource
                                  ["select 1"]
                                  {:builder-fn
                                   rs/as-unqualified-lower-maps})))))))))
