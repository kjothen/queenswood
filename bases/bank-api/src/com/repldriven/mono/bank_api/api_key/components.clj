(ns com.repldriven.mono.bank-api.api-key.components
  (:require
    [com.repldriven.mono.bank-api.api-key.examples :as examples]
    [com.repldriven.mono.bank-api.schema :as schema
     :refer [components-registry]]))

(def ApiKeyId (schema/id-schema "ApiKeyId" "sk" examples/ApiKeyId))

(def ApiKey
  [:map {:json-schema/example examples/ApiKey}
   [:api-key-id [:ref "ApiKeyId"]]
   [:name [:ref "Name"]]
   [:key-prefix string?]
   [:created-at {:optional true} [:maybe [:ref "Timestamp"]]]])

(def ApiKeyList
  [:map {:json-schema/example examples/ApiKeyList}
   [:api-keys [:vector [:ref "ApiKey"]]]])

(def CreateApiKeyResponse
  [:map {:json-schema/example examples/CreateApiKeyResponse}
   [:api-key-id [:ref "ApiKeyId"]]
   [:name [:ref "Name"]]
   [:key-prefix string?]
   [:key-secret string?]
   [:created-at [:ref "Timestamp"]]])

(def registry
  (components-registry [#'ApiKeyId #'ApiKey #'ApiKeyList
                        #'CreateApiKeyResponse]))
