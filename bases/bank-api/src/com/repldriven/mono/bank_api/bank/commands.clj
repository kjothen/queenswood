(ns com.repldriven.mono.bank-api.bank.commands
  (:require
    [com.repldriven.mono.bank-api.commands :as commands]
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-bank-query.interface :as banks]

    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.identity-provider.interface :as identity-provider]))

(defn- dispatcher
  [request]
  (let [{:keys [dispatchers]} request
        {:keys [banks]} dispatchers]
    banks))

(defn send-create-bank
  "Dispatch a create-bank command. `data` is the command payload
  (name/status/tier/currencies plus optional audience,
  company-binding, membership). Returns the `commands/send` ring
  response (200 + flat bank body on success)."
  [request data]
  (commands/send (dispatcher request) request "create-bank" "bank" data))

(defn bank-with-secret
  "Mint a fresh client secret for the bank — the command reply carries
  no credential, so it never sits on the bus — and load the bank
  enriched with its party and accounts. Returns the rich bank map with
  `:client-secret`, or an anomaly."
  [request bank-id]
  (let [{:keys [record-db record-store identity-provider]} request
        txn {:record-db record-db :record-store record-store}]
    (let-nom>
      [{:keys [client-secret]} (identity-provider/rotate-secret
                                identity-provider
                                bank-id)
       bank (banks/get-bank-view txn bank-id)]
      (assoc bank :client-secret client-secret))))

(defn create-bank
  [request]
  (let [{:keys [parameters audiences-by-status]} request
        {:keys [body]} parameters
        {:keys [status]} body
        ;; `audiences-by-status` is bank-api deployment config (sits
        ;; in server/interceptors next to `expected-audiences`). The
        ;; substrate IDP brick is naive about audience naming; the
        ;; handler resolves the per-status audience here and forwards
        ;; it through.
        result (send-create-bank request
                                 (assoc body
                                        :audience
                                        (get audiences-by-status
                                             status)))]
    (if (not= 200 (:status result))
      result
      (let [bank (bank-with-secret request (get-in result [:body :bank-id]))]
        (if (error/anomaly? bank)
          (errors/anomaly->response bank)
          {:status 201 :body bank})))))
