(ns com.repldriven.mono.bank-test-projections.payments
  "Payment-record projections — outbound by model-payment-id and
  inbound by stx-marker. Catches:

    - missing/extra outbound payments,
    - status drift (`:pending` vs `:completed`),
    - missing/extra inbound settlements,
    - settlement-event idempotency regressions (a duplicate
      stx-id leading to two records would surface as a divergence
      between model and real on the inbound projection).

  The bank-payment store doesn't list either kind of payment, so
  both projections iterate the runner's tracked markers/ids and
  do per-key lookups."
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
                             (payment/get-outbound-payment bank real-id)))]))
       (into {})))

(defn project-model-outbound-payments
  "Reads outbound payment statuses out of model state. Returns
  `{model-payment-id :pending|:completed}`."
  [model-state]
  (update-vals (:payments model-state) :status))

(defn- inbound-stx-id
  "The deterministic scheme-transaction-id the runner assigns
  to a model inbound marker (e.g. `:in-3`) for a given run.
  Mirrors the runner's :inbound-transfer dispatch."
  [run-id marker]
  (str "scen-in-" run-id "-" (name marker)))

(defn project-inbound-payments
  "For each marker the model has settled, looks the inbound
  payment up in the real bank by deterministic stx-id. Returns a
  set of markers actually present — equality with the model's
  `:inbound-payments` set catches missing settlements (a marker
  the runner attempted to settle but real has no record of) and
  spurious ones (real has a record the model didn't expect)."
  [bank run-id markers]
  (->> markers
       (filter (fn [marker]
                 (some? (payment/get-inbound-payment
                         bank
                         (inbound-stx-id run-id marker)))))
       set))

(defn project-model-inbound-payments
  "The set of inbound stx-markers the model has settled."
  [model-state]
  (:inbound-payments model-state))
