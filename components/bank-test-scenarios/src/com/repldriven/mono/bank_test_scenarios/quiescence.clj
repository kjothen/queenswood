(ns com.repldriven.mono.bank-test-scenarios.quiescence
  (:require
    [com.repldriven.mono.bank-party.interface :as party]

    [com.repldriven.mono.error.interface :as error]))

(def ^:private default-deadline-ms 5000)
(def ^:private poll-interval-ms 25)

(defn wait-for-party-active
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
  [_bank]
  :quiescent)
