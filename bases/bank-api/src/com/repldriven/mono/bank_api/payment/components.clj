(ns com.repldriven.mono.bank-api.payment.components
  (:require
    [com.repldriven.mono.bank-api.payment.coercion :as coercion]
    [com.repldriven.mono.bank-api.payment.examples :as examples]
    [com.repldriven.mono.bank-api.schema :refer [components-registry]]))

(def SubmitInternalPaymentRequest
  [:map
   {:json-schema/example examples/SubmitInternalPaymentRequest}
   [:idempotency-key string?]
   [:debtor-account-id string?]
   [:creditor-account-id string?]
   [:currency [:ref "Currency"]]
   [:amount int?]
   [:reference {:optional true} [:maybe string?]]])

(def InternalPayment
  [:map {:json-schema/example examples/InternalPayment}
   [:payment-id string?]
   [:idempotency-key string?]
   [:debtor-account-id string?]
   [:creditor-account-id string?]
   [:currency [:ref "Currency"]]
   [:amount int?]
   [:transaction-id string?]
   [:reference {:optional true} [:maybe string?]]
   [:created-at {:optional true} [:maybe [:ref "Timestamp"]]]
   [:updated-at {:optional true} [:maybe [:ref "Timestamp"]]]])

(def OutboundPaymentStatus
  (coercion/outbound-payment-status-enum-schema {:json-schema/example
                                                 "pending"}))

(def SubmitOutboundPaymentRequest
  [:map
   {:json-schema/example examples/SubmitOutboundPaymentRequest}
   [:idempotency-key string?]
   [:debtor-account-id string?]
   [:creditor-bban string?]
   [:creditor-name string?]
   [:currency [:ref "Currency"]]
   [:amount int?]
   [:scheme string?]
   [:reference {:optional true} [:maybe string?]]])

(def OutboundPayment
  [:map {:json-schema/example examples/OutboundPayment}
   [:payment-id string?]
   [:idempotency-key string?]
   [:scheme string?]
   [:debtor-account-id string?]
   [:creditor-bban string?]
   [:creditor-name string?]
   [:currency [:ref "Currency"]]
   [:amount int?]
   [:payment-status [:ref "OutboundPaymentStatus"]]
   [:transaction-id string?]
   [:reference {:optional true} [:maybe string?]]
   [:cancellation-code {:optional true} [:maybe string?]]
   [:cancellation-reason {:optional true} [:maybe string?]]
   [:created-at {:optional true} [:maybe [:ref "Timestamp"]]]
   [:updated-at {:optional true} [:maybe [:ref "Timestamp"]]]])

(def registry
  (components-registry [#'SubmitInternalPaymentRequest #'InternalPayment
                        #'OutboundPaymentStatus #'SubmitOutboundPaymentRequest
                        #'OutboundPayment]))
