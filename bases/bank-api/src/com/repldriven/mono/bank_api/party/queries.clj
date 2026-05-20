(ns com.repldriven.mono.bank-api.party.queries
  (:require
    [com.repldriven.mono.bank-api.cursor :as cursor]
    [com.repldriven.mono.bank-api.errors :as errors]

    [com.repldriven.mono.bank-party.interface :as parties]

    [com.repldriven.mono.error.interface :as error]
    [com.repldriven.mono.utility.interface :as utility]))

(defn list-parties
  [request]
  (let [{:keys [auth parameters]} request
        {:keys [organization-id]} auth
        {:keys [query]} parameters
        {:keys [page]} query
        {:keys [after before size]} page
        after-id (cursor/decode after)
        before-id (cursor/decode before)
        size (cursor/clamp-size size)
        result (parties/get-parties request
                                    organization-id
                                    {:after after-id
                                     :before before-id
                                     :limit size})]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      (let [{:keys [parties before after]} result
            links (when (seq parties)
                    (cursor/build-links "/v1/parties"
                                        size
                                        (when after-id before)
                                        after))]
        {:status 200
         :body (utility/assoc-seq {:parties parties} :links links)}))))

(defn get-party
  [request]
  (let [{:keys [auth parameters]} request
        {:keys [organization-id]} auth
        {:keys [path]} parameters
        {:keys [party-id]} path
        result (parties/get-party request organization-id party-id)]
    (if (error/anomaly? result)
      (errors/anomaly->response result)
      {:status 200 :body result})))
