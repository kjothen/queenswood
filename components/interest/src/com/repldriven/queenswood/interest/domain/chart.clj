(ns com.repldriven.queenswood.interest.domain.chart
  (:require
    [com.repldriven.queenswood.ledger-account.interface :as ledger-accounts]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- account-id
  "The id of the bank's ledger account for `gl-account-code`, or a
  rejection naming the code it has no account for. A bank whose chart
  cannot take a posting the run has to make is a chart-of-accounts
  problem the run cannot work around."
  [chart bank-id gl-account-code]
  (if-some [account (first (filter (fn [a]
                                     (= gl-account-code (:gl-account-code a)))
                                   chart))]
    (:ledger-account-id account)
    (error/reject :interest/missing-gl-account
                  {:message "Bank has no such account in its chart"
                   :bank-id bank-id
                   :gl-account-code gl-account-code})))

(defn- deposit-controls
  "Every product type that rolls up into a control, against the id of
  the control it rolls into. Taken from the ledger's own mapping rather
  than a list of the product types that pay interest today, so a
  product that starts paying tomorrow already has somewhere to land."
  [chart bank-id]
  (reduce (fn [acc [product-type gl-account-code]]
            (let [id (account-id chart bank-id gl-account-code)]
              (if (error/anomaly? id)
                (reduced id)
                (assoc acc product-type id))))
          {}
          ledger-accounts/product-type->control-code))

(defn accrual-accounts
  "What an accrual run posts between: interest expense and the
  interest-payable control. Two fixed roles — an account's product type
  makes no difference to where the bank's side of an accrual lands."
  [chart bank-id]
  (let-nom>
    [expense (account-id chart bank-id :gl-account-code-interest-expense)
     payable (account-id chart bank-id :gl-account-code-interest-payable)]
    {:expense expense :payable payable}))

(defn capitalization-accounts
  "What a capitalisation run posts between: the interest-payable
  control it clears, and the deposit control each earning product type
  rolls into. The credit side differs by product type, which is why
  capitalisation groups by it and accrual does not."
  [chart bank-id]
  (let-nom>
    [payable (account-id chart bank-id :gl-account-code-interest-payable)
     controls (deposit-controls chart bank-id)]
    {:payable payable :controls controls}))
