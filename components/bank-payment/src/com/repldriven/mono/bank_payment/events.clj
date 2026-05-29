(ns com.repldriven.mono.bank-payment.events
  (:require
    [com.repldriven.mono.bank-payment.domain :as domain]
    [com.repldriven.mono.bank-payment.store :as store]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account.interface :as cash-accounts]
    [com.repldriven.mono.bank-chart-of-accounts.interface :as
     chart-of-accounts]
    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.bank-transaction.interface :as transactions]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.utility.interface :as utility]))

(def ^:private gl-code-suspense "2500")
(def ^:private gl-code-pending-outbound "1200")
(def ^:private gl-code-cash-at-correspondent "1100")

(defn- check-debit-credit-code
  [debit-credit-code]
  (when (not= :debit-credit-code-credit debit-credit-code)
    (error/fail
     :payment/settle-inbound
     {:message "Inbound payment settlement for non-credit is not permissible"
      :debit-credit-code debit-credit-code})))

(defn- record-inbound-settlement
  [config data account]
  (let [{:keys [account-id bank-id]} account
        business-day (domain/current-business-day
                      (utility/now)
                      (:business-day-cutoff config))]
    (store/transact
     config
     (fn [txn]
       (let-nom>
         [suspense (cash-accounts/get-account-by-gl-code
                    txn
                    bank-id
                    gl-code-suspense)
          _ (when (nil? suspense)
              (error/fail
               :payment/no-suspense-account
               {:message
                (str "Bank has no 2500 suspense account in its chart"
                     " of accounts")
                :bank-id bank-id}))
          policies (policy/get-effective-policies
                    txn
                    {:bank-id bank-id})
          today-count (store/count-inbound-by-org-business-day
                       txn
                       bank-id
                       business-day)
          aggregates {:inbound-payment
                      {#{:bank-id :business-day} today-count}}
          transaction (domain/inbound-payment->transaction
                       data
                       account
                       (:account-id suspense)
                       policies
                       aggregates)
          expanded-legs (chart-of-accounts/expand-legs
                         txn
                         bank-id
                         (:legs transaction))
          transaction+legs (transactions/record-transaction
                            txn
                            (assoc transaction :legs expanded-legs))
          {:keys [transaction-id transaction-type legs]} transaction+legs
          _ (balances/apply-legs txn legs transaction-type)
          payment (domain/new-inbound-payment data
                                              account-id
                                              bank-id
                                              business-day
                                              transaction-id)
          _ (store/save-inbound-payment txn payment)]
         payment)))))

(defn settle-inbound
  [config data]
  (let [{:keys [debit-credit-code creditor-bban scheme-transaction-id]} data]
    (let-nom>
      [_ (check-debit-credit-code debit-credit-code)
       account (cash-accounts/get-account-by-bban config creditor-bban)
       _ (when-not account
           (error/fail :payment/settle-inbound
                       {:message "No account found for creditor BBAN"
                        :bban creditor-bban}))
       settled (store/get-inbound-payment config scheme-transaction-id)]
      (if settled
        (do (log/infof
             "Inbound payment settlement already processed: %s"
             scheme-transaction-id)
            settled)
        (record-inbound-settlement config data account)))))

(defn- settlement-transaction
  "DEBIT 1200 pending-outbound / CREDIT 1100 cash-at-correspondent —
  the second hop of an outbound payment, fired when ClearBank
  confirms settlement. Drains the in-flight asset bucket and reduces
  the bank's correspondent-account claim."
  [pending-outbound-id cash-at-correspondent-id payment]
  (let [{:keys [amount currency payment-id]} payment]
    {:idempotency-key (str "settle-out-" payment-id)
     :transaction-type :transaction-type-outbound-transfer
     :currency currency
     :legs [{:account-id pending-outbound-id
             :balance-type :balance-type-default
             :balance-status :balance-status-posted
             :side :leg-side-debit
             :amount amount}
            {:account-id cash-at-correspondent-id
             :balance-type :balance-type-default
             :balance-status :balance-status-posted
             :side :leg-side-credit
             :amount amount}]}))

(defn- record-settlement-leg
  "Drain 1200 → 1100 on the customer bank's books. GL-only legs (no
  sub-ledger) so `expand-legs` is a no-op, but we route through it
  anyway for consistency."
  [config payment]
  (let [{:keys [bank-id]} payment]
    (store/transact
     config
     (fn [txn]
       (let-nom>
         [pending (cash-accounts/get-account-by-gl-code
                   txn
                   bank-id
                   gl-code-pending-outbound)
          _ (when (nil? pending)
              (error/fail :payment/no-pending-outbound-account
                          {:message
                           (str "Bank has no 1200 account during "
                                "outbound settlement")
                           :bank-id bank-id}))
          cash (cash-accounts/get-account-by-gl-code
                txn
                bank-id
                gl-code-cash-at-correspondent)
          _ (when (nil? cash)
              (error/fail :payment/no-cash-at-correspondent-account
                          {:message
                           (str "Bank has no 1100 account during "
                                "outbound settlement")
                           :bank-id bank-id}))
          tx (settlement-transaction (:account-id pending)
                                     (:account-id cash)
                                     payment)
          recorded (transactions/record-transaction txn tx)
          {:keys [transaction-type legs]} recorded
          _ (balances/apply-legs txn legs transaction-type)]
         recorded)))))

(defn settle-outbound
  [config data]
  (let [{payment-id :end-to-end-id} data]
    (let-nom> [payment (store/get-outbound-payment config payment-id)]
      (cond
       (nil? payment)
       (error/fail
        :payment/settle-outbound
        {:message "Failed to find corresponding outbound payment for settlement"
         :payment-id payment-id})

       (= :outbound-payment-status-completed (:payment-status payment))
       (do (log/infof "Outbound payment settlement already completed: %s"
                      payment-id)
           payment)

       :else
       (let-nom>
         [completed (domain/completed-outbound-payment payment)
          _ (store/save-outbound-payment config completed)
          _ (record-settlement-leg config payment)]
         (log/infof "Outbound payment settlement now completed: %s"
                    {:payment-id payment-id})
         completed)))))
