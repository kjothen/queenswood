(ns com.repldriven.queenswood.api.examples
  (:require
    [com.repldriven.queenswood.api.schema :refer [examples-registry]]))

(def BadRequest
  {:value {:title "REJECTED"
           :type "mono/bad-request"
           :status 400
           :detail "Bad Request"}})

(def Unauthorized
  {:value {:title "UNAUTHORIZED"
           :type "mono/unauthenticated"
           :status 401
           :detail "Missing or invalid API key"}})

(def Forbidden
  {:value {:title "UNAUTHORIZED"
           :type "mono/unauthorized"
           :status 403
           :detail
           "API key does not have sufficient privileges for this operation"}})

(def BadResponse
  {:value {:title "FAILED"
           :type "mono/bad-response"
           :status 500
           :detail "Bad Response"}})

(def InternalServerError
  {:value {:title "FAILED"
           :type "mono/internal-server-error"
           :status 500
           :detail "Internal server error"}})

(def ServiceUnavailable
  {:value {:title "FAILED"
           :type ":fdb/contention"
           :status 503
           :detail "Failed to save user"}})

(def GatewayTimeout
  {:value {:title "FAILED"
           :type ":fdb/timeout"
           :status 504
           :detail "Failed to save user"}})

(def registry
  (examples-registry [#'BadRequest #'Unauthorized #'Forbidden #'BadResponse
                      #'InternalServerError #'ServiceUnavailable
                      #'GatewayTimeout]))
