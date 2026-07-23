(ns com.repldriven.queenswood.cash-account.watcher
  (:require
    [com.repldriven.queenswood.cash-account.domain :as domain]
    [com.repldriven.queenswood.cash-account.store :as store]

    [com.repldriven.queenswood.cash-account-query.interface :as q]
    [com.repldriven.queenswood.schema.interface :as schema]

    [com.repldriven.mono.fdb.interface :as fdb]))

(defn cash-account-changelog-handler
  [record-store]
  (fn [ctx changelog-bytes]
    (let [changelog (schema/pb->CashAccountChangelog changelog-bytes)
          {:keys [bank-id account-id status-after]} changelog]
      (when (#{:cash-account-status-opening :cash-account-status-closing}
             status-after)
        (let [txn (fdb/ctx->txn ctx record-store)
              account (q/find-account txn bank-id account-id)]
          (when (and account (= status-after (:account-status account)))
            (let [transitioned
                  (case status-after
                    :cash-account-status-opening
                    (domain/opened-account account)

                    :cash-account-status-closing
                    (domain/closed-account account))]
              (store/save-account
               txn
               transitioned
               {:account-id account-id
                :status-before status-after
                :status-after (:account-status transitioned)}))))))))
