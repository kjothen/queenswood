(ns com.repldriven.mono.server.openapi
  (:require
    [reitit.openapi :as openapi]
    [reitit.swagger-ui :as swagger-ui]))

(defn standard-handler [] (openapi/create-openapi-handler))

(defn standard-ui-handler
  []
  (swagger-ui/create-swagger-ui-handler {:path "/"
                                         :url "/openapi.json"
                                         :config {:validatorUrl nil
                                                  :operationsSorter "alpha"}}))
