(ns com.repldriven.mono.bank-ledger-account.domain
  (:require
    [com.repldriven.mono.bank-policy.interface :as policy]

    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]))

(def product-type->control-code
  "Maps a cash-account product type to the `:gl-code` of the control
  ledger account its *default* balance rolls up into. Postings on those
  accounts fan out to the matching control so the control balance is
  always the live roll-up of its sub-ledger. Customer deposits roll into
  2100/2200/2300; the bank's own funding account rolls into 3100."
  {:product-type-sub-ledger-current "2100"
   :product-type-sub-ledger-savings "2200"
   :product-type-sub-ledger-term-deposit "2300"
   :product-type-sub-ledger-own-funds "3100"})

(def balance-type->control-code
  "Maps a non-default customer balance bucket to the `:gl-code` of the
  control it rolls up into, regardless of product type. The customer
  `interest-accrued` buckets reconcile to 2400 Interest payable, the
  same way default buckets reconcile to the deposit controls. Checked
  ahead of `product-type->control-code`, so a savings account's accrued
  interest rolls into 2400, not 2200."
  {:balance-type-interest-accrued "2400"})

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
  with, tagged `:product-type-general-ledger` so read sites can tell the
  bank's own books from a customer instrument without inferring it from
  an absent product-type."
  [ledger-account]
  (let [{:keys [ledger-account-id :currency]} ledger-account]
    {:account-id ledger-account-id
     :product-type :product-type-general-ledger
     :balance-type :balance-type-default
     :balance-status :balance-status-posted
     :currency currency}))

(defn fans-out?
  "Posted customer legs that have a control counterpart roll up into it:
  default buckets into the product-type deposit control (2100/2200/2300/
  3100), interest-accrued buckets into 2400. Other buckets (interest-
  paid) and non-posted statuses are sub-ledger-only and don't fan out."
  [leg]
  (and (= :balance-status-posted (:balance-status leg))
       (contains? #{:balance-type-default :balance-type-interest-accrued}
                  (:balance-type leg))))

(defn debit-normal?
  "True for the debit-normal account families (asset, expense); false
  for the credit-normal ones (liability, equity, income). A trial
  balance places a debit-normal account's balance in the debit column
  and a credit-normal account's in the credit column."
  [gl-account-type]
  (contains? #{:gl-account-type-asset :gl-account-type-expense}
             gl-account-type))
