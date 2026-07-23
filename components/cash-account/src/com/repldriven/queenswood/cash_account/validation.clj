(ns com.repldriven.queenswood.cash-account.validation
  (:require
    [com.repldriven.mono.error.interface :as error]))

(defn valid-product?
  [product]
  (let [{:keys [status version-id]} product]
    (if (not= :cash-account-product-status-published status)
      (error/reject :cash-account-product/not-published
                    {:message "Product version is not published"
                     :version-id version-id
                     :status status})
      true)))

(defn valid-currency?
  [currency product]
  (let [allowed (:allowed-currencies product)]
    (if (and (seq allowed)
             (not (some #{currency} allowed)))
      (error/reject :cash-account/invalid-currency
                    "Currency not allowed for this product")
      true)))

(defn- enum-suffix
  [kw prefix]
  (subs (name kw) (inc (count (name prefix)))))

(defn valid-party?
  [party]
  (let [status (:status party)]
    (if (not= :party-status-active status)
      (error/reject :cash-account/party-status
                    (str "Party is " (enum-suffix status :party-status)))
      true)))
