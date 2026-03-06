(ns com.repldriven.mono.bank-api.api-keys.components
  (:require
    [com.repldriven.mono.bank-api.api-keys.examples :as examples]

    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def ApiKeyResponse
  [:map
   {:json-schema/example examples/ApiKey}
   [:id string?]
   [:key-prefix string?]
   [:raw-key string?]])

(def registry (components-registry [#'ApiKeyResponse]))
