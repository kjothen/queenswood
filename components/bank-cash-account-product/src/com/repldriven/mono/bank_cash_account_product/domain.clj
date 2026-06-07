(ns com.repldriven.mono.bank-cash-account-product.domain
  (:require
    [com.repldriven.mono.bank-cash-account-product.resources :as resources]

    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]))

(defn- draft?
  [version]
  (= :cash-account-product-status-draft (:status version)))

(defn- ensure-draft
  [version]
  (when-not (draft? version)
    (let [{:keys [bank-id product-id version-id status]} version]
      (error/reject :cash-account-product/version-immutable
                    {:message "Version is not a draft and cannot be modified"
                     :bank-id bank-id
                     :product-id product-id
                     :version-id version-id
                     :status status}))))

;; ---------------------------------------------------------------------------
;; Instrument fields — resolve the per-product-type template

(defn- resolve-template
  [product-type]
  (or (get resources/product-defaults product-type)
      (error/reject :cash-account-product/unknown-product-type
                    {:message "No template defined for product-type"
                     :product-type product-type})))

(defn- ensure-currency-allowed
  [template currency]
  (when-not (contains? (set (:allowed-currencies template)) currency)
    (error/reject :cash-account-product/currency-not-allowed
                  {:message "Currency not allowed for this product-type"
                   :currency currency
                   :allowed-currencies (:allowed-currencies template)})))

(defn- product-fields
  "Merge the caller's `:product-type` / `:interest-rate-bps` /
  `:iso-cash-account-type` with the per-product-type template defaults
  (balance-sheet-side, balance buckets, payment-address schemes,
  allowed currencies). Returns the instrument fields or an anomaly on
  unknown product-type or currency-not-allowed."
  [data currency]
  (let [{:keys [product-type interest-rate-bps iso-cash-account-type]} data]
    (let-nom>
      [template (resolve-template product-type)
       _ (ensure-currency-allowed template currency)]
      (utility/assoc-some
       {:product-type product-type
        :balance-sheet-side (:balance-sheet-side template)
        :balance-products (:balance-products template)
        :allowed-payment-address-schemes
        (:allowed-payment-address-schemes template)
        :interest-rate-bps (or interest-rate-bps 0)}
       :iso-cash-account-type
       iso-cash-account-type))))

;; ---------------------------------------------------------------------------
;; Capability + limit checks

(defn- check-capability
  [action product-type policies]
  (policy/check-capability policies
                           :cash-account-product
                           {:action action :product-type product-type}))

(defn- check-limit
  [aggregate window dimensions aggregates policies]
  (let [value (inc (get-in aggregates [:cash-account-product dimensions]))]
    (policy/check-limit policies
                        :cash-account-product
                        {:aggregate aggregate
                         :window window
                         :value value})))

;; ---------------------------------------------------------------------------
;; Effective window
;;
;; effective-from / effective-to are epoch-day (int). effective-from is
;; required; effective-to, when present, must fall strictly after it.

(defn- ensure-effective-window
  [effective-from effective-to]
  (cond
   (nil? effective-from)
   (error/reject :cash-account-product/effective-from-required
                 {:message "A product needs an effective-from date"})

   (and effective-to (<= effective-to effective-from))
   (error/reject :cash-account-product/invalid-effective-window
                 {:message "effective-to must be after effective-from"
                  :effective-from effective-from
                  :effective-to effective-to})

   :else
   nil))

(defn active-version
  "The published version effective on epoch-day `as-of`: of the
  published versions whose `[effective-from, effective-to)` window
  contains `as-of`, the one with the greatest effective-from (then
  version-number). nil if none. A version with no effective-from is
  treated as effective from the beginning of time."
  [{:keys [versions]} as-of]
  (->> versions
       (filter (fn [v]
                 (= :cash-account-product-status-published (:status v))))
       (filter (fn [{:keys [effective-from effective-to]}]
                 (and (or (nil? effective-from) (<= effective-from as-of))
                      (or (nil? effective-to) (< as-of effective-to)))))
       (sort-by (fn [{:keys [effective-from version-number]}]
                  [(or effective-from Long/MIN_VALUE) version-number]))
       last))

;; ---------------------------------------------------------------------------
;; Public domain operations

(defn new-version
  [bank-id product-id versions data policies]
  (let [{:keys [name currency product-type effective-from effective-to]} data
        now (utility/now)]
    (let-nom>
      [fields (product-fields data currency)
       _ (ensure-effective-window effective-from effective-to)
       _ (check-capability :cash-account-product-action-draft
                           product-type
                           policies)
       _ (when (some draft? versions)
           (error/reject :cash-account-product/draft-already-exists
                         {:message "A draft already exists"
                          :bank-id bank-id
                          :product-id product-id}))]

      (utility/assoc-some
       (merge {:bank-id bank-id
               :product-id product-id
               :version-id (utility/generate-id "prv")
               :version-number (inc (count versions))
               :status :cash-account-product-status-draft
               :name name
               :allowed-currencies [currency]
               :created-at now
               :updated-at now}
              fields)
       :effective-from effective-from
       :effective-to effective-to))))

(defn new-product
  [bank-id data aggregates policies]
  (let-nom>
    [_ (check-limit :count
                    :time-window-instant
                    #{:bank-id}
                    aggregates
                    policies)]
    (new-version bank-id
                 (utility/generate-id "prd")
                 []
                 data
                 policies)))

(defn update-version
  [existing data policies]
  (let [{:keys [bank-id product-id version-id
                version-number status created-at]}
        existing
        {:keys [name currency product-type effective-from effective-to]} data]
    (let-nom>
      [_ (ensure-draft existing)
       fields (product-fields data currency)
       _ (ensure-effective-window effective-from effective-to)
       _ (check-capability :cash-account-product-action-draft
                           product-type
                           policies)]
      (utility/assoc-some
       (merge {:bank-id bank-id
               :product-id product-id
               :version-id version-id
               :version-number version-number
               :status status
               :name name
               :allowed-currencies [currency]
               :created-at created-at
               :updated-at (utility/now)}
              fields)
       :effective-from effective-from
       :effective-to effective-to))))

(defn publish
  [existing policies]
  (let-nom>
    [_ (ensure-draft existing)
     _ (check-capability :cash-account-product-action-publish
                         (:product-type existing)
                         policies)]
    (assoc existing
           :status :cash-account-product-status-published
           :updated-at (utility/now))))

(defn discard
  [existing policies]
  (let-nom>
    [_ (ensure-draft existing)
     _ (check-capability :cash-account-product-action-draft
                         (:product-type existing)
                         policies)]
    (let [now (utility/now)]
      (assoc existing
             :status :cash-account-product-status-discarded
             :discarded-at now
             :updated-at now))))
