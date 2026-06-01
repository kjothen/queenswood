(ns com.repldriven.mono.bank-cash-account-product.domain
  (:require
    [com.repldriven.mono.bank-cash-account-product.resources :as resources]

    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.error.interface :as error :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]))

(defn- variant
  "Return the first key of a protojure oneof map (the active variant
  tag), nil if the map is empty or absent."
  [m]
  (when (map? m) (first (keys m))))

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

(defn- kind-of
  "Return :sub-ledger or :general-ledger from a version (or input data
  map), reading the active variant under `:kind`."
  [version-or-data]
  (variant (:kind version-or-data)))

(defn- sub-ledger-product-type
  "Read :product-type from a sub-ledger product's :kind variant. Nil
  for GL products."
  [version-or-data]
  (get-in version-or-data [:kind :sub-ledger :product-type]))

(defn- product-type-for-kind
  "Top-level product-type denormalised from a built :kind map: the
  sub-ledger variant's product-type, or :product-type-general-ledger
  for a GL product."
  [kind]
  (or (get-in kind [:sub-ledger :product-type])
      (when (:general-ledger kind) :product-type-general-ledger)))

;; ---------------------------------------------------------------------------
;; Capability + limit checks
;;
;; The :cash-account-product capability action takes `:product-type` as a
;; filter dimension. For sub-ledger products we pass the variant's
;; product-type; for GL products we omit the filter so a single CoA-author
;; capability applies across all GL accounts in a bank's chart.

(defn- check-capability
  [action version-or-data policies]
  (let [request (cond-> {:action action}

                        (= :sub-ledger (kind-of version-or-data))
                        (assoc :product-type
                               (sub-ledger-product-type version-or-data)))]
    (policy/check-capability policies :cash-account-product request)))

(defn- check-limit
  [aggregate window dimensions aggregates policies]
  (let [value (inc (get-in aggregates [:cash-account-product dimensions]))]
    (policy/check-limit policies
                        :cash-account-product
                        {:aggregate aggregate
                         :window window
                         :value value})))

;; ---------------------------------------------------------------------------
;; Sub-ledger product construction

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

(defn- build-sub-ledger-kind
  "Merge a sub-ledger product's caller-supplied :kind.sub-ledger map
  with the per-product-type template defaults. Returns
  {:product-type :balance-sheet-side :balance-products
   :allowed-payment-address-schemes :interest-rate-bps
   :iso-cash-account-type} ready to drop into :kind.sub-ledger on the
  version record. Returns an anomaly on unknown product-type or
  currency-not-allowed."
  [data currency]
  (let [{:keys [product-type interest-rate-bps iso-cash-account-type]}
        (get-in data [:kind :sub-ledger])]
    (let-nom>
      [template (resolve-template product-type)
       _ (ensure-currency-allowed template currency)]
      (cond-> {:product-type product-type
               :balance-sheet-side (:balance-sheet-side template)
               :balance-products (:balance-products template)
               :allowed-payment-address-schemes
               (:allowed-payment-address-schemes template)
               :interest-rate-bps (or interest-rate-bps 0)}

              iso-cash-account-type
              (assoc :iso-cash-account-type iso-cash-account-type)))))

;; ---------------------------------------------------------------------------
;; GL product construction
;;
;; GL products carry their fields explicitly — no template lookup. We just
;; pass through what the caller supplied (`bank-chart-of-accounts/seed!`),
;; with a sanity check that the required GL fields are present.

(defn- ensure-gl-fields
  [data]
  (let [{:keys [gl-code gl-account-type gl-account-class required]}
        (get-in data [:kind :general-ledger])
        missing (cond-> []
                        (nil? gl-code)
                        (conj :gl-code)
                        (nil? gl-account-type)
                        (conj :gl-account-type)
                        (nil? gl-account-class)
                        (conj :gl-account-class)
                        (nil? required)
                        (conj :required))]
    (when (seq missing)
      (error/reject :cash-account-product/incomplete-gl-product
                    {:message "GL product is missing required fields"
                     :missing missing}))))

(defn- build-gl-kind
  [data]
  (let-nom>
    [_ (ensure-gl-fields data)]
    ;; Pass through the caller-supplied GL fields verbatim.
    (get-in data [:kind :general-ledger])))

;; ---------------------------------------------------------------------------
;; Build :kind variant — dispatch on the active variant tag

(defn- build-kind
  [data currency]
  (case (kind-of data)
    :sub-ledger
    (let-nom>
      [fields (build-sub-ledger-kind data currency)]
      {:sub-ledger fields})

    :general-ledger
    (let-nom>
      [fields (build-gl-kind data)]
      {:general-ledger fields})

    (error/reject :cash-account-product/unknown-kind
                  {:message
                   "Product must declare :kind :sub-ledger or :general-ledger"
                   :kind (:kind data)})))

;; ---------------------------------------------------------------------------
;; Public domain operations

(defn new-version
  [bank-id product-id versions data policies]
  (let [{:keys [name currency valid-from]} data
        now (utility/now)]
    (let-nom>
      [kind (build-kind data currency)
       _ (check-capability :cash-account-product-action-draft
                           {:kind kind}
                           policies)
       _ (when (some draft? versions)
           (error/reject :cash-account-product/draft-already-exists
                         {:message "A draft already exists"
                          :bank-id bank-id
                          :product-id product-id}))]

      (-> {:bank-id bank-id
           :product-id product-id
           :version-id (utility/generate-id "prv")
           :version-number (inc (count versions))
           :status :cash-account-product-status-draft
           :name name
           :allowed-currencies [currency]
           :kind kind
           :created-at now
           :updated-at now}
          ;; Denormalised caches mirroring the kind variant so the FDB
          ;; indexes can be flat concats: product_type for every product
          ;; (sub-ledger type, or general-ledger for GL); gl_code only
          ;; for GL products (sub-ledger products leave it absent).
          (utility/assoc-some :product-type (product-type-for-kind kind))
          (utility/assoc-some :gl-code
                              (get-in kind
                                      [:general-ledger
                                       :gl-code]))
          (utility/assoc-some :valid-from valid-from)))))

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
        {:keys [name currency valid-from]} data]
    (let-nom>
      [_ (ensure-draft existing)
       ;; Updates can't change the kind. Preserve the existing variant
       ;; tag and merge in the caller's fresh kind-specific fields.
       _ (when (not= (kind-of existing) (kind-of data))
           (error/reject :cash-account-product/kind-immutable
                         {:message "Cannot change product kind on update"
                          :existing-kind (kind-of existing)
                          :requested-kind (kind-of data)}))
       kind (build-kind data currency)
       _ (check-capability :cash-account-product-action-draft
                           {:kind kind}
                           policies)]
      (-> {:bank-id bank-id
           :product-id product-id
           :version-id version-id
           :version-number version-number
           :status status
           :name name
           :allowed-currencies [currency]
           :kind kind
           :created-at created-at
           :updated-at (utility/now)}
          (utility/assoc-some :product-type (product-type-for-kind kind))
          (utility/assoc-some :gl-code
                              (get-in kind
                                      [:general-ledger
                                       :gl-code]))
          (utility/assoc-some :valid-from valid-from)))))

(defn publish
  [existing policies]
  (let-nom>
    [_ (ensure-draft existing)
     _ (check-capability :cash-account-product-action-publish
                         existing
                         policies)]
    (assoc existing
           :status :cash-account-product-status-published
           :updated-at (utility/now))))

(defn discard
  [existing policies]
  (let-nom>
    [_ (ensure-draft existing)
     _ (check-capability :cash-account-product-action-draft
                         existing
                         policies)]
    (let [now (utility/now)]
      (assoc existing
             :status :cash-account-product-status-discarded
             :discarded-at now
             :updated-at now))))
