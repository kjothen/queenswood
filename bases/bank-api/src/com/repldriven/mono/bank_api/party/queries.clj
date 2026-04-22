(ns com.repldriven.mono.bank-api.party.queries
  (:require
    [com.repldriven.mono.bank-api.cursor :as cursor]
    [com.repldriven.mono.bank-api.errors :as errors]
    [com.repldriven.mono.bank-party.interface :as parties]
    [com.repldriven.mono.error.interface :as error]))

(def ^:private default-page-size 20)
(def ^:private max-page-size 100)

(defn- parse-page-size
  [s]
  (let [n (when s
            (try (Integer/parseInt s) (catch NumberFormatException _ nil)))]
    (cond (nil? n)
          default-page-size
          (< n 1)
          1
          (> n max-page-size)
          max-page-size
          :else
          n)))

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
        after-id (cursor/decode
                  (get query (keyword "page[after]")))
        before-id (cursor/decode
                   (get query (keyword "page[before]")))
        size (parse-page-size
              (get query (keyword "page[size]")))
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
