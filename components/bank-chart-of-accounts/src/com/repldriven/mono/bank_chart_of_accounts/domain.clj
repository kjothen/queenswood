(ns com.repldriven.mono.bank-chart-of-accounts.domain)

(def template
  "The canonical chart of accounts every customer bank starts with.
  Each row defines one bank-owned `CashAccount`, instantiated per
  currency at bank-creation time. The `:gl-code` is the
  three-or-four-digit account number a bookkeeper would recognise;
  the GL discriminators (`:gl-account-type`, `:gl-account-class`,
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
  "Customer cash accounts inherit a `:gl-control-account-id` from their
  product type — payments and interest postings on customer accounts
  fan out to the matching control GL account so the control balance
  is always the live roll-up of its sub-ledger."
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
