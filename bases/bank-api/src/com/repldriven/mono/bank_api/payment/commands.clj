(ns com.repldriven.mono.bank-api.payment.commands
  (:require
    [com.repldriven.mono.bank-api.commands :as commands]))

(defn- dispatcher
  [request]
  (get-in request [:dispatchers :payments]))

(defn submit-internal-payment
  [request]
  (commands/send (dispatcher request)
                 request
                 "submit-internal-payment"
                 "internal-payment"
                 (assoc (get-in request [:parameters :body])
                        :organization-id
                        (get-in request [:auth :organization-id]))))

(defn submit-outbound-payment
  [request]
  (commands/send (dispatcher request)
                 request
                 "submit-outbound-payment"
                 "outbound-payment"
                 (assoc (get-in request [:parameters :body])
                        :organization-id
                        (get-in request [:auth :organization-id]))))
