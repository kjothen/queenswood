(ns com.repldriven.mono.pulsar.interface-test
  (:refer-clojure :exclude [send])
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [com.repldriven.mono.env.interface :as env]
            [com.repldriven.mono.pulsar.env-reader]
            [com.repldriven.mono.pulsar.interface :as SUT]
            [com.repldriven.mono.system.interface :as system
             :refer [with-system]]
            [org.httpkit.client :as httpkit])
  (:import (java.util.concurrent TimeUnit)
           (org.apache.pulsar.client.admin PulsarAdmin)
           (org.apache.pulsar.client.api Consumer MessageId Producer)
           (org.apache.pulsar.common.api EncryptionContext)
           (org.apache.pulsar.common.policies.data
            SchemaAutoUpdateCompatibilityStrategy)))

(defn env-fixture
  [f]
  (env/set-env! (io/resource "pulsar/test-env.edn") :test)
  (f))

(use-fixtures :once env-fixture)

(defn http-get-json
  [url]
  (json/read-str
   (:body @(httpkit/get url {:headers {"Accept" "application/json"}}))))

(deftest development-test
  (testing "Developers should be able to start/stop a pulsar system from  REPL"
    (with-system [sys (SUT/configure-system (get-in @env/env [:system :pulsar]))]
      (let [producer (system/instance sys [:pulsar :producer])
            consumer (system/instance sys [:pulsar :consumer])
            msg {:y 42}]
        (.. producer (send (.getBytes (json/write-str msg))))
        (is (= msg
               (json/read-str (.. consumer (receive 500 TimeUnit/MILLISECONDS)
                                  getValue getJsonNode toString)
                              :key-fn keyword)))))))

(comment
  (env/set-env! (io/resource "pulsar/test-env.edn") :test)
  (def system-config (SUT/configure-system (get-in @env/env [:system :pulsar])))
  (def running-system (system/start system-config))

  (def ^PulsarAdmin admin (system/instance running-system [:pulsar :admin]))
  (def service-url (.getServiceUrl admin))
  (def namespace "tenant-1/namespace-1")
  (def namespace-api-url
    (string/join "/" [service-url "admin" "v2" "namespaces" namespace]))

  (def is-allow-auto-update-schema-url
    (str namespace-api-url "/isAllowAutoUpdateSchema"))
  (is (= false (http-get-json is-allow-auto-update-schema-url)))

  ;;(def schema-auto-update-compatibility-strategy-url
  ;;  (str namespace-api-url "/schemaAutoUpdateCompatibilityStrategy"))
  ;;(is (= "AutoUpdateDisabled"
  ;;       (http-get-json schema-auto-update-compatibility-strategy-url)))

  (def encryption-required-url (str namespace-api-url "/encryptionRequired"))
  (is (= true (http-get-json encryption-required-url)))

  (def ^Producer producer (system/instance running-system [:pulsar :producer]))
  (def ^Consumer consumer (system/instance running-system [:pulsar :consumer]))
  (def ^Consumer schemas (system/instance running-system [:pulsar :schemas]))
  (def schema (get-in schemas [:user :definition]))
  (def msg {:y 41})

  (.. producer (send (.getBytes (json/write-str msg))))

  (def recv-msg (.. consumer (receive 500 TimeUnit/MILLISECONDS)))
  (.. consumer (acknowledge recv-msg))
  (= msg
     (json/read-str (.. recv-msg getValue getJsonNode toString)
                    :key-fn keyword))
  (system/stop running-system)

)
