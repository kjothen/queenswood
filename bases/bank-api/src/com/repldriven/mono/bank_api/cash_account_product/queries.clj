(ns com.repldriven.mono.bank-api.cash-account-product.queries
  (:require
    [com.repldriven.mono.bank-api.cursor :as cursor]
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-cash-account-product.interface :as
     cash-account-products]
    [com.repldriven.mono.error.interface :as error]))

(def ^:private default-page-size 20)
(def ^:private max-page-size 100)

(defn- clamp-size
  [n]
  (cond (nil? n)
        default-page-size
        (< n 1)
        1
        (> n max-page-size)
        max-page-size
        :else
        n))

(defn- build-links
  [base before-id after-id]
  (cond-> {}
          after-id
          (assoc :next
                 (str base "?page[after]=" (cursor/encode after-id)))
          before-id
          (assoc :prev
                 (str base "?page[before]=" (cursor/encode before-id)))))

(defn- paginate
  "Windows a seq of product aggregates — assumed to already be in
  descending product-id order, which is what `core/get-products`
  returns under the store's default `:order :desc` scan — using
  `page[after|before|size]` cursor semantics.

  In descending display order, `:after cursor` advances further
  into smaller product-ids; `:before cursor` retreats toward larger
  ones. `size` caps the page length."
  [items {:keys [after before size]}]
  (let [limit (clamp-size size)]
    (cond
     after
     (let [after-items (drop-while
                        (fn [{:keys [product-id]}]
                          (not (neg? (compare product-id after))))
                        items)
           page (vec (take limit after-items))]
       {:page page
        :before (when (seq page) (:product-id (first page)))
        :after (when (> (count after-items) limit)
                 (:product-id (last page)))})

     before
     (let [before-items (take-while
                         (fn [{:keys [product-id]}]
                           (pos? (compare product-id before)))
                         items)
           page (vec (take-last limit before-items))]
       {:page page
        :before (when (> (count before-items) limit)
                  (:product-id (first page)))
        :after (when (seq page) (:product-id (last page)))})

     :else
     (let [page (vec (take limit items))]
       {:page page
        :before nil
        :after (when (> (count items) limit)
                 (:product-id (last page)))}))))

(defn list-products
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        query (get-in request [:parameters :query])
        {:keys [page]} query
        after (cursor/decode (:after page))
        before (cursor/decode (:before page))
        size (:size page)
        result (cash-account-products/get-products
                {:record-db record-db :record-store record-store}
                org-id)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      (let [{:keys [items]} result
            windowed (paginate (or items [])
                               {:after after :before before :size size})
            {windowed-items :page
             next-cursor :after
             prev-cursor :before}
            windowed
            links (when (seq windowed-items)
                    (build-links "/v1/cash-account-products"
                                 (when after prev-cursor)
                                 next-cursor))]
        {:status 200
         :body (cond-> {:items windowed-items}
                       (seq links)
                       (assoc :links links))}))))

(defn get-product
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        {:keys [product-id]} (get-in request [:parameters :path])
        result (cash-account-products/get-product
                {:record-db record-db :record-store record-store}
                org-id
                product-id)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body result})))

(defn get-version
  [request]
  (let [{:keys [record-db record-store]} request
        org-id (get-in request [:auth :organization-id])
        {:keys [product-id version-id]} (get-in request [:parameters :path])
        result (cash-account-products/get-version
                {:record-db record-db :record-store record-store}
                org-id
                product-id
                version-id)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body result})))
