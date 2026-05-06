(ns com.repldriven.mono.bank-test-model.parties
  (:require
    [com.repldriven.mono.bank-test-model.state :as state]

    [clojure.test.check.generators :as gen]))

(defn- ni-arg-gen
  [state]
  (let [fresh (state/next-ni-id state)
        existing (vec (mapcat seq (vals (:nis-by-org state))))]
    (gen/frequency
     (cond-> [[5 (gen/return nil)]
              [3 (gen/return fresh)]]

             (seq existing)
             (conj [2 (gen/elements existing)])))))

(def create-person-party
  {:run? (fn [state] (seq (state/known-orgs state)))
   :args (fn [state]
           (gen/tuple (gen/elements (state/known-orgs state))
                      (ni-arg-gen state)))
   :next-state
   (fn [state {[org-id ni-marker] :args}]
     (let [duplicate? (and ni-marker
                           (contains? (get-in state [:nis-by-org org-id])
                                      ni-marker))
           fresh-ni? (and ni-marker (= ni-marker (state/next-ni-id state)))
           party-id (state/next-party-id state)]
       (cond->
        state

        true
        (update :next-party-id inc)

        fresh-ni?
        (update :next-ni-id inc)

        (and ni-marker (not duplicate?))
        (update-in [:nis-by-org org-id]
                   (fnil conj #{})
                   ni-marker)

        (not duplicate?)
        (-> (assoc-in [:parties party-id]
                      {:org org-id
                       :type :person
                       :status :active})
            (update-in [:orgs org-id :parties]
                       (fnil conj [])
                       party-id)))))
   :valid? (fn [state {[org-id] :args}] (contains? (:orgs state) org-id))})

(def activate-party
  {:run? (fn [state] (seq (state/pending-parties state)))
   :args (fn [state] (gen/tuple (gen/elements (state/pending-parties state))))
   :next-state (fn [state {[party-id] :args}]
                 (assoc-in state [:parties party-id :status] :active))
   :valid? (fn [state {[party-id] :args}]
             (= :pending (get-in state [:parties party-id :status])))})
