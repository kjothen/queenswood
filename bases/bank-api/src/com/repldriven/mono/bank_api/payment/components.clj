(ns com.repldriven.mono.bank-api.payment.components
  (:require
    [com.repldriven.mono.bank-api.payment.coercion :as coercion]
    [com.repldriven.mono.bank-api.payment.examples :as examples]
    [com.repldriven.mono.bank-api.schema :as schema
     :refer [components-registry]]))

(def PaymentId (schema/id-schema "PaymentId" "pmt" examples/PaymentId))

(def SubmitInternalPaymentRequest
  [:map
   {:json-schema/example examples/SubmitInternalPaymentRequest}
   [:debtor-account-id [:ref "CashAccountId"]]
   [:creditor-account-id [:ref "CashAccountId"]]
   [:currency [:ref "Currency"]]
   [:amount [:ref "MinorUnits"]]
   [:reference {:optional true} [:maybe string?]]])

(def InternalPayment
  [:map {:json-schema/example examples/InternalPayment}
   [:payment-id [:ref "PaymentId"]]
   [:debtor-account-id [:ref "CashAccountId"]]
   [:creditor-account-id [:ref "CashAccountId"]]
   [:currency [:ref "Currency"]]
   [:amount [:ref "MinorUnits"]]
   [:transaction-id [:ref "TransactionId"]]
   [:reference {:optional true} [:maybe string?]]
   [:created-at [:ref "Timestamp"]]
   [:updated-at {:optional true} [:maybe [:ref "Timestamp"]]]])

(def OutboundPaymentStatus
  (coercion/outbound-payment-status-enum-schema {:json-schema/example
                                                 "pending"}))

(def SubmitOutboundPaymentRequest
  [:map
   {:json-schema/example examples/SubmitOutboundPaymentRequest}
   [:debtor-account-id [:ref "CashAccountId"]]
   [:creditor-bban [:ref "Bban"]]
   [:creditor-name [:ref "Name"]]
   [:currency [:ref "Currency"]]
   [:amount [:ref "MinorUnits"]]
   [:scheme string?]
   [:reference {:optional true} [:maybe string?]]])

(def OutboundPayment
  [:map {:json-schema/example examples/OutboundPayment}
   [:payment-id [:ref "PaymentId"]]
   [:scheme string?]
   [:debtor-account-id [:ref "CashAccountId"]]
   [:creditor-bban [:ref "Bban"]]
   [:creditor-name [:ref "Name"]]
   [:currency [:ref "Currency"]]
   [:amount [:ref "MinorUnits"]]
   [:payment-status [:ref "OutboundPaymentStatus"]]
   [:transaction-id [:ref "TransactionId"]]
   [:reference {:optional true} [:maybe string?]]
   [:cancellation-code {:optional true} [:maybe string?]]
   [:cancellation-reason {:optional true} [:maybe string?]]
   [:created-at {:optional true} [:maybe [:ref "Timestamp"]]]
   [:updated-at {:optional true} [:maybe [:ref "Timestamp"]]]])

(def registry
  (components-registry [#'PaymentId #'SubmitInternalPaymentRequest
                        #'InternalPayment #'OutboundPaymentStatus
                        #'SubmitOutboundPaymentRequest #'OutboundPayment]))
