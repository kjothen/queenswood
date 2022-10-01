(ns com.repldriven.mono.postgres.interface-test
  (:require [clojure.test :as test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.postgres.interface :as SUT]
            [com.repldriven.mono.system.interface
             :as system :refer [with-system]]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(defn env-fixture
  [f]
  (env/set-env! (io/resource "postgres/test-env.edn") :test)
  (f))

(use-fixtures :once env-fixture)

(deftest development-test
  (testing "Developers should be able to start and stop a postgres db"
    (with-system [sys (SUT/configure-system
                        (get-in @env/env [:system :postgres]))]
      (let [datasource (system/instance sys [:postgres :datasource])]
        (is (= [{:?column? 1}]
              (jdbc/execute! datasource ["select 1"]
                {:builder-fn rs/as-unqualified-lower-maps})))))))

(comment
  (env/set-env! (io/resource "postgres/test-env.edn") :test)
  (def system-config (SUT/configure-system (get-in @env/env [:system :postgres])))
  (tap> system-config)
  (def running-system (system/start system-config))
  (tap> running-system)
  (system/stop running-system)
  )
