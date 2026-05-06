(ns com.repldriven.mono.bank-test-projections.parties
  (:require
    [com.repldriven.mono.bank-party.interface :as party]))

(defn- bare-status
  [v]
  (when v
    (keyword (subs (name v) (count "party-status-")))))

(defn project-parties
  [bank model->real]
  (->> model->real
       (map (fn [[model-id {:keys [real-id org-real-id]}]]
              [model-id
               (bare-status (:status (party/get-party
                                      bank
                                      org-real-id
                                      real-id)))]))
       (into {})))

(defn project-model-parties
  [model-state]
  (update-vals (:parties model-state) :status))
