(ns com.repldriven.mono.bank-api.organizations.examples
  (:require
    [com.repldriven.mono.bank-api.api-keys.examples :as api-key-examples]

    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def registry (examples-registry []))

(def Organization
  {:organization-id "org_01JMABC123" :name "Acme Corp" :status "ACTIVE"})

(def CreateOrganizationRequest (select-keys Organization [:name]))

(def CreateOrganizationResponse
  {:organization Organization :api-key api-key-examples/ApiKey})
