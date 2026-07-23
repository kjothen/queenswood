(ns ^:eftest/synchronized com.repldriven.queenswood.cash-account.watcher-test
  (:require
    [com.repldriven.queenswood.cash-account.store :as store]
    [com.repldriven.queenswood.cash-account.watcher :as watcher]

    [com.repldriven.queenswood.cash-account-query.interface :as q]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.system.interface :as system]
    [com.repldriven.mono.testcontainers.interface]
    [com.repldriven.mono.test-system.interface :refer
     [with-test-system nom-test>]]
    [com.repldriven.mono.utility.interface :as utility]

    [clojure.test :refer [deftest is testing]]))

;; must match bank-cash-account.store/store-name — same FDB store
(def ^:private store-name "cash-accounts")

(def ^:private test-bank-id "bnk_watcher_test")

(defn- account
  [account-id status]
  {:bank-id test-bank-id
   :account-id account-id
   :party-id "pty.test"
   :product-id "prd.test"
   :version-id "v1"
   :name "Watcher Redelivery Test Account"
   :currency "GBP"
   :account-status status
   :created-at (utility/now)
   :updated-at (utility/now)})

(deftest redelivered-changelog-entry-is-a-noop-test
  (with-test-system
   [sys "classpath:bank-cash-account/application-test.yml"]
   (let [record-db (system/instance sys [:fdb :record-db])
         record-store (system/instance sys [:fdb :store])
         config {:record-db record-db :record-store record-store}
         account-id "acc.watcher.1"]
     (testing
       "seed the account already opened, but write a changelog
              entry as if the opening->opened transition hadn't
              consumed yet — simulates a redelivered/replayed entry"
       (nom-test> [_ (store/save-account
                      config
                      (account account-id :cash-account-status-opened)
                      {:account-id account-id
                       :status-after :cash-account-status-opening})]))
     (testing
       "redelivering the changelog entry is a no-op: the
              account's current status no longer matches the
              expected source, so the watcher skips the transition"
       (fdb/process-changelog record-db
                              "watcher-redelivery-test"
                              store-name
                              (watcher/cash-account-changelog-handler
                               record-store))
       (nom-test> [found (q/find-account config test-bank-id account-id)
                   _ (is (= :cash-account-status-opened
                            (:account-status found)))])))))
