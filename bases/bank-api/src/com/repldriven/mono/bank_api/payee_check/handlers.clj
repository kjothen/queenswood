(ns com.repldriven.mono.bank-api.payee-check.handlers
  (:require
    [com.repldriven.mono.bank-api.commands :as commands]))

(defn- dispatcher
  [request]
  (-> request
      :dispatchers
      :payee-checks))

(defn create-check
  [request]
  (let [{:keys [auth parameters]} request
        {:keys [bank-id]} auth
        {:keys [body]} parameters
        result (commands/send (dispatcher request)
                              request
                              "check-payee"
                              "payee-check"
                              (assoc body :bank-id bank-id))]
    (cond-> result
            (= 200 (:status result))
            (assoc :status 201))))
