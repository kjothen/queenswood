(ns com.repldriven.mono.db.interface-test
  (:require [clojure.test :as test :refer [deftest is testing use-fixtures]]
            [com.repldriven.mono.db.interface :as SUT]
            [com.repldriven.mono.system.interface :as system]
            [com.repldriven.mono.test-system.interface :as test-system]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defn with-system-fixture
  [f]
  (system/with-*sys* test-system/*sysdef*
    (f)))

(use-fixtures :once
  (test-system/fixture "classpath:db/test-application.yml" :test)
  with-system-fixture)

(deftest system-start
  (testing "Developers should be able to start a sql system from a REPL"
           (is (some? system/*sys*))))

(deftest valid-connection
  (testing "Connection must be valid")
  (let [datasource (system/instance system/*sys* [:db :datasource])]
    (is (= [{:?column? 1}]
           (jdbc/execute! datasource
                          ["select 1"]
                          {:builder-fn rs/as-unqualified-lower-maps})))))
