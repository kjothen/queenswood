(ns com.repldriven.mono.bank-cash-account-product.validation
  "Domain-level validation predicates for cash-account-product
  data — invariants that must hold regardless of how the data
  reached the domain (API coercion, org bootstrap, migration).
  Returns `true` when happy, rejection anomaly otherwise, so
  callers can chain through `let-nom>`."
  (:require
    [com.repldriven.mono.error.interface :as error]

    [clojure.string :as str]))

(def ^:private unique-fields
  "Version fields whose items must be distinct. Mirrors the
  `:unique-vector` schemas in the API request body."
  [:balance-products :allowed-currencies :allowed-payment-address-schemes])

(defn- has-duplicates?
  [xs]
  (and (seq xs) (not (apply distinct? xs))))

(defn unique-fields?
  "Rejects when any repeated-value field on a version holds
  duplicate items. Returns true when all fields are distinct."
  [data]
  (let [dup-fields (filterv (fn [k] (has-duplicates? (get data k)))
                            unique-fields)]
    (if (empty? dup-fields)
      true
      (error/reject :cash-account-product/duplicate-items
                    {:message (str "Duplicate items in: "
                                   (str/join ", " (map name dup-fields)))
                     :fields dup-fields}))))
