(ns com.repldriven.mono.bank-test-projections.orgs
  "Org-shape projections — for each tracked org, the *set* of
  accounts, products, and parties that belong to it, translated
  back to model ids. Catches structural drift: e.g. an account
  opened in production against an org the model didn't expect, or
  a product registered in production but missing from the model
  (or vice versa).

  Reads through `bank-cash-account/get-accounts`,
  `bank-cash-account-product/get-products`, and
  `bank-party/get-parties` — the same query path the API uses."
  (:require
    [com.repldriven.mono.bank-cash-account-product.interface :as products]
    [com.repldriven.mono.bank-cash-account.interface :as cash-accounts]
    [com.repldriven.mono.bank-party.interface :as party]))

(defn- model-ids
  "Translates a sequence of real-side records to a set of
  model-ids via `real->model` lookup, dropping anything not in
  the runner's mapping."
  [real->model real-id-key records]
  (into #{}
        (keep (fn [r] (real->model (real-id-key r))))
        records))

(defn project-orgs
  "Reads each tracked org's accounts, products and parties from the
  real bank, returning
  `{model-org-id {:accounts #{model-acct-ids}
                  :products #{model-prod-ids}
                  :parties  #{model-party-ids}}}`."
  [bank ctx]
  (let [{:keys [orgs id-mapping products parties]} ctx
        acct-real->model (:real->model id-mapping)
        prod-real->model (into {}
                               (map (fn [[m {:keys [real-id]}]] [real-id m]))
                               products)
        party-real->model (into {}
                                (map (fn [[m {:keys [real-id]}]] [real-id m]))
                                parties)]
    (->> orgs
         (map (fn [[model-org {:keys [real-id]}]]
                (let [accts (:accounts (cash-accounts/get-accounts
                                        bank
                                        real-id))
                      prods (:items (products/get-products bank real-id))
                      ptys (:parties (party/get-parties bank real-id))]
                  [model-org
                   {:accounts (model-ids acct-real->model :account-id accts)
                    :products (model-ids prod-real->model :product-id prods)
                    :parties (model-ids party-real->model :party-id ptys)}])))
         (into {}))))

(defn project-model-orgs
  "Reads each org's account/product/party membership from the
  model state, normalised to sets so equality with
  `project-orgs` is direct."
  [model-state]
  (->> (:orgs model-state)
       (map (fn [[org-id {:keys [accounts products parties]}]]
              [org-id
               {:accounts (set accounts)
                :products (set products)
                :parties (set parties)}]))
       (into {})))
