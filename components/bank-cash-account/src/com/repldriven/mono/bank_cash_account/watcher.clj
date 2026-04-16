(ns com.repldriven.mono.bank-cash-account.watcher
  (:require
    [com.repldriven.mono.bank-cash-account.domain :as domain]
    [com.repldriven.mono.bank-cash-account.store :as store]

    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.fdb.interface :as fdb]))

(defn cash-account-changelog-handler
  "Returns a watcher handler that transitions an opening
  account to opened, or a closing account to closed."
  [record-store]
  (fn [ctx changelog-bytes]
    (let [changelog (schema/pb->CashAccountChangelog changelog-bytes)
          {:keys [organization-id account-id status-after]} changelog]
      (when (#{:cash-account-status-opening :cash-account-status-closing}
             status-after)
        (let [txn (fdb/ctx->txn ctx record-store)
              account (store/find-account txn organization-id account-id)]
          (when account
            (let [transitioned
                  (case status-after
                    :cash-account-status-opening (domain/opened-account account)
                    :cash-account-status-closing (domain/closed-account
                                                  account))]
              (store/save-account txn
                                  transitioned
                                  {:account-id account-id
                                   :status-before status-after
                                   :status-after (:account-status
                                                  transitioned)}))))))))
