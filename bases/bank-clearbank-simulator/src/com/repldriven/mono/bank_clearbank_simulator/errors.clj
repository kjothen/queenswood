(ns com.repldriven.mono.bank-clearbank-simulator.errors
  (:require
    [com.repldriven.mono.error.interface :as error]))

(defn error-response
  "Builds an RFC 9457-shaped ErrorResponse body.

  Two-arity form derives fields from an anomaly.
  Four-arity form takes explicit title, type, and detail."
  ([status anomaly]
   (cond-> {:title (cond (error/unauthorized? anomaly)
                         "UNAUTHORIZED"
                         (error/rejection? anomaly)
                         "REJECTED"
                         :else
                         "FAILED")
            :type (str (error/kind anomaly))
            :status status}
           (:message (error/payload anomaly))
           (assoc :detail
                  (:message (error/payload anomaly)))))
  ([status title type detail]
   (cond-> {:title title :type type :status status}
           detail
           (assoc :detail detail))))
