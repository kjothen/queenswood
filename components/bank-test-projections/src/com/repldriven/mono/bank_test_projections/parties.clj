(ns com.repldriven.mono.bank-test-projections.parties
  "Party-status projections — `:active` vs `:pending` per model-party-
  id. The auto-organization-party from `:open-account` is born
  active; a person-party from `:create-person-party` is born
  pending and only flips to `:active` after `:activate-party`."
  (:require
    [com.repldriven.mono.bank-party.interface :as party]))

(defn- bare-status
  "Strips the `:party-status-` prefix so the model and real sides
  produce comparable keywords. e.g. `:party-status-active` →
  `:active`."
  [v]
  (when v
    (keyword (subs (name v) (count "party-status-")))))

(defn project-parties
  "For each entry in `model->real`, reads the party from the real
  bank and reports `:active` or `:pending`. `model->real` is
  `{model-party-id {:real-id <id> :org-real-id <org>}}`."
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
  "Reads party statuses out of model state. Returns
  `{model-party-id :active|:pending}`."
  [model-state]
  (update-vals (:parties model-state) :status))
