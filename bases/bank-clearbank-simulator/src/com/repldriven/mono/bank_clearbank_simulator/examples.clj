(ns com.repldriven.mono.bank-clearbank-simulator.examples)

(def BadRequest
  {:summary "Bad Request"
   :value {:title "BAD_REQUEST"
           :type "validation/failed"
           :status 400
           :detail "Invalid request body"}})

(def InternalServerError
  {:summary "Internal Server Error"
   :value {:title "FAILED"
           :type "server/error"
           :status 500
           :detail "Internal server error"}})
