(ns com.repldriven.mono.bank-cash-account.validation
  "Domain-level validation predicates for cash-account data —
  intrinsic invariants (product must be published, currency must be
  allowed by product, party must be active). Returns `true` when
  happy, rejection anomaly otherwise, so callers can chain through
  `let-nom>`."
  (:require
    [com.repldriven.mono.error.interface :as error]))

(defn valid-product?
  "Returns true if the product version is published, rejection
  anomaly otherwise."
  [product]
  (let [{:keys [status version-id]} product]
    (if (not= :cash-account-product-version-status-published status)
      (error/reject :cash-account-product/not-published
                    {:message "Product version is not published"
                     :version-id version-id
                     :status status})
      true)))

(defn valid-currency?
  "Returns true if currency is allowed by product, rejection
  anomaly otherwise."
  [currency product]
  (let [allowed (:allowed-currencies product)]
    (if (and (seq allowed)
             (not (some #{currency} allowed)))
      (error/reject :cash-account/invalid-currency
                    "Currency not allowed for this product")
      true)))

(defn- enum-suffix
  "Strips a keyword prefix from a keyword name.
  (enum-suffix :party-status-active :party-status)
  => \"active\""
  [kw prefix]
  (subs (name kw) (inc (count (name prefix)))))

(defn valid-party?
  "Returns true if party is active, rejection anomaly otherwise."
  [party]
  (let [status (:status party)]
    (if (not= :party-status-active status)
      (error/reject :cash-account/party-status
                    (str "Party is " (enum-suffix status :party-status)))
      true)))
