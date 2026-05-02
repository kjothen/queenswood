(ns ^:eftest/synchronized com.repldriven.mono.bank-idv.interface-test
  (:require
    [com.repldriven.mono.bank-idv.commands :as commands]
    [com.repldriven.mono.bank-idv.interface]

    [com.repldriven.mono.fdb.interface]
    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.processor.interface :as processor]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.testcontainers.interface]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(deftest unknown-command-test
  (testing "dispatch rejects command names not in the handler registry"
    (let [result (#'commands/dispatch
                  {:schemas {}}
                  {:command "unknown-idv-command" :payload nil})]
      (is (error/rejection? result))
      (is (= :idv/unknown-command (error/kind result))))))

(def ^:private test-org-id "org_test_idv")

(defn- send-command
  [proc schemas command-name data]
  (let [payload (avro/serialize (get schemas command-name) data)]
    (if (error/anomaly? payload)
      payload
      (processor/process proc {:command command-name :payload payload}))))

(defn- decode-payload
  [schemas schema-name result]
  (avro/deserialize-same (get schemas schema-name) (:payload result)))

(defn- test-initiate-idv
  [proc schemas]
  (testing "initiate creates IDV with pending status"
    (let [payload {:organization-id test-org-id :party-id "pty.test-party-id"}]
      (nom-test> [result (send-command proc schemas "initiate-idv" payload)
                  _
                  (is (= "ACCEPTED" (:status result)))
                  decoded
                  (decode-payload schemas "idv" result)
                  _
                  (is (some? (:verification-id decoded)))
                  _
                  (is (= "pty.test-party-id" (:party-id decoded)))
                  _
                  (is (= :idv-status-pending (:status decoded)))
                  _
                  (is (nil? (:completed-at decoded)))]))))

;; pending → accepted is no longer driven by an unconditional flip
;; in this brick; it now flows through the IDV-provider adapter
;; (bank-idv-onfido-adapter) and the message-bus event handler in
;; `bank-idv.events`. The full chain is exercised by the monolith
;; integration test `idv_test.clj`.

(deftest process-idv-test
  (with-test-system [sys "classpath:bank-idv/application-test.yml"]
                    (let [proc (system/instance sys [:idv :processor])
                          schemas (system/instance sys [:avro :serde])]
                      (test-initiate-idv proc schemas))))
