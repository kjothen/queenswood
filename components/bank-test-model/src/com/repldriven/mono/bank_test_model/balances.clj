(ns com.repldriven.mono.bank-test-model.balances
  (:require
    [com.repldriven.mono.bank-test-model.state :as state]

    [clojure.test.check.generators :as gen]))

(def create-org
  {:args (fn [_state] (gen/return []))
   :next-state (fn [state _command]
                 (let [org-id (state/next-org-id state)
                       acct-id (state/next-id state)
                       prod-id (state/next-product-id state)
                       party-id (state/next-party-id state)]
                   (-> state
                       (assoc-in [:accounts acct-id]
                                 {:available 0
                                  :credit-carry 0
                                  :interest-accrued 0
                                  :status :open
                                  :org org-id
                                  :product prod-id
                                  :party party-id})
                       (assoc-in [:orgs org-id]
                                 {:accounts [acct-id]
                                  :products [prod-id]
                                  :parties [party-id]
                                  :settlement-account acct-id})
                       (assoc-in [:products prod-id]
                                 {:org org-id
                                  :product-type :settlement
                                  :interest-rate-bps 0
                                  :versions [{:status :published :number 1}]})
                       (assoc-in
                        [:parties party-id]
                        {:org org-id :type :organization :status :active})
                       (update :next-id inc)
                       (update :next-org-id inc)
                       (update :next-product-id inc)
                       (update :next-party-id inc))))})

(def close-account
  {:run? (fn [state] (seq (state/open-accounts state)))
   :args (fn [state] (gen/tuple (gen/elements (state/open-accounts state))))
   :next-state (fn [state {[acct-id] :args}]
                 (assoc-in state [:accounts acct-id :status] :closed))
   :valid? (fn [state {[acct-id] :args}]
             (= :open (get-in state [:accounts acct-id :status])))})
