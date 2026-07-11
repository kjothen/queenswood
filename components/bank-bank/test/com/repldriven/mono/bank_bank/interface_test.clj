(ns ^:eftest/synchronized com.repldriven.mono.bank-bank.interface-test
  "Unknown-command dispatch stays pure; the FDB-backed cases cover
  what the API scenario suite can't see — that the owner membership
  commits atomically with the bank and that a duplicate onboarding
  aborts the whole transaction. Happy-path admin creation over the
  bus is covered by banks/*.edn in bank-test-api-scenarios."
  (:require
    [com.repldriven.mono.bank-bank.interface :as SUT]

    [com.repldriven.mono.bank-bank.commands :as commands]

    [com.repldriven.mono.bank-membership.interface :as memberships]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface]
    [com.repldriven.mono.identity-provider.interface :as identity-provider]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.testcontainers.interface]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]

    [clojure.test :refer [deftest is testing]]))

(defn- fdb-config
  [sys]
  {:record-db (system/instance sys [:fdb :record-db])
   :record-store (system/instance sys [:fdb :store])})

(deftest unknown-command-test
  (testing "dispatch rejects command names not in the handler registry"
    (let [result (#'commands/dispatch
                  {:schemas {}}
                  {:command "unknown-bank-command" :payload nil})]
      (is (error/rejection? result))
      (is (= :bank/unknown-command (error/kind result))))))

(deftest new-bank-with-membership-test
  (with-test-system
   [sys "classpath:bank-bank/application-test.yml"]
   (let [config (fdb-config sys)
         idp (identity-provider/local-provider {})
         user-id "usr.test-onboard"]
     (testing "creates the bank and owner membership in one transaction"
       (nom-test> [{:keys [bank membership]} (SUT/new-bank
                                              config
                                              "Acme Bank"
                                              :bank-status-test
                                              nil
                                              ["GBP"]
                                              {:identity-provider idp
                                               :membership {:user-id user-id
                                                            :role :role-owner}})
                   _ (is (re-find #"^bnk\." (:bank-id bank)))
                   _ (is (= user-id (:user-id membership)))
                   _ (is (= (:bank-id bank) (:bank-id membership)))
                   _ (is (= :role-owner (:role membership)))
                   listed (memberships/list-by-user config user-id)
                   _ (is (= 1 (count listed)))]))
     (testing "a second bank for the same user aborts with no writes"
       (let [r (SUT/new-bank config
                             "Acme Again"
                             :bank-status-test
                             nil
                             ["GBP"]
                             {:identity-provider idp
                              :membership {:user-id user-id
                                           :role :role-owner}})]
         (is (error/rejection? r))
         (is (= :membership/already-exists (error/kind r)))
         (nom-test> [listed (memberships/list-by-user config user-id)
                     _ (is (= 1 (count listed)))]))))))
