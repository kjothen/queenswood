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

(def create-person-party
  "Creates a pending person-party in an existing org. Args are
  `[org-id]`. Always eligible once at least one org exists."
  {:run? (fn [state] (seq (state/known-orgs state)))
   :args (fn [state] (gen/tuple (gen/elements (state/known-orgs state))))
   :next-state (fn [state {[org-id] :args}]
                 (let [party-id (state/next-party-id state)]
                   (->
                     state
                     (assoc-in [:parties party-id]
                               {:org org-id :type :person :status :pending})
                     (update-in [:orgs org-id :parties] (fnil conj []) party-id)
                     (update :next-party-id inc))))
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
