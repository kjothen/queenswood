(ns com.repldriven.mono.bank-api.party.queries
  (:require
    [com.repldriven.mono.bank-api.cursor :as cursor]
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-party.interface :as parties]
    [com.repldriven.mono.error.interface :as error]))

(def ^:private default-page-size 20)

(defn- build-links
  [base before-cursor after-cursor]
  (cond-> {}
          after-cursor
          (assoc :next
                 (str base
                      "?page[after]="
                      (cursor/encode after-cursor)))
          before-cursor
          (assoc :prev
                 (str base
                      "?page[before]="
                      (cursor/encode before-cursor)))))

(defn list-parties
  [request]
  (let [org-id (get-in request [:auth :organization-id])
        query (get-in request [:parameters :query])
        {:keys [page]} query
        after-id (cursor/decode (:after page))
        before-id (cursor/decode (:before page))
        size (or (:size page) default-page-size)
        result (parties/get-parties request
                                    org-id
                                    {:after after-id
                                     :before before-id
                                     :limit size})]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      (let [{:keys [parties before after]} result
            links (when (seq parties)
                    (build-links "/v1/parties"
                                 (when after-id before)
                                 after))]
        {:status 200
         :body (cond-> {:parties parties}
                       (seq links)
                       (assoc :links links))}))))

(defn get-party
  [request]
  (let [org-id (get-in request [:auth :organization-id])
        {:keys [party-id]} (get-in request [:parameters :path])
        result (parties/get-party request org-id party-id)]
    (cond (error/anomaly? result)
          (errors/anomaly->response result)
          :else
          {:status 200 :body result})))
