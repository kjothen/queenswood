(ns com.repldriven.mono.migrator.interface-test
  (:require [clojure.test :as test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.migrator.interface :as SUT]
            [com.repldriven.mono.postgres.interface :as postgres]
            [com.repldriven.mono.system.interface :as system]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defn env-fixture
  [f]
  (env/set-env! (io/resource "migrator/test-env.edn") :test)
  (f))

(use-fixtures :once env-fixture)

(deftest migrate-test
  (testing "Applying a migration changelog should result in a paved db"
    (let [system-config (postgres/configure-system (get-in @env/env [:system :postgres]))]
      (try
        (let [running-system (system/start system-config)]
          (try
            (let [datasource (system/instance running-system [:postgres :datasource])
                  db-spec (next.jdbc/get-datasource datasource)]
              (SUT/migrate db-spec "migrator/test-changelog.sql")
              (is (= [{:name "hello"} {:name "world"}]
                    (jdbc/execute! datasource ["select name from test order by id asc"]
                      {:builder-fn rs/as-unqualified-lower-maps}))))
            (catch Exception e
              (assert false (format "Unable to get datasource, %s" e)))
            (finally
              (system/stop running-system))))
        (catch Exception e
          (assert false (format "Unable to start system, %s" e)))))))
