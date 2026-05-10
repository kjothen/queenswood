(ns com.repldriven.mono.bank-payment.domain
  (:require
    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility])
  (:import
    (java.time Instant ZoneId)))

(defn current-business-day
  "Returns the epoch-day (long) of the business day that contains
  `now-ms` under `cutoff`. A timestamp that falls in a
  zone-local time-of-day before `:hour-of-day` counts as the
  *previous* day's bucket. A missing `cutoff` (nil or empty map)
  defaults to UTC midnight — i.e. plain calendar day in UTC."
  [^long now-ms cutoff]
  (let [{:keys [zone hour-of-day]
         :or {zone "UTC" hour-of-day 0}}
        cutoff]
    (-> (Instant/ofEpochMilli now-ms)
        (.atZone (ZoneId/of zone))
        (.minusHours (long hour-of-day))
        .toLocalDate
        .toEpochDay)))

(defn- ensure-distinct-accounts
  [debtor-account-id creditor-account-id]
  (when (= debtor-account-id creditor-account-id)
    (error/reject :payment/self-transfer-not-permitted
                  {:message "Debtor and creditor accounts must differ"
                   :account-id debtor-account-id})))

(defn- ensure-currency-matches
  [payment-currency account]
  (when (not= payment-currency (:currency account))
    (error/reject :payment/currency-mismatch
                  {:message
                   "Payment currency must match account currency"
                   :payment-currency payment-currency
                   :account-id (:account-id account)
                   :account-currency (:currency account)})))

(defn- check-capability
  [policies kind action]
  (policy/check-capability policies kind {:action action}))

(defn- check-daily-count
  [policies kind aggregates]
  (policy/check-limit
   policies
   kind
   {:aggregate :count
    :window :daily
    :value (inc (get-in aggregates
                        [kind #{:organization-id :business-day}]))}))

(defn internal-payment->transaction
  [data debtor-account creditor-account policies aggregates]
  (let [{:keys [idempotency-key debtor-account-id
                creditor-account-id currency amount
                reference]}
        data]
    (let-nom>
      [_ (ensure-distinct-accounts debtor-account-id creditor-account-id)
       _ (ensure-currency-matches currency debtor-account)
       _ (ensure-currency-matches currency creditor-account)
       _ (check-capability policies
                           :internal-payment
                           :internal-payment-action-submit)
       _ (check-daily-count policies :internal-payment aggregates)]
      (utility/assoc-some
       {:idempotency-key idempotency-key
        :transaction-type :transaction-type-internal-transfer
        :currency currency
        :legs [{:account-id debtor-account-id
                :balance-type :balance-type-default
                :balance-status :balance-status-posted
                :side :leg-side-debit
                :amount amount}
               {:account-id creditor-account-id
                :balance-type :balance-type-default
                :balance-status :balance-status-posted
                :side :leg-side-credit
                :amount amount}]}
       :reference
       reference))))

(defn inbound-payment->transaction
  [data creditor-account internal-account-id policies aggregates]
  (let [{:keys [scheme-transaction-id currency amount reference]} data
        {creditor-account-id :account-id} creditor-account]
    (let-nom>
      [_ (ensure-currency-matches currency creditor-account)
       _ (check-capability policies
                           :inbound-payment
                           :inbound-payment-action-receive)
       _ (check-daily-count policies :inbound-payment aggregates)]
      (utility/assoc-some
       {:idempotency-key scheme-transaction-id
        :transaction-type :transaction-type-inbound-transfer
        :currency currency
        :legs [{:account-id internal-account-id
                :balance-type :balance-type-suspense
                :balance-status :balance-status-posted
                :side :leg-side-debit
                :amount amount}
               {:account-id creditor-account-id
                :balance-type :balance-type-default
                :balance-status :balance-status-posted
                :side :leg-side-credit
                :amount amount}]}
       :reference
       reference))))

(defn new-inbound-payment
  [data creditor-account-id organization-id business-day transaction-id]
  (let [{:keys [scheme-transaction-id end-to-end-id scheme
                currency amount debtor-name reference]}
        data
        now (utility/now)]
    (utility/assoc-some
     {:payment-id (utility/generate-id "pmt")
      :scheme-transaction-id scheme-transaction-id
      :end-to-end-id end-to-end-id
      :scheme scheme
      :creditor-account-id creditor-account-id
      :organization-id organization-id
      :business-day business-day
      :currency currency
      :amount amount
      :transaction-id transaction-id
      :debtor-name debtor-name
      :created-at now
      :updated-at now}
     :reference
     reference)))

(defn outbound-payment->transaction
  [data debtor-account internal-account-id policies aggregates]
  (let [{:keys [idempotency-key debtor-account-id
                currency amount reference]}
        data]
    (let-nom>
      [_ (ensure-currency-matches currency debtor-account)
       _ (check-capability policies
                           :outbound-payment
                           :outbound-payment-action-send)
       _ (check-daily-count policies :outbound-payment aggregates)]
      (utility/assoc-some
       {:idempotency-key idempotency-key
        :transaction-type :transaction-type-outbound-transfer
        :currency currency
        :legs [{:account-id debtor-account-id
                :balance-type :balance-type-default
                :balance-status :balance-status-posted
                :side :leg-side-debit
                :amount amount}
               {:account-id internal-account-id
                :balance-type :balance-type-suspense
                :balance-status :balance-status-posted
                :side :leg-side-credit
                :amount amount}]}
       :reference
       reference))))

(defn new-outbound-payment
  [data business-day transaction-id]
  (let [{:keys [idempotency-key organization-id debtor-account-id
                creditor-bban creditor-name scheme
                currency amount reference]}
        data
        now (utility/now)]
    (utility/assoc-some
     {:payment-id (utility/generate-id "pmt")
      :idempotency-key idempotency-key
      :scheme scheme
      :organization-id organization-id
      :business-day business-day
      :debtor-account-id debtor-account-id
      :creditor-bban creditor-bban
      :creditor-name creditor-name
      :currency currency
      :amount amount
      :payment-status :outbound-payment-status-pending
      :transaction-id transaction-id
      :created-at now
      :updated-at now}
     :reference
     reference)))

(defn completed-outbound-payment
  [payment]
  (assoc payment
         :payment-status :outbound-payment-status-completed
         :updated-at (utility/now)))

(defn new-internal-payment
  [data business-day transaction-id]
  (let [{:keys [idempotency-key organization-id debtor-account-id
                creditor-account-id currency amount
                reference]}
        data
        now (utility/now)]
    (utility/assoc-some
     {:payment-id (utility/generate-id "pmt")
      :idempotency-key idempotency-key
      :organization-id organization-id
      :business-day business-day
      :debtor-account-id debtor-account-id
      :creditor-account-id creditor-account-id
      :currency currency
      :amount amount
      :transaction-id transaction-id
      :created-at now
      :updated-at now}
     :reference
     reference)))
