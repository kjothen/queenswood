(ns com.repldriven.mono.server.handlers
  (:require [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]))

(def standard-swagger-ui-handler
  "Standard Swagger UI handler with standard configuration."
  (swagger-ui/create-swagger-ui-handler
   {:path "/"
    :config {:validatorUrl nil
             :operationsSorter "alpha"}}))

(def standard-swagger-handler
  "Standard Swagger spec handler."
  swagger/create-swagger-handler)
