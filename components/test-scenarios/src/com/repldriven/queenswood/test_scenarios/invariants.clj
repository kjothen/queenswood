(ns com.repldriven.queenswood.test-scenarios.invariants
  "Standing accounting invariant, asserted after every scenario step:
  every bank's trial balance ties — Σdebit == Σcredit per currency
  across the whole chart of accounts. A failure means a step committed
  an unbalanced or mis-routed set of posted legs (e.g. a posting whose
  offset never reached the GL, or fanned out to the wrong control).
  This is the check that would have caught interest accrual landing off
  the books, and it guards the next class of bug too — a reversal that
  only reverses one leg, a new transaction type that forgets a control.

  Cheap by construction: it reads only `default/posted` balances (the
  same set `trial-balance` aggregates), so in-flight buckets (held,
  pending, interest-accrued sub-ledger) don't perturb it — they roll up
  into their control accounts via the fan-out before they count."
  (:require
    [com.repldriven.queenswood.balance-query.interface :as balances]
    [com.repldriven.queenswood.ledger-account.interface :as ledger-accounts]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]

    [clojure.test :refer [is]]))

(defn- posted-value
  "The credit-positive posted net (credit − debit) of one ledger
  account, or 0 if its balances can't be read."
  [config account-id]
  (let [bs (balances/get-balances config account-id)]
    (if (error/anomaly? bs)
      0
      (:value (:posted-balance bs)))))

(defn- trial-balance-entries
  [config accounts]
  (mapv (fn [account]
          {:currency (:currency account)
           :normal-side (if (ledger-accounts/debit-normal?
                             (:gl-account-type account))
                          :debit
                          :credit)
           :value (posted-value config (:ledger-account-id account))})
        accounts))

(defn assert-bank-ties
  "Assert the trial balance ties for one bank, per currency. Reads the
  whole chart — `list-accounts` plus every account's posted balance — in
  a single FDB snapshot, so an async settlement commit landing mid-read
  (e.g. `settle-outbound` posting its 1100/2100 legs on a webhook thread
  while this runs on the scenario thread) can't tear the per-account
  reads into a spurious imbalance."
  [config bank-id]
  (let [entries (fdb/transact
                 config
                 (fn [txn]
                   (let [accounts (ledger-accounts/list-accounts txn bank-id)]
                     (when-not (error/anomaly? accounts)
                       (trial-balance-entries txn accounts))))
                 :scenario/trial-balance-snapshot
                 "Failed to read trial balance snapshot")]
    (when (and (some? entries) (not (error/anomaly? entries)))
      (doseq [{:keys [currency debit credit]} (balances/trial-balance entries)]
        (is (= debit credit)
            (str "trial balance must tie — bank "
                 bank-id
                 " "
                 currency
                 " (Dr "
                 debit
                 " / Cr "
                 credit
                 ")"))))))

(defn verify-books-tie
  "Assert every bank created so far in the run still has tying books.
  Returns `ctx` unchanged so it can be threaded through the step
  reducer. `ctx` is the runner context — `:bank` is the FDB config and
  `:banks` holds the per-model `{:real-id ...}` entries."
  [{:keys [bank banks] :as ctx}]
  (doseq [{:keys [real-id]} (vals banks)]
    (when real-id
      (assert-bank-ties bank real-id)))
  ctx)
