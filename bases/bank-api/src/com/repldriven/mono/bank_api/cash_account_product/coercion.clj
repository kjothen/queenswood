(ns com.repldriven.mono.bank-api.cash-account-product.coercion
  (:require
    [com.repldriven.mono.bank-api.coercion :as coercion]))

(def ^:private product-type-enum
  (coercion/enum-coercion {"current" :product-type-sub-ledger-current
                           "savings" :product-type-sub-ledger-savings
                           "term-deposit" :product-type-sub-ledger-term-deposit
                           "own-funds" :product-type-sub-ledger-own-funds
                           "general-ledger" :product-type-general-ledger}
                          :product-type-unknown))

(def ^:private balance-sheet-side-enum
  (coercion/enum-coercion {"asset" :balance-sheet-side-asset
                           "liability" :balance-sheet-side-liability}
                          :balance-sheet-side-unknown))

(def ^:private payment-address-scheme-enum
  (coercion/enum-coercion {"scan" :payment-address-scheme-scan
                           "iban" :payment-address-scheme-iban
                           "swift" :payment-address-scheme-swift
                           "ach" :payment-address-scheme-ach}
                          :payment-address-scheme-unknown))

(def ^:private version-status-enum
  (coercion/enum-coercion {"draft" :cash-account-product-status-draft
                           "published" :cash-account-product-status-published
                           "discarded" :cash-account-product-status-discarded}
                          :cash-account-product-status-unknown))

(def product-type-enum-schema (:enum-schema product-type-enum))
(def balance-sheet-side-enum-schema (:enum-schema balance-sheet-side-enum))
(def payment-address-scheme-enum-schema
  (:enum-schema payment-address-scheme-enum))
(def version-status-enum-schema (:enum-schema version-status-enum))

(defn request->sub-ledger
  "Lift the API's flat request body into the `:kind :sub-ledger`
  discriminator the domain expects. The public API only creates
  sub-ledger products; GL rows are seeded internally by
  chart-of-accounts."
  [body]
  (let [{:keys [product-type interest-rate-bps iso-cash-account-type]} body]
    (-> (dissoc body :product-type :interest-rate-bps :iso-cash-account-type)
        (assoc :kind
               {:sub-ledger
                (cond-> {:product-type product-type}
                        interest-rate-bps
                        (assoc :interest-rate-bps interest-rate-bps)

                        iso-cash-account-type
                        (assoc :iso-cash-account-type
                               iso-cash-account-type))}))))

(defn version->response
  "Flatten a stored version's `:kind` discriminator back into the
  API's flat shape. Sub-ledger versions lift product-type and friends
  to the top level; GL versions never reach the public API surface."
  [version]
  (if-let [sub (get-in version [:kind :sub-ledger])]
    (-> version
        (dissoc :kind)
        (merge sub))
    version))

(defn product->response
  "Apply `version->response` across every version in a product
  aggregate."
  [product]
  (update product :versions (partial mapv version->response)))

(defn sub-ledger?
  "Sub-ledger products are the only thing the public API surfaces.
  GL rows live behind chart-of-accounts and are filtered out before
  list/get responses."
  [product]
  (some? (get-in (peek (:versions product)) [:kind :sub-ledger])))
