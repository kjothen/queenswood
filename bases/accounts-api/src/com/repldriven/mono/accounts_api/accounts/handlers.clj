(ns com.repldriven.mono.accounts-api.accounts.handlers
  (:require
    [com.repldriven.mono.accounts-api.accounts.cursor :as cursor]

    [com.repldriven.mono.avro.interface :as avro]
    [com.repldriven.mono.command.interface :as command]
    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.fdb.interface :as fdb]
    [com.repldriven.mono.schema.interface :as schema])
  (:import
    (java.time Instant)))

(def ^:private response-schema
  {"open-account" "account"
   "close-account" "account"
   "get-account-status" "account-status"})

(defn- decode-payload
  [schemas command-name result]
  (if-let [payload (:payload result)]
    (let [schema (get schemas (get response-schema command-name))
          decoded (avro/deserialize-same schema payload)]
      (if (error/anomaly? decoded) decoded (assoc result :payload decoded)))
    result))

(defn- send-command
  [request command-name data]
  (let [dispatcher (:command-dispatcher request)
        schemas (:avro request)
        schema (get schemas command-name)]
    (if-not schema
      {:status 400
       :body (command/req->command-response
              request
              (error/fail :accounts-api/unknown-command
                          {:message "No Avro schema for command"
                           :command command-name}))}
      (let [result (error/let-nom> [payload (avro/serialize schema data)]
                     (command/send dispatcher
                                   (command/req->command-request request
                                                                 command-name
                                                                 payload)))]
        (cond
         (= (error/kind result) :command/timeout)
         {:status 408
          :body (command/req->command-response request
                                               result)}

         (error/anomaly? result)
         {:status 500
          :body (command/req->command-response request
                                               result)}

         (= "REJECTED" (:status result))
         {:status 422 :body result}

         :else
         {:status 200
          :body (decode-payload schemas
                                command-name
                                result)})))))

(defn open-account
  [request]
  (send-command request "open-account" (:body-params request)))

(defn close-account
  [request]
  (let [{:keys [account-id]} (:path-params request)]
    (send-command request "close-account" {:account-id account-id})))

(defn get-account-status
  [request]
  (let [{:keys [account-id]} (:path-params request)]
    (send-command request "get-account-status" {:account-id account-id})))

(defn- millis->iso [ms] (when (pos? ms) (str (Instant/ofEpochMilli ms))))

(defn- format-timestamps
  [account]
  (-> account
      (update :created-at-ms millis->iso)
      (update :updated-at-ms millis->iso)))

(def ^:private default-page-size 20)
(def ^:private max-page-size 100)

(defn- parse-page-size
  [s]
  (let [n (when s
            (try (Integer/parseInt s) (catch NumberFormatException _ nil)))]
    (cond
     (nil? n)
     default-page-size
     (< n 1)
     1
     (> n max-page-size)
     max-page-size
     :else
     n)))

(defn- build-links
  [{:keys [accounts has-more after before]}]
  (let [base "/v1/accounts"
        first-id (:account-id (first accounts))
        last-id (:account-id (peek accounts))
        forward? (some? after)
        backward? (some? before)]
    (cond-> {}
      (or (and (not backward?) has-more) backward?)
      (assoc :next (str base "?page[after]=" (cursor/encode last-id)))
      (or forward? (and backward? has-more))
      (assoc :prev (str base "?page[before]=" (cursor/encode first-id))))))

(defn list-accounts
  [request]
  (let [{:keys [record-db record-store]} request
        query-params (:query-params request)
        after-cursor (get query-params "page[after]")
        before-cursor (get query-params "page[before]")
        size (parse-page-size (get query-params "page[size]"))
        after-id (cursor/decode after-cursor)
        before-id (cursor/decode before-cursor)
        result (fdb/transact record-db
                             record-store
                             "accounts"
                             (fn [store]
                               (fdb/scan-records store
                                                 {:after after-id
                                                  :before before-id
                                                  :limit size})))]
    (if (error/anomaly? result)
      {:status 500 :body {:error "Failed to list accounts"}}
      (let [accounts (mapv (comp format-timestamps schema/pb->Account)
                           (:records result))
            links (when (seq accounts)
                    (build-links {:accounts accounts
                                  :has-more (:has-more result)
                                  :after after-id
                                  :before before-id}))]
        {:status 200
         :body (cond-> {:accounts accounts}
                 (seq links) (assoc :links links))}))))
