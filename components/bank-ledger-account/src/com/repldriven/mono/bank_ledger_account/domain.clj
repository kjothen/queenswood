(ns com.repldriven.mono.bank-ledger-account.domain
  (:require
    [com.repldriven.mono.utility.interface :as utility]))

(def template
  "The canonical chart of accounts every customer bank starts with.
  Each row defines one bank-owned `LedgerAccount`, instantiated per
  currency at bank-creation time. The `:gl-code` is the
  three-or-four-digit account number a bookkeeper would recognise;
  the discriminators (`:gl-account-type`, `:gl-account-class`,
  `:required`) drive payment, interest, and reconciliation flows."
  [{:gl-code "1100"
    :name "Cash at correspondent"
    :gl-account-type :gl-account-type-asset
    :gl-account-class :gl-account-class-detail
    :required :required-mandatory}
   {:gl-code "1200"
    :name "Pending outbound payments"
    :gl-account-type :gl-account-type-asset
    :gl-account-class :gl-account-class-detail
    :required :required-mandatory}
   {:gl-code "2100"
    :name "Customer deposits - current"
    :gl-account-type :gl-account-type-liability
    :gl-account-class :gl-account-class-control
    :required :required-mandatory}
   {:gl-code "2200"
    :name "Customer deposits - savings"
    :gl-account-type :gl-account-type-liability
    :gl-account-class :gl-account-class-control
    :required :required-mandatory}
   {:gl-code "2300"
    :name "Customer deposits - term deposits"
    :gl-account-type :gl-account-type-liability
    :gl-account-class :gl-account-class-control
    :required :required-mandatory}
   {:gl-code "2400"
    :name "Interest payable"
    :gl-account-type :gl-account-type-liability
    :gl-account-class :gl-account-class-detail
    :required :required-mandatory}
   {:gl-code "2500"
    :name "Suspense - unreconciled inbound"
    :gl-account-type :gl-account-type-liability
    :gl-account-class :gl-account-class-detail
    :required :required-mandatory}])

(def control-code-for-product-type
  "Maps a customer cash-account product type to the `:gl-code` of the
  control ledger account its balance rolls up into. Postings on
  customer accounts fan out to the matching control so the control
  balance is always the live roll-up of its sub-ledger."
  {:product-type-sub-ledger-current "2100"
   :product-type-sub-ledger-savings "2200"
   :product-type-sub-ledger-term-deposit "2300"})

(def ^:private by-gl-code (into {} (map (juxt :gl-code identity)) template))

(defn mandatory?
  "True if `gl-code` names a mandatory seeded account, false
  otherwise (including for unknown gl-codes — only known seeded
  rows count as mandatory)."
  [gl-code]
  (= :required-mandatory (:required (get by-gl-code gl-code))))

(defn new-ledger-account
  "Build a `LedgerAccount` map for one template `row` in `currency`,
  stamping a fresh `led.` id and timestamps."
  [bank-id currency row]
  (let [now (utility/now)]
    (assoc (select-keys row
                        [:gl-code :name :gl-account-type
                         :gl-account-class :required])
           :bank-id bank-id
           :currency currency
           :ledger-account-id (utility/generate-id "led")
           :created-at now
           :updated-at now)))

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
