(ns com.repldriven.mono.migrator.component-test
  (:require
    [com.repldriven.mono.db.interface :as sql]
    [com.repldriven.mono.env.interface :as env]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.migrator.interface :as SUT]
    [com.repldriven.mono.system.interface :as system]

    [next.jdbc :as jdbc]
    [next.jdbc.result-set :as rs]

    [clojure.test :as test :refer [deftest is testing]]))

(defn db-spec
  [sys]
  (let [datasource (system/instance sys [:db :datasource])]
    (sql/get-datasource datasource)))

(deftest migrate-test
  (testing "Applying a migration changelog should result in a paved db"
    (let [sys (error/nom-> (env/config "classpath:migrator/test-application.yml"
                                       :test)
                           system/defs
                           system/start)]
      (is (not (error/anomaly? sys)) (str "System should start: " (pr-str sys)))
      (when (system/system? sys)
        (system/with-system sys
          (let [datasource (system/instance sys [:db :datasource])
                db-spec (db-spec sys)]
            (SUT/migrate db-spec "migrator/test-changelog.sql")
            (is (= [{:name "hello"} {:name "world"}]
                   (jdbc/execute! datasource
                                  ["select name from test order by id asc"]
                                  {:builder-fn rs/as-unqualified-lower-maps})))))))))
