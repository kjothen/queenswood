(ns com.repldriven.queenswood.idv.domain
  (:require
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]))

(defn- guard-source-status
  [idv message allowed]
  (when-not (contains? allowed (:status idv))
    (error/reject :idv/invalid-status
                  {:message message
                   :verification-id (:verification-id idv)
                   :status (:status idv)
                   :allowed allowed})))

(defn new-idv
  [data]
  (let [{:keys [bank-id party-id]} data
        now (utility/now)]
    {:bank-id bank-id
     :party-id party-id
     :verification-id (utility/generate-id "idv")
     :status :idv-status-pending
     :created-at now
     :updated-at now}))

(defn in-review-idv
  [idv]
  (let-nom>
    [_ (guard-source-status idv
                            "IDV is not awaiting a result"
                            #{:idv-status-pending})]
    (assoc idv
           :status :idv-status-in-review
           :updated-at (utility/now))))

(defn accepted-idv
  [idv]
  (let-nom>
    [_ (guard-source-status idv
                            "IDV is not in a status that can be accepted"
                            #{:idv-status-pending :idv-status-in-review})]
    (assoc idv
           :status :idv-status-accepted
           :completed-at (utility/now)
           :updated-at (utility/now))))

(defn rejected-idv
  [idv]
  (let-nom>
    [_ (guard-source-status idv
                            "IDV is not in a status that can be rejected"
                            #{:idv-status-pending :idv-status-in-review})]
    (assoc idv
           :status :idv-status-rejected
           :completed-at (utility/now)
           :updated-at (utility/now))))

(defn failed-idv
  [idv]
  (let-nom>
    [_ (guard-source-status idv
                            "IDV is not in a status that can fail"
                            #{:idv-status-pending :idv-status-in-review})]
    (assoc idv
           :status :idv-status-failed
           :completed-at (utility/now)
           :updated-at (utility/now))))
