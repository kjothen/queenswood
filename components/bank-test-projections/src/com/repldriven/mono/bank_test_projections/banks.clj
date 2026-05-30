(ns com.repldriven.mono.bank-test-projections.banks
  (:require
    [com.repldriven.mono.bank-cash-account-product.interface :as products]
    [com.repldriven.mono.bank-cash-account.interface :as cash-accounts]
    [com.repldriven.mono.bank-party.interface :as party]))

(defn- model-ids
  [real->model real-id-key records]
  (into #{}
        (keep (fn [r] (real->model (real-id-key r))))
        records))

(defn project-banks
  [bank ctx]
  (let [{:keys [banks id-mapping products parties]} ctx
        acct-real->model (:real->model id-mapping)
        prod-real->model (into {}
                               (map (fn [[m {:keys [real-id]}]] [real-id m]))
                               products)
        party-real->model (into {}
                                (map (fn [[m {:keys [real-id]}]] [real-id m]))
                                parties)]
    (->> banks
         (map (fn [[model-bank {:keys [real-id]}]]
                (let [accts (:accounts (cash-accounts/get-accounts
                                        bank
                                        real-id))
                      prods (:items (products/get-products bank real-id))
                      ptys (:parties (party/get-parties bank real-id))]
                  [model-bank
                   {:accounts (model-ids acct-real->model :account-id accts)
                    :products (model-ids prod-real->model :product-id prods)
                    :parties (model-ids party-real->model :party-id ptys)}])))
         (into {}))))

(defn project-model-banks
  [model-state]
  (->> (:banks model-state)
       (map (fn [[org-id {:keys [accounts products parties]}]]
              [org-id
               {:accounts (set accounts)
                :products (set products)
                :parties (set parties)}]))
       (into {})))
