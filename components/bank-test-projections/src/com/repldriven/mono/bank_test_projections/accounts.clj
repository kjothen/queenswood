(ns com.repldriven.mono.bank-test-projections.accounts
  "Per-account associations — `:org`, `:product`, `:party` —
  reverse-mapped to model-ids. Catches the case where an account
  exists in real but is registered against a different org, party,
  or product than the model expects (or vice versa). The balance
  itself is covered by `bank-test-projections.balances/project-
  balances`; this projection is about *who owns what*."
  (:require
    [com.repldriven.mono.bank-cash-account.interface :as cash-accounts]))

(defn- reverse-by-real-id
  "From `model-id → {:real-id ...}`, builds `real-id → model-id`."
  [m]
  (into {} (map (fn [[mid {:keys [real-id]}]] [real-id mid])) m))

(defn project-accounts
  "For each account the runner has tracked in `:id-mapping`, fetches
  the real account and reports its `:org` / `:product` / `:party`
  associations as model-ids. Returns
  `{model-acct-id {:org :model-org :product :model-prod :party :model-party}}`.

  The runner's `:accounts` side-table already records `:org` per
  model-acct, but reading from the real bank ensures we catch
  cases where the runner's intent and reality drifted (e.g. the
  account was opened against a different product after a partial
  failure)."
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
                    :party (party-real->model (:party-id account))}])))
         (into {}))))

(defn project-model-accounts
  "Reads the same shape from the model state. Returns
  `{model-acct-id {:org :model-org :product :model-prod :party :model-party}}`."
  [model-state]
  (->> (:accounts model-state)
       (map (fn [[acct-id {:keys [org product party]}]]
              [acct-id {:org org :product product :party party}]))
       (into {})))
