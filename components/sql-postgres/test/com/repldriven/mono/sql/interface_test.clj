(ns com.repldriven.mono.sql.interface-test
  (:require [clojure.test :as test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.sql.interface :as SUT]
            [com.repldriven.mono.system.interface :as system]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defn sys-fixture
  [f]
  (env/set-env! (io/resource "sql/test-env.edn") :test)
  (system/with-*sys* (SUT/configure-system (get-in @env/env [:system :sql]))
    (f)))

(use-fixtures :once sys-fixture)

(deftest system-start
  (testing "Developers should be able to start a sql system from a REPL"
           (is (some? system/*sys*))))

(deftest valid-connection
  (testing "Connection must be valid")
  (let [datasource (system/instance system/*sys* [:sql :datasource])]
    (is (= [{:?column? 1}]
           (jdbc/execute! datasource
                          ["select 1"]
                          {:builder-fn rs/as-unqualified-lower-maps})))))

(comment
  (env/set-env! (io/resource "sql/test-env.edn") :test)
  (def system-config (SUT/configure-system (get-in @env/env [:system :sql])))
  (def running-system (system/start system-config))
  (system/stop running-system)
  ;
)
