(ns com.repldriven.mono.bank-interest.interface-test
  "Pure unit test for the dispatcher's unknown-command rejection.
  Daily accrual and monthly capitalisation happy paths are
  covered by the property test (~245 :accrue-interest and ~265
  :capitalize-interest per 500-trial run) and by the
  interest-accrual.edn / full-happy-path.edn scenarios — no
  system boot is needed at the brick layer."
  (:require
    [com.repldriven.mono.bank-interest.commands :as commands]

    [com.repldriven.mono.error.interface :as error]

    [clojure.test :refer [deftest is testing]]))

(deftest unknown-command-test
  (testing "dispatch rejects command names not in the handler registry"
    (let [result (#'commands/dispatch
                  {:schemas {}}
                  {:command "unknown-interest-command" :payload nil})]
      (is (error/rejection? result))
      (is (= :interest/unknown-command (error/kind result))))))
