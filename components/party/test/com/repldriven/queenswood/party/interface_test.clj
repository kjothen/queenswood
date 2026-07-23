(ns com.repldriven.queenswood.party.interface-test
  "Pure unit test for the dispatcher's unknown-command rejection.
  Happy-path command handlers, watcher behaviour, and rejection
  paths surfaced by the API are covered by the EDN scenario suite
  (parties/*.edn in bank-test-api-scenarios) and any domain-level
  pure-function tests — no system boot is needed at the brick
  layer."
  (:require
    [com.repldriven.queenswood.party.commands :as commands]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(deftest unknown-command-test
  (testing "dispatch rejects command names not in the handler registry"
    (let [result (#'commands/dispatch
                  {:schemas {}}
                  {:command "unknown-party-command" :payload nil})]
      (is (error/rejection? result))
      (is (= :party/unknown-command (error/kind result))))))
