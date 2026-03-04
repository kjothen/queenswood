(ns com.repldriven.mono.bank-api.errors
  (:require
    [com.repldriven.mono.error.interface :as error]))

(defn error-response
  "Builds an ErrorResponse body. Two-arity form derives fields
  from an anomaly or command response. Four-arity form takes
  explicit title, type, and detail."
  ([status result]
   (cond-> {:title (cond
                    (error/rejection? result)
                    "REJECTED"
                    (error/error? result)
                    "FAILED"
                    :else
                    (:status result))
            :type (cond
                   (error/anomaly? result)
                   (str (error/kind result))
                   :else
                   (:reason result))
            :status status}
     (or (and (error/anomaly? result) (:message (error/payload result)))
         (:message result))
     (assoc :detail
            (if (error/anomaly? result)
              (:message (error/payload result))
              (:message result)))))
  ([status title type detail]
   (cond-> {:title title :type type :status status}
     detail (assoc :detail detail))))
