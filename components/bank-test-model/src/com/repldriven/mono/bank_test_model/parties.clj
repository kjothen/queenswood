(ns com.repldriven.mono.bank-test-model.parties
  "Party lifecycle command specs. `:create-person-party` makes a
  pending person-party in an org; `:activate-party` flips a pending
  party to active. The model treats parties as opaque ids carrying a
  `:type` (`:organization` or `:person`) and a `:status` (`:active`
  or `:pending`); the runner translates those into real
  `bank-party/new-party` and `bank-party/seed-active-party` calls."
  (:require
    [com.repldriven.mono.bank-test-model.state :as state]

    [clojure.test.check.generators :as gen]))

(defn- ni-arg-gen
  "Generator for the optional national-identifier marker. Picks
  among `nil` (no NI), the next fresh `:ni-N` (will be allocated on
  apply), or — if any NIs are already in use anywhere — an existing
  one. Existing markers may collide within an org (real bank rejects
  as duplicate) or be reused cross-org (real bank allows, since the
  uniqueness index is `(org, type, value)`)."
  [state]
  (let [fresh (state/next-ni-id state)
        existing (vec (mapcat seq (vals (:nis-by-org state))))]
    (gen/frequency
     (cond-> [[5 (gen/return nil)]
              [3 (gen/return fresh)]]

             (seq existing)
             (conj [2 (gen/elements existing)])))))

(def create-person-party
  "Creates a pending person-party in an existing org. Args are
  `[org-id ni-marker]` where `ni-marker` is `nil`, a fresh
  `:ni-N` marker, or an existing one. NI uniqueness is per-org —
  reusing an existing marker in the same org is rejected; reusing
  across orgs is allowed. Always eligible once at least one org
  exists."
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
                       :status :pending})
            (update-in [:orgs org-id :parties]
                       (fnil conj [])
                       party-id)))))
   :valid? (fn [state {[org-id] :args}] (contains? (:orgs state) org-id))})

(def activate-party
  "Transitions a pending party to `:active`. Args are `[party-id]`.
  Eligible only when at least one pending party exists."
  {:run? (fn [state] (seq (state/pending-parties state)))
   :args (fn [state] (gen/tuple (gen/elements (state/pending-parties state))))
   :next-state (fn [state {[party-id] :args}]
                 (assoc-in state [:parties party-id :status] :active))
   :valid? (fn [state {[party-id] :args}]
             (= :pending (get-in state [:parties party-id :status])))})
