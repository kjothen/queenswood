(ns com.repldriven.mono.migrator.interface-test
  (:require
    com.repldriven.mono.testcontainers.interface

    [com.repldriven.mono.db.interface :as sql]
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.migrator.interface :as SUT]
    [com.repldriven.mono.system.interface :as system]

    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]

    [clojure.test :as test :refer [deftest is testing]]))

(defn- test-system
  []
  (error/nom-> (env/config "classpath:migrator/application-test.yml" :test)
               system/defs
               system/start))

(defn db-spec
  [sys]
  (let [datasource (system/instance sys [:db :datasource])]
    (sql/get-datasource datasource)))

(deftest migrate-test
  (testing "Applying a migration changelog should result in a paved db"
    (system/with-system [sys (test-system)]
      (let [datasource (system/instance sys [:db :datasource])
            db-spec (db-spec sys)]
        (SUT/migrate db-spec "migrator/test-changelog.sql")
        (is (= [{:name "hello"} {:name "world"}]
               (jdbc/execute! datasource
                              ["select name from test order by id asc"]
                              {:builder-fn rs/as-unqualified-lower-maps})))))))
