(ns com.repldriven.mono.bank-payment.events
  (:require
    [com.repldriven.mono.bank-payment.domain :as domain]
    [com.repldriven.mono.bank-payment.store :as store]

    [com.repldriven.mono.bank-balance.interface :as balances]
    [com.repldriven.mono.bank-cash-account.interface :as cash-accounts]
    [com.repldriven.mono.bank-ledger-account.interface :as
     ledger-accounts]
    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.bank-transaction.interface :as transactions]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.log.interface :as log]
    [com.repldriven.mono.utility.interface :as utility]))

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
       ;; Creditor is known (matched via BBAN) so the inbound settles
       ;; directly to the bank's correspondent cash account; suspense
       ;; (2500) is reserved for unmatched inbounds — a workflow for
       ;; a later wave.
       (let-nom>
         [cash (ledger-accounts/find-by-code
                txn
                bank-id
                gl-code-cash-at-correspondent)
          _ (when (nil? cash)
              (error/fail
               :payment/no-cash-at-correspondent-account
               {:message
                (str "Bank has no 1100 cash-at-correspondent account"
                     " in its chart of accounts")
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
                       (:ledger-account-id cash)
                       policies
                       aggregates)
          expanded-legs (ledger-accounts/add-control-legs
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
  sub-ledger) so `add-control-legs` is a no-op, but we route through it
  anyway for consistency."
  [config payment]
  (let [{:keys [bank-id]} payment]
    (store/transact
     config
     (fn [txn]
       (let-nom>
         [pending (ledger-accounts/find-by-code
                   txn
                   bank-id
                   gl-code-pending-outbound)
          _ (when (nil? pending)
              (error/fail :payment/no-pending-outbound-account
                          {:message
                           (str "Bank has no 1200 account during "
                                "outbound settlement")
                           :bank-id bank-id}))
          cash (ledger-accounts/find-by-code
                txn
                bank-id
                gl-code-cash-at-correspondent)
          _ (when (nil? cash)
              (error/fail :payment/no-cash-at-correspondent-account
                          {:message
                           (str "Bank has no 1100 account during "
                                "outbound settlement")
                           :bank-id bank-id}))
          tx (settlement-transaction (:ledger-account-id pending)
                                     (:ledger-account-id cash)
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

(defn hold-outbound
  "Mark an outbound payment held while the scheme screens it. The money
  stays parked in 1200 pending-outbound, so there is no balance move —
  only the payment status flips pending → held."
  [config data]
  (let [{payment-id :end-to-end-id} data]
    (let-nom> [payment (store/get-outbound-payment config payment-id)]
      (cond
       (nil? payment)
       (error/fail
        :payment/hold-outbound
        {:message "Failed to find corresponding outbound payment to hold"
         :payment-id payment-id})

       (not= :outbound-payment-status-pending (:payment-status payment))
       (do (log/infof "Outbound payment hold ignored, not pending: %s"
                      {:payment-id payment-id
                       :payment-status (:payment-status payment)})
           payment)

       :else
       (let-nom>
         [held (domain/held-outbound-payment payment)
          _ (store/save-outbound-payment config held)]
         (log/infof "Outbound payment now held: %s" {:payment-id payment-id})
         held)))))

(defn hold-inbound
  "Inbound held transactions are informational for now — log and move on.
  A held-inbound workflow (suspense + release) is a later wave."
  [_config data]
  (log/infof "Inbound transaction held: %s"
             {:end-to-end-id (:end-to-end-id data)})
  data)

(defn- record-reversal-leg
  "DEBIT 1200 pending-outbound / CREDIT debtor — reverse the submission of
  an outbound payment the scheme declined or returned. The debtor leg is a
  sub-ledger account, so route through `add-control-legs`."
  [config payment]
  (let [{:keys [bank-id debtor-account-id]} payment]
    (store/transact
     config
     (fn [txn]
       (let-nom>
         [pending (ledger-accounts/find-by-code
                   txn
                   bank-id
                   gl-code-pending-outbound)
          _ (when (nil? pending)
              (error/fail :payment/no-pending-outbound-account
                          {:message
                           (str "Bank has no 1200 account during "
                                "outbound reversal")
                           :bank-id bank-id}))
          debtor-account (cash-accounts/get-account
                          txn
                          bank-id
                          debtor-account-id)
          tx (domain/outbound-reversal->transaction
              payment
              debtor-account
              (:ledger-account-id pending))
          expanded-legs (ledger-accounts/add-control-legs
                         txn
                         bank-id
                         (:legs tx))
          recorded (transactions/record-transaction
                    txn
                    (assoc tx :legs expanded-legs))
          {:keys [transaction-type legs]} recorded
          _ (balances/apply-legs txn legs transaction-type)]
         recorded)))))

(defn reject-outbound
  "Process an outbound `transaction-rejected` event. Reverses the in-flight
  payment (DEBIT 1200 / CREDIT debtor) and flips the OutboundPayment to
  failed with the scheme's cancellation code/reason. Pending and held
  payments are reversible; an already-failed payment is an idempotent
  no-op; a completed (settled) payment cannot be reversed here."
  [config data]
  (let [{payment-id :end-to-end-id
         :keys [cancellation-code cancellation-reason]}
        data]
    (let-nom> [payment (store/get-outbound-payment config payment-id)]
      (cond
       (nil? payment)
       (error/fail
        :payment/reject-outbound
        {:message "Failed to find corresponding outbound payment to reject"
         :payment-id payment-id})

       (= :outbound-payment-status-failed (:payment-status payment))
       (do (log/infof "Outbound payment rejection already processed: %s"
                      {:payment-id payment-id})
           payment)

       (= :outbound-payment-status-completed (:payment-status payment))
       (error/fail
        :payment/reject-outbound
        {:message "Cannot reverse an already-settled outbound payment"
         :payment-id payment-id})

       :else
       (let-nom>
         [failed (domain/failed-outbound-payment payment
                                                 cancellation-code
                                                 cancellation-reason)
          _ (store/save-outbound-payment config failed)
          _ (record-reversal-leg config payment)]
         (log/infof "Outbound payment rejected and reversed: %s"
                    {:payment-id payment-id
                     :cancellation-code cancellation-code})
         failed)))))
