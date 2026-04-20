(ns com.repldriven.mono.bank-cash-account-product.domain
  (:require
    [com.repldriven.mono.encryption.interface :as encryption]))

(defn new-version
  "Creates a new CashAccountProductVersion record map in
  draft status."
  [organization-id product-id version-number data]
  (let [{:keys [name product-type balance-sheet-side
                allowed-currencies balance-products
                allowed-payment-address-schemes
                interest-rate-bps valid-from valid-to]}
        data
        now (System/currentTimeMillis)]
    (cond-> {:organization-id organization-id
             :product-id product-id
             :version-id (encryption/generate-id "prv")
             :version-number version-number
             :status :cash-account-product-version-status-draft
             :name name
             :product-type product-type
             :balance-sheet-side balance-sheet-side
             :interest-rate-bps (or interest-rate-bps 0)
             :created-at now
             :updated-at now}

            (seq allowed-currencies)
            (assoc :allowed-currencies allowed-currencies)

            valid-from
            (assoc :valid-from valid-from)

            valid-to
            (assoc :valid-to valid-to)

            (seq balance-products)
            (assoc :balance-products balance-products)

            (seq allowed-payment-address-schemes)
            (assoc :allowed-payment-address-schemes
                   allowed-payment-address-schemes))))

(defn update-version
  "Replaces an existing draft's mutable fields with the
  given data. Preserves :organization-id, :product-id,
  :version-id, :version-number, :status and :created-at;
  bumps :updated-at. Optional fields with no value are
  omitted."
  [existing data]
  (let [{:keys [organization-id product-id version-id
                version-number status created-at]}
        existing
        {:keys [name product-type balance-sheet-side
                allowed-currencies balance-products
                allowed-payment-address-schemes
                interest-rate-bps valid-from valid-to]}
        data]
    (cond-> {:organization-id organization-id
             :product-id product-id
             :version-id version-id
             :version-number version-number
             :status status
             :name name
             :product-type product-type
             :balance-sheet-side balance-sheet-side
             :interest-rate-bps (or interest-rate-bps 0)
             :created-at created-at
             :updated-at (System/currentTimeMillis)}

            (seq allowed-currencies)
            (assoc :allowed-currencies allowed-currencies)

            valid-from
            (assoc :valid-from valid-from)

            valid-to
            (assoc :valid-to valid-to)

            (seq balance-products)
            (assoc :balance-products balance-products)

            (seq allowed-payment-address-schemes)
            (assoc :allowed-payment-address-schemes
                   allowed-payment-address-schemes))))

(defn publish
  "Sets version status to published."
  [version]
  (assoc version
         :status :cash-account-product-version-status-published
         :updated-at (System/currentTimeMillis)))
