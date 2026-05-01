(ns com.repldriven.mono.bank-test-projections.payments
  "Outbound-payment record projections — for each payment the
  runner created via `:outbound-payment`, reads its current state
  from the real bank and reports the status. The bank-payment
  store doesn't expose a list API, so the runner has to track
  payment-ids; the projection iterates those.

  Status currently stays `:pending` in tests (the scheme adapter
  isn't running in this harness), but the projection still catches
  failures-to-record: a missing payment-id in real means
  `submit-outbound` silently dropped the record."
  (:require
    [com.repldriven.mono.bank-payment.interface :as payment]))

(defn- bare-status
  "Strips the `:outbound-payment-status-` prefix so model and real
  produce comparable keywords."
  [v]
  (when v
    (keyword (subs (name v) (count "outbound-payment-status-")))))

(defn project-outbound-payments
  "For each entry in `model->real` (`{model-payment-id {:real-id <id>}}`),
  fetches the OutboundPayment from the real bank and returns
  `{model-payment-id :pending|:completed|...}`. A nil status
  signals the payment record is missing in real."
  [bank model->real]
  (->> model->real
       (map (fn [[model-id {:keys [real-id]}]]
              [model-id
               (bare-status (:payment-status
                             (payment/get-outbound-payment
                              bank
                              real-id)))]))
       (into {})))

(defn project-model-outbound-payments
  "Reads payment statuses out of model state. Returns
  `{model-payment-id :pending|...}`."
  [model-state]
  (update-vals (:payments model-state) :status))
