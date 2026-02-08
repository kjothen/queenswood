(ns com.repldriven.mono.server.swagger
  (:require [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]))

(defn standard-ui-handler []
  (swagger-ui/create-swagger-ui-handler
   {:path "/"
    :config {:validatorUrl nil
             :operationsSorter "alpha"}}))

(defn standard-handler []
  (swagger/create-swagger-handler))
