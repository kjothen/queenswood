(ns com.repldriven.mono.bank-test-projections.orgs
  (:require
    [com.repldriven.mono.bank-cash-account-product.interface :as products]
    [com.repldriven.mono.bank-cash-account.interface :as cash-accounts]
    [com.repldriven.mono.bank-party.interface :as party]))

(defn- model-ids
  [real->model real-id-key records]
  (into #{}
        (keep (fn [r] (real->model (real-id-key r))))
        records))

(defn project-orgs
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
  [model-state]
  (->> (:orgs model-state)
       (map (fn [[org-id {:keys [accounts products parties]}]]
              [org-id
               {:accounts (set accounts)
                :products (set products)
                :parties (set parties)}]))
       (into {})))
