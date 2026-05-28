(ns com.repldriven.mono.bank-api.payment.commands
  (:require
    [com.repldriven.mono.bank-api.commands :as commands]
    [com.repldriven.mono.bank-api.payment.coercion :as coercion]))

(defn- dispatcher
  [request]
  (let [{:keys [dispatchers]} request
        {:keys [payments]} dispatchers]
    payments))

(defn submit-internal-payment
  [request]
  (let [{:keys [auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [body]} parameters]
    (commands/send (dispatcher request)
                   request
                   "submit-internal-payment"
                   "internal-payment"
                   (assoc body :bank-id bank-id))))

(defn submit-outbound-payment
  [request]
  (let [{:keys [auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [body]} parameters]
    (commands/send (dispatcher request)
                   request
                   "submit-outbound-payment"
                   "outbound-payment"
                   (-> body
                       (update :scheme coercion/encode-payment-scheme)
                       (assoc :bank-id bank-id)))))
