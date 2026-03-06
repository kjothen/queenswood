(ns com.repldriven.mono.bank-api.examples
  (:require
    [com.repldriven.mono.bank-api.schema :refer [examples-registry]]))

(def BadRequest
  {:value {:title "REJECTED"
           :type "api/bad-request"
           :status 400
           :detail "Bad Request"}})

(def Unauthorized
  {:value {:title "UNAUTHORIZED"
           :type "auth/unauthenticated"
           :status 401
           :detail "Missing or invalid API key"}})

(def Forbidden
  {:value {:title "UNAUTHORIZED"
           :type "auth/unauthorized"
           :status 403
           :detail
           "API key does not have sufficient privileges for this operation"}})

(def InternalServerError
  {:value {:title "FAILED"
           :type "mono/error"
           :status 500
           :detail "Internal server error"}})

(def registry
  (examples-registry [#'BadRequest #'Unauthorized #'Forbidden
                      #'InternalServerError]))
