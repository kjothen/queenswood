(ns com.repldriven.mono.bank-api.payee-check.queries
  (:require
    [com.repldriven.mono.bank-api.cursor :as cursor]
    [com.repldriven.mono.bank-api.errors :refer [error-response]]
    [com.repldriven.mono.bank-payee-check.interface :as payee-checks]
    [com.repldriven.mono.error.interface :as error]))

(def ^:private default-page-size 20)
(def ^:private max-page-size 100)

(defn- parse-page-size
  [s]
  (let [n (when s
            (try (Integer/parseInt s)
                 (catch NumberFormatException _ nil)))]
    (cond (nil? n)
          default-page-size
          (< n 1)
          1
          (> n max-page-size)
          max-page-size
          :else
          n)))

(defn- build-links
  [before-cursor after-cursor]
  (cond-> {}
          after-cursor
          (assoc :next
                 (str "/v1/payee-checks?page[after]="
                      (cursor/encode after-cursor)))
          before-cursor
          (assoc :prev
                 (str "/v1/payee-checks?page[before]="
                      (cursor/encode before-cursor)))))

(defn get-check
  [request]
  (let [{:keys [record-db record-store auth parameters]} request
        {:keys [path]} parameters
        {:keys [check-id]} path
        {:keys [organization-id]} auth
        config {:record-db record-db :record-store record-store}
        result (payee-checks/get-check config
                                       organization-id
                                       check-id)]
    (cond (error/anomaly? result)
          (if (= :payee-check/not-found (error/kind result))
            {:status 404 :body (error-response 404 result)}
            {:status 500 :body (error-response 500 result)})
          :else
          {:status 200 :body result})))

(defn list-checks
  [request]
  (let [{:keys [record-db record-store auth parameters]} request
        {:keys [query]} parameters
        {:keys [organization-id]} auth
        after-id (cursor/decode
                  (get query (keyword "page[after]")))
        before-id (cursor/decode
                   (get query (keyword "page[before]")))
        size (parse-page-size
              (get query (keyword "page[size]")))
        config {:record-db record-db :record-store record-store}
        result (payee-checks/list-checks config
                                         organization-id
                                         {:after after-id
                                          :before before-id
                                          :limit size})]
    (if (error/anomaly? result)
      {:status 500 :body (error-response 500 result)}
      (let [{:keys [items before after]} result
            links (when (seq items)
                    (build-links (when after-id before)
                                 after))]
        {:status 200
         :body (cond-> {:items items}
                       (seq links)
                       (assoc :links links))}))))
