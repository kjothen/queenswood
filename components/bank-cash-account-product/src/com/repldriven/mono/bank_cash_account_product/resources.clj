(ns com.repldriven.mono.bank-cash-account-product.resources
  "Static per-product-type defaults loaded once at namespace init
  from `bank-resources` classpath. The map fills in the derived
  fields (`:balance-sheet-side`, `:balance-products`,
  `:allowed-payment-address-schemes`, the menu of
  `:allowed-currencies`) on product creation and update — callers
  pick a name, a product-type, a single currency from the menu, and
  optionally an interest rate / valid-from."
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]))

(def ^:private product-types
  [:product-type-current
   :product-type-savings
   :product-type-term-deposit
   :product-type-settlement
   :product-type-internal])

(defn- type->resource
  [product-type]
  (let [stem (subs (name product-type) (count "product-type-"))]
    (str "bank/cash-account-products/" stem ".edn")))

(defn- load-one
  [product-type]
  (let [path (type->resource product-type)
        url (io/resource path)]
    (when (nil? url)
      (throw (ex-info "Cash-account-product defaults resource missing"
                      {:product-type product-type :path path})))
    (edn/read-string (slurp url))))

(def product-defaults
  "Map from product-type keyword to its derived-field defaults."
  (into {} (map (fn [t] [t (load-one t)])) product-types))
