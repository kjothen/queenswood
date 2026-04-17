(ns com.repldriven.mono.bank-balance.domain
  (:require
    [com.repldriven.mono.bank-balance.restriction :as restriction]

    [com.repldriven.mono.error.interface :refer [let-nom>]]))

(defn- net
  "Returns credit minus debit for a balance, defaulting
  to zero."
  [balance]
  (if balance (- (:credit balance 0) (:debit balance 0)) 0))

(defn- find-balance
  [balances balance-type balance-status]
  (first (filter #(and (= balance-type (:balance-type %))
                       (= balance-status (:balance-status %)))
                 balances)))

(defn posted-balance
  "Returns the net default/posted balance."
  [balances currency]
  (let [b (find-balance balances
                        :balance-type-default
                        :balance-status-posted)]
    {:value (if b (net b) 0)
     :currency currency}))

(defn available-balance
  "Returns the available balance for the given account
  type."
  [account-type balances currency]
  (let [default-posted
        (net (find-balance balances
                           :balance-type-default
                           :balance-status-posted))
        v (case account-type
            (:account-type-current
             :account-type-savings
             :account-type-term-deposit)
            (+ default-posted
               (net (find-balance
                     balances
                     :balance-type-default
                     :balance-status-pending-incoming))
               (net (find-balance
                     balances
                     :balance-type-default
                     :balance-status-pending-outgoing)))

            :account-type-settlement
            (+ default-posted
               (net (find-balance
                     balances
                     :balance-type-interest-payable
                     :balance-status-posted)))

            :account-type-internal
            default-posted

            ;; unknown type — just posted
            default-posted)]
    {:value v :currency currency}))

(defn apply-leg
  "Applies a transaction leg to a balance. Debits add to
  :debit, credits add to :credit. Returns the updated
  balance."
  [balance {:keys [side amount]}]
  (let [field (if (= :leg-side-debit side) :debit :credit)]
    (update balance field + amount)))

(defn new-balance
  "Creates a new Balance record map. Rejects if a balance
  with the same composite key already exists. Credit and
  debit default to zero if not provided."
  [data exists?]
  (let-nom>
    [_ (restriction/check-unique? data exists?)]
    (let [{:keys [account-id account-type balance-type balance-status
                  currency credit debit credit-carry]}
          data
          now (System/currentTimeMillis)]
      (cond-> {:account-id account-id
               :balance-type balance-type
               :balance-status balance-status
               :currency currency
               :credit (or credit 0)
               :debit (or debit 0)
               :credit-carry (or credit-carry 0)
               :created-at now
               :updated-at now}
              account-type
              (assoc :account-type account-type)))))
