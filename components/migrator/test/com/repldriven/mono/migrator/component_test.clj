(ns com.repldriven.mono.migrator.component-test
  (:require
   [com.repldriven.mono.db.interface :as sql]
   [com.repldriven.mono.migrator.interface :as SUT]
   [com.repldriven.mono.system.interface :as system]
   [com.repldriven.mono.test-system.interface :as test-system]

   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs]

   [clojure.test :as test :refer [deftest is testing use-fixtures]]))

(defn db-spec
  [sys]
  (let [datasource (system/instance sys [:db :datasource])]
    (sql/get-datasource datasource)))

(defn with-system-fixture
  [f]
  (system/with-*sys* test-system/*sysdef*
    (f)))

(use-fixtures :once
  (test-system/fixture "classpath:migrator/test-application.yml" :test)
  with-system-fixture)

(deftest migrate-test
  (testing
   "Applying a migration changelog should result in a paved db"
   (let [datasource (system/instance system/*sys* [:db :datasource])
         db-spec (db-spec system/*sys*)]
     (SUT/migrate db-spec "migrator/test-changelog.sql")
     (is (= [{:name "hello"} {:name "world"}]
            (jdbc/execute! datasource
                           ["select name from test order by id asc"]
                           {:builder-fn rs/as-unqualified-lower-maps}))))))
