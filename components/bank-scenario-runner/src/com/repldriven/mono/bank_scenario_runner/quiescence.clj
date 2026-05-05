(ns com.repldriven.mono.bank-scenario-runner.quiescence
  "Read-side catch-up helpers. Verbs that trigger asynchronous
  watcher chains (currently `:create-person-party` and
  `:activate-party` — both depend on the bank-idv → bank-onfido
  chain to flip the party to `:active`) call into here to wait
  until production has caught up to the model's expected state
  before the next verb runs.

  Synchronous verbs (`:create-org`, `:create-product`,
  `:outbound-payment`, etc.) are committed-then-returned and don't
  need quiescence."
  (:require
    [com.repldriven.mono.bank-party.interface :as party]

    [com.repldriven.mono.error.interface :as error]))

(def ^:private default-deadline-ms 5000)
(def ^:private poll-interval-ms 25)

(defn wait-for-party-active
  "Polls `(party/get-party bank organization-id party-id)` until
  the party reaches `:party-status-active`, or `deadline-ms` is
  hit. Returns `:quiescent` on success, an
  `(error/fail :scenario/quiescence-timeout)` anomaly on timeout.
  Used by verbs that drive the IDV chain."
  ([bank organization-id party-id]
   (wait-for-party-active bank organization-id party-id default-deadline-ms))
  ([bank organization-id party-id deadline-ms]
   (let [deadline (+ (System/currentTimeMillis) deadline-ms)]
     (loop []
       (let [party (party/get-party bank organization-id party-id)
             status (when-not (error/anomaly? party) (:status party))]
         (cond
          (= :party-status-active status)
          :quiescent

          (>= (System/currentTimeMillis) deadline)
          (error/fail :scenario/quiescence-timeout
                      {:message "Party did not become active"
                       :organization-id organization-id
                       :party-id party-id
                       :status status})

          :else
          (do (Thread/sleep poll-interval-ms) (recur))))))))

(defn wait
  "End-of-trial catch-up hook. The per-verb waits in `verbs.clj`
  already settle the IDV chain so this is a no-op for now —
  reserved as a hook for future async verbs."
  [_bank]
  :quiescent)
