(ns com.repldriven.queenswood.clearbank-relay.outbound
  (:require
    [com.repldriven.queenswood.clearbank-relay.intent :as intent]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.http-client.interface :as http]
    [com.repldriven.mono.log.interface :as log]))

(def ^:private default-poll-ms 200)
(def ^:private default-max-attempts 10)

(defn- post-fps
  [clearbank-url request-body]
  (error/try-nom
   :clearbank-outbound/http
   "Failed to POST outbound payment to ClearBank"
   (http/request {:method :post
                  :url (str clearbank-url "/v3/payments/fps")
                  :headers {"Content-Type" "application/json"}
                  :body request-body})))

(defn- relay-one
  "Make the outbound FPS call for one intent OUTSIDE any FDB transaction,
  then record the outcome. On 2xx mark it sent; on failure bump the
  attempt count and, past the cap, fail it (a retried POST is safe —
  ClearBank dedupes on endToEndIdentification)."
  [config {:keys [intent-id request attempts]}]
  (let [{:keys [clearbank-url max-attempts]} config
        max-attempts (or max-attempts default-max-attempts)
        res (post-fps clearbank-url request)
        next-attempts (inc (or attempts 0))]
    (cond
     (and (map? res) (number? (:status res)) (< (:status res) 400))
     (intent/mark-sent config intent-id)

     (>= next-attempts max-attempts)
     (do (log/error "Outbound intent giving up after max attempts"
                    {:intent-id intent-id :attempts next-attempts :last res})
         (intent/mark-failed config intent-id next-attempts))

     :else
     (do (log/warn "Outbound intent POST failed; will retry"
                   {:intent-id intent-id :attempt next-attempts})
         (intent/mark-attempt config intent-id next-attempts)))))

(defn drain-once
  "Relay every pending intent once. Reads are transactional; the HTTP
  call and status write per intent are separate, so no network I/O
  happens inside an FDB transaction."
  [config]
  (let [pending (intent/pending-intents config)]
    (when-not (error/anomaly? pending)
      (doseq [i pending] (relay-one config i)))))

(defn start-runner
  "Start the daemon poll loop that drains pending outbound intents.
  Returns `{:stop fn}`."
  [config]
  (let [running (atom true)
        poll-ms (or (:poll-ms config) default-poll-ms)
        t (doto
            (Thread.
             (fn []
               (while @running
                 (try (drain-once config)
                      (catch Exception e
                        (log/error e "Outbound relay drain threw; continuing")))
                 (try (when @running (Thread/sleep poll-ms))
                      (catch InterruptedException _ (reset! running false))))))
            (.setDaemon true)
            (.setName "clearbank-outbound-relay")
            (.start))]
    {:stop (fn [] (reset! running false) (.interrupt t))}))
