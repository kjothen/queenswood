(ns com.repldriven.mono.bank-cash-account-product.validation
  (:require
    [com.repldriven.mono.error.interface :as error]

    [clojure.string :as str]))

(def ^:private unique-fields
  [:balance-products :allowed-currencies :allowed-payment-address-schemes])

(defn- has-duplicates?
  [xs]
  (and (seq xs) (not (apply distinct? xs))))

(defn unique-fields?
  [data]
  (let [dup-fields (filterv (fn [k] (has-duplicates? (get data k)))
                            unique-fields)]
    (if (empty? dup-fields)
      true
      (error/reject :cash-account-product/duplicate-items
                    {:message (str "Duplicate items in: "
                                   (str/join ", " (map name dup-fields)))
                     :fields dup-fields}))))
