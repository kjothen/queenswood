(ns com.repldriven.mono.bank-test-projections.interface-test
  (:require
    [com.repldriven.mono.bank-test-projections.interface :as SUT]

    [com.repldriven.mono.bank-test-model.interface :as model]

    [clojure.test :refer [deftest is testing]]))

(deftest project-model-balances-test
  (testing "reads :available off each model account"
    (let [state (-> model/init-state
                    (assoc-in [:accounts :acct-0] {:available 100})
                    (assoc-in [:accounts :acct-1] {:available -50}))]
      (is (= {:acct-0 100 :acct-1 -50} (SUT/project-model-balances state)))))
  (testing "empty state projects to empty map"
    (is (= {} (SUT/project-model-balances model/init-state)))))
