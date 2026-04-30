(ns com.repldriven.mono.bank-cash-account-product.domain
  (:require
    [com.repldriven.mono.bank-cash-account-product.validation :as validation]

    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]))

(defn- draft?
  [version]
  (= :cash-account-product-status-draft (:status version)))

(defn- ensure-draft
  [version]
  (when-not (draft? version)
    (let [{:keys [organization-id product-id version-id status]} version]
      (error/reject :cash-account-product/version-immutable
                    {:message "Version is not a draft and cannot be modified"
                     :organization-id organization-id
                     :product-id product-id
                     :version-id version-id
                     :status status}))))

(defn- check-capability
  [action product-type policies]
  (policy/check-capability policies
                           :cash-account-product
                           {:action action
                            :product-type product-type}))

(defn- check-limit
  [aggregate window dimensions aggregates policies]
  (let [value (inc (get-in aggregates [:cash-account-product dimensions]))]
    (policy/check-limit policies
                        :cash-account-product
                        {:aggregate aggregate
                         :window window
                         :value value})))

(defn new-version
  "Requires the `:cash-account-product-action-draft` capability.
  `versions` is the existing version history (empty for a new
  product)."
  [organization-id product-id versions data policies]
  (let [{:keys [name product-type balance-sheet-side
                allowed-currencies balance-products
                allowed-payment-address-schemes
                interest-rate-bps valid-from]}
        data
        now (System/currentTimeMillis)]
    (let-nom>
      [_ (validation/unique-fields? data)
       _ (check-capability :cash-account-product-action-draft
                           product-type
                           policies)
       _ (when (some draft? versions)
           (error/reject :cash-account-product/draft-already-exists
                         {:message "A draft already exists"
                          :organization-id organization-id
                          :product-id product-id}))]

      (cond-> {:organization-id organization-id
               :product-id product-id
               :version-id (utility/generate-id "prv")
               :version-number (inc (count versions))
               :status :cash-account-product-status-draft
               :name name
               :product-type product-type
               :balance-sheet-side balance-sheet-side
               :balance-products balance-products
               :interest-rate-bps (or interest-rate-bps 0)
               :created-at now
               :updated-at now}

              (seq allowed-currencies)
              (assoc :allowed-currencies allowed-currencies)

              valid-from
              (assoc :valid-from valid-from)

              (seq allowed-payment-address-schemes)
              (assoc :allowed-payment-address-schemes
                     allowed-payment-address-schemes)))))

(defn new-product
  "Requires the `:cash-account-product-action-draft` capability
  and the `:cash-account-product` count limit. Generates a
  fresh product-id and produces v1."
  [organization-id data aggregates policies]
  (let-nom>
    [_ (check-limit :count :instant #{:organization-id} aggregates policies)]
    (new-version organization-id
                 (utility/generate-id "prd")
                 []
                 data
                 policies)))

(defn update-version
  "Replaces `existing`'s mutable fields with `data`. Requires
  the `:cash-account-product-action-draft` capability and
  `existing` to be in draft state."
  [existing data policies]
  (let [{:keys [organization-id product-id version-id
                version-number status created-at]}
        existing
        {:keys [name product-type balance-sheet-side
                allowed-currencies balance-products
                allowed-payment-address-schemes
                interest-rate-bps valid-from]}
        data]
    (let-nom>
      [_ (validation/unique-fields? data)
       _ (ensure-draft existing)
       _ (check-capability :cash-account-product-action-draft
                           product-type
                           policies)]
      (cond-> {:organization-id organization-id
               :product-id product-id
               :version-id version-id
               :version-number version-number
               :status status
               :name name
               :product-type product-type
               :balance-sheet-side balance-sheet-side
               :balance-products balance-products
               :interest-rate-bps (or interest-rate-bps 0)
               :created-at created-at
               :updated-at (utility/now)}

              (seq allowed-currencies)
              (assoc :allowed-currencies allowed-currencies)

              valid-from
              (assoc :valid-from valid-from)

              (seq allowed-payment-address-schemes)
              (assoc :allowed-payment-address-schemes
                     allowed-payment-address-schemes)))))

(defn publish
  "Requires the `:cash-account-product-action-publish` capability
  and `existing` to be in draft state."
  [existing policies]
  (let [{:keys [product-type]} existing]
    (let-nom>
      [_ (ensure-draft existing)
       _ (check-capability :cash-account-product-action-publish
                           product-type
                           policies)]
      (assoc existing
             :status :cash-account-product-status-published
             :updated-at (utility/now)))))

(defn discard
  "Requires the `:cash-account-product-action-draft` capability
  and `existing` to be in draft state."
  [existing policies]
  (let [{:keys [product-type]} existing]
    (let-nom>
      [_ (ensure-draft existing)
       _ (check-capability :cash-account-product-action-draft
                           product-type
                           policies)]
      (let [now (utility/now)]
        (assoc existing
               :status :cash-account-product-status-discarded
               :discarded-at now
               :updated-at now)))))
