(ns com.repldriven.mono.bank-party.core
  (:require
    [com.repldriven.mono.bank-party.domain :as domain]
    [com.repldriven.mono.bank-party.store :as store]

    [com.repldriven.mono.bank-person-identification.interface :as person-id]
    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]))

(defn- create-person
  [txn data]
  (store/transact
   txn
   (fn [txn]
     (let [party (domain/new-party data)
           {:keys [national-identifier]} data
           {:keys [bank-id party-id status]} party
           pi (person-id/new-person-identification data party-id)]
       (let-nom>
         [_ (person-id/save-person-identification txn pi)
          _ (when national-identifier
              (store/save-party-national-identifier
               txn
               (domain/new-party-national-identifier
                national-identifier
                bank-id
                party-id)))
          result (store/save-party
                  txn
                  party
                  {:bank-id bank-id
                   :party-id party-id
                   :status-after status})]
         result)))))

(defn- create-internal
  [txn data]
  (store/transact
   txn
   (fn [txn]
     (let [party (domain/new-party data)
           {:keys [bank-id party-id status]} party]
       (store/save-party
        txn
        party
        {:bank-id bank-id
         :party-id party-id
         :status-after status})))))

(defn get-party
  [txn bank-id party-id]
  (let-nom> [party (store/get-party txn bank-id party-id)]
    (or party
        (error/reject :party/not-found
                      {:message "Party not found"
                       :bank-id bank-id
                       :party-id party-id}))))

(defn get-party-detail
  [txn bank-id party-id
   {:keys [person-identification address national-identifier]}]
  ;; Only the summary party read is load-bearing (not-found ⇒ 404). The
  ;; enrichment is opt-in per `embed` flag and best-effort: a failure
  ;; there must never break the party read the rest of the system (and
  ;; the IDV-status poll) depends on. Person identification carries both
  ;; the identity fields and the address, so one read serves both flags.
  (let-nom> [party (get-party txn bank-id party-id)]
    (let [pi (when (or person-identification address)
               (let [r (error/try-nom
                        :party/person-identification-read
                        "Failed to load person identification"
                        (person-id/get-person-identification txn party-id))]
                 (when-not (error/anomaly? r) r)))
          ni (when national-identifier
               (let [r (error/try-nom
                        :party/national-identifier-read
                        "Failed to load national identifier"
                        (store/get-party-national-identifier txn party-id))]
                 (when-not (error/anomaly? r) r)))]
      (cond-> party
              (and person-identification pi)
              (merge (select-keys pi
                                  [:given-name :middle-names :family-name
                                   :date-of-birth :nationality]))

              (and address pi)
              (assoc :address (:address pi))

              ni
              (assoc :national-identifier
                     ;; Stringify the enum `:type` — it leaves via a
                     ;; lenient map with no schema encoder, so hand JSON a
                     ;; plain string rather than a raw keyword.
                     (-> (select-keys ni [:type :value :issuing-country])
                         (update :type
                                 #(cond (keyword? %)
                                        (name %)
                                        (some? %)
                                        (str %)
                                        :else
                                        %))))))))

(defn new-party
  ([txn data]
   (new-party txn data {}))
  ([txn data opts]
   (let-nom>
     [policies (or (:policies opts)
                   (policy/get-effective-policies
                    txn
                    {:bank-id (:bank-id data)}))
      _ (policy/check-capability policies
                                 :party
                                 {:action :party-action-create
                                  :type (:type data)})]
     (let [result (if (= :party-type-person (:type data))
                    (create-person txn data)
                    (create-internal txn data))]
       (if (store/uniqueness-violation? result)
         (error/reject :party/identification-rejected
                       "Identification rejected for this party")
         result)))))
