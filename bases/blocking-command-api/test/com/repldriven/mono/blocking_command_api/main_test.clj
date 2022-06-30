(ns com.repldriven.mono.blocking-command-api.main-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.pulsar.interface :as pulsar]
            [com.repldriven.mono.blocking-command-api.main :as SUT]
            [com.repldriven.mono.system.interface :as system])
  (:import (org.apache.pulsar.client.admin PulsarAdmin)))

(defn env-fixture
  [f]
  (env/set-env! (io/resource "blocking-command-api/test-env.edn") :test)
  (f))

(use-fixtures :once env-fixture)


(comment
  (env/set-env! (io/resource "blocking-command-api/test-env.edn") :test)
  (def system-config (SUT/create-system (:system @env/env)))
  )
