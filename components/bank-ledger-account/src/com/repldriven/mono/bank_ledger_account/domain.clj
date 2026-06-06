(ns com.repldriven.mono.bank-ledger-account.domain
  (:require
    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]))

(def control-code-for-product-type
  "Maps a cash-account product type to the `:gl-code` of the control
  ledger account its balance rolls up into. Postings on those accounts
  fan out to the matching control so the control balance is always the
  live roll-up of its sub-ledger. Customer deposits roll into
  2100/2200/2300; the bank's own funding account rolls into 3100."
  {:product-type-sub-ledger-current "2100"
   :product-type-sub-ledger-savings "2200"
   :product-type-sub-ledger-term-deposit "2300"
   :product-type-sub-ledger-own-funds "3100"})

(defn new-ledger-account
  "Build a `LedgerAccount` map for one template `row` in `currency`,
  stamping a fresh `led.` id and timestamps. Gated on the
  `:ledger-account` open capability in `policies` (opening a ledger
  account mirrors opening a cash account), so a tier that denies it
  (e.g. micro) cannot mint ledger accounts; returns the account map or
  the deny anomaly."
  [bank-id currency row policies]
  (let-nom>
    [_ (policy/check-capability policies
                                :ledger-account
                                {:action :ledger-account-action-open})]
    (let [now (utility/now)]
      (assoc (select-keys row
                          [:gl-code :name :gl-account-type
                           :gl-account-class :required])
             :bank-id bank-id
             :currency currency
             :ledger-account-id (utility/generate-id "led")
             :created-at now
             :updated-at now))))

(defn opening-balance
  "The single default-posted balance bucket a ledger account opens
  with. Carries no `:product-type` — that is a sub-ledger-only
  concept."
  [ledger-account]
  {:account-id (:ledger-account-id ledger-account)
   :balance-type :balance-type-default
   :balance-status :balance-status-posted
   :currency (:currency ledger-account)})

(defn fans-out?
  "Only customer-deposit legs touching the default-posted bucket roll
  up into a control. Non-default buckets (interest-accrued,
  interest-paid) and non-posted statuses are sub-ledger-only and don't
  have a control counterpart."
  [leg]
  (and (= :balance-type-default (:balance-type leg))
       (= :balance-status-posted (:balance-status leg))))
