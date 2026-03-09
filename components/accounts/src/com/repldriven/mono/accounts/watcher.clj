(ns com.repldriven.mono.accounts.watcher
  (:require
    [com.repldriven.mono.accounts.domain :as domain]

    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.schemas.interface :as schema]))

(defn account-changelog-handler
  "Returns a watcher handler that transitions closing
  accounts to closed when their changelog reports
  status-after :closing."
  [accounts-store-fn]
  (fn [ctx changelog-bytes]
    (let [changelog (schema/pb->AccountChangelog
                     changelog-bytes)]
      (when (= :closing (:status-after changelog))
        (let [store (accounts-store-fn ctx "accounts")
              account-id (:account-id changelog)]
          (when-some [rec (fdb/load-record store account-id)]
            (let [account (schema/pb->Account rec)]
              (when-some [transitioned
                          (domain/transition-lifecyle
                           store
                           account)]
                (fdb/save-record
                 store
                 (schema/Account->java transitioned))
                (fdb/write-changelog
                 store
                 "accounts"
                 (:account-id transitioned)
                 (schema/AccountChangelog->pb
                  {:account-id account-id
                   :status-before
                   (:account-status account)
                   :status-after
                   (:account-status transitioned)}))))))))))
