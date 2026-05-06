(ns com.repldriven.mono.bank-test-projections.accounts
  (:require
    [com.repldriven.mono.bank-cash-account.interface :as cash-accounts]))

(defn- reverse-by-real-id
  [m]
  (into {} (map (fn [[mid {:keys [real-id]}]] [real-id mid])) m))

(defn- normalise-status
  [status]
  (case status
    :cash-account-status-opening :open
    :cash-account-status-opened :open
    :cash-account-status-closing :closed
    :cash-account-status-closed :closed))

(defn project-accounts
  [bank ctx]
  (let [{:keys [id-mapping accounts orgs products parties]} ctx
        org-real->model (reverse-by-real-id orgs)
        prod-real->model (reverse-by-real-id products)
        party-real->model (reverse-by-real-id parties)]
    (->> (:real->model id-mapping)
         (map (fn [[real-acct-id model-acct-id]]
                (let [model-org (get-in accounts [model-acct-id :org])
                      org-real-id (get-in orgs [model-org :real-id])
                      account (cash-accounts/get-account bank
                                                         org-real-id
                                                         real-acct-id)]
                  [model-acct-id
                   {:org (org-real->model (:organization-id account))
                    :product (prod-real->model (:product-id account))
                    :party (party-real->model (:party-id account))
                    :status (normalise-status (:account-status account))}])))
         (into {}))))

(defn project-model-accounts
  [model-state]
  (->> (:accounts model-state)
       (map (fn [[acct-id {:keys [org product party status]}]]
              [acct-id
               {:org org
                :product product
                :party party
                :status status}]))
       (into {})))
