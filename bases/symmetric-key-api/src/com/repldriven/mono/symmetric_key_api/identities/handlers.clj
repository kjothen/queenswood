(ns com.repldriven.mono.symmetric-key-api.identities.handlers)

(defn list-keys
  [_]
  {:status 200
   :body {:data []}})

(defn get-key
  [_]
  {:status 200
   :body {:data {}}})
