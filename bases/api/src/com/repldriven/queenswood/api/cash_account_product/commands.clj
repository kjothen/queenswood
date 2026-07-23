(ns com.repldriven.queenswood.api.cash-account-product.commands
  (:require
    [com.repldriven.queenswood.api.commands :as commands]))

(defn- dispatcher
  [request]
  (let [{:keys [dispatchers]} request
        {:keys [cash-account-products]} dispatchers]
    cash-account-products))

(defn- version-uri
  [{:keys [product-id version-id]}]
  (str "/v1/cash-account-products/" product-id "/versions/" version-id))

(defn- created
  "Turn a successful command reply (`commands/send` returns 200 + body)
  into 201 + a Location to the new version. Error responses (4xx/5xx)
  pass through unchanged."
  [result]
  (if (= 200 (:status result))
    (-> result
        (assoc :status 201)
        (assoc-in [:headers "Location"] (version-uri (:body result))))
    result))

(defn- no-content
  "Turn a successful command reply into 204. Error responses pass
  through unchanged."
  [result]
  (if (= 200 (:status result))
    {:status 204}
    result))

(defn create-product
  [request]
  (let [{:keys [auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [body]} parameters]
    (created
     (commands/send (dispatcher request)
                    request
                    "create-cash-account-product"
                    "cash-account-product"
                    (assoc body :bank-id bank-id)))))

(defn open-draft
  [request]
  (let [{:keys [auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [path body]} parameters
        {:keys [product-id]} path]
    (created
     (commands/send (dispatcher request)
                    request
                    "open-cash-account-product-draft"
                    "cash-account-product"
                    (assoc body :bank-id bank-id :product-id product-id)))))

(defn update-draft
  [request]
  (let [{:keys [auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [path body]} parameters
        {:keys [product-id version-id]} path]
    (commands/send (dispatcher request)
                   request
                   "update-cash-account-product-draft"
                   "cash-account-product"
                   (assoc body
                          :bank-id bank-id
                          :product-id product-id
                          :version-id version-id))))

(defn discard-draft
  [request]
  (let [{:keys [auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [path]} parameters
        {:keys [product-id version-id]} path]
    (no-content
     (commands/send (dispatcher request)
                    request
                    "discard-cash-account-product-draft"
                    "cash-account-product"
                    {:bank-id bank-id
                     :product-id product-id
                     :version-id version-id}))))

(defn publish-draft
  [request]
  (let [{:keys [auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [path]} parameters
        {:keys [product-id version-id]} path]
    (commands/send (dispatcher request)
                   request
                   "publish-cash-account-product"
                   "cash-account-product"
                   {:bank-id bank-id
                    :product-id product-id
                    :version-id version-id})))
