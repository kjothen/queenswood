(ns com.repldriven.mono.bank-api-key.domain
  (:require
    [com.repldriven.mono.bank-policy.interface :as policy]
    [com.repldriven.mono.encryption.interface :as encryption]
    [com.repldriven.mono.error.interface :refer [let-nom>]]
    [com.repldriven.mono.utility.interface :as utility]))

(def ^:private status->api-key-prefix
  {:organization-status-live "sk_live." :organization-status-test "sk_test."})

(def ^:private default-api-key-prefix "sk_test.")
(def ^:private api-key-display-prefix-len 12)

(defn new-api-key
  "Creates a new ApiKey record map and its key secret.
  Returns {:api-key <map> :key-secret <string>} or an
  unauthorized anomaly when `policies` deny issuing keys.
  The key-secret is only available at creation time. The
  key prefix tracks the organization's `status` — `sk_live.`
  for live orgs, `sk_test.` otherwise."
  [org-id status key-name aggregates policies]
  (let-nom>
    [_ (policy/check-capability policies
                                :api-key
                                {:action :api-key-action-issue})
     _ (policy/check-limit
        policies
        :api-key
        {:aggregate :count
         :window :instant
         :value (inc (get-in aggregates [:api-key #{:organization-id}]))})]

    (let [api-key-prefix (get status->api-key-prefix
                              status
                              default-api-key-prefix)
          key-secret (encryption/generate-token api-key-prefix)
          key-hash (encryption/hash-token key-secret)
          key-prefix (subs key-secret
                           0
                           (min api-key-display-prefix-len
                                (count key-secret)))
          now (System/currentTimeMillis)]
      {:api-key {:api-key-id (utility/generate-id "sk")
                 :organization-id org-id
                 :key-hash key-hash
                 :key-prefix key-prefix
                 :name key-name
                 :created-at now}
       :key-secret key-secret})))
