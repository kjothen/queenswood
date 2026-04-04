(ns com.repldriven.mono.bank-clearbank.webhook.components
  (:require
    [com.repldriven.mono.bank-clearbank.webhook.examples
     :as examples]
    [com.repldriven.mono.utility.interface :refer [vname]]))

(defn- components-registry
  [vars]
  (reduce (fn [m v] (assoc m (vname v) @v)) {} vars))

(defn- examples-registry
  [vars]
  (reduce (fn [m v] (assoc m (vname v) @v)) {} vars))

(def AccountInfo
  [:map
   {:json-schema/example examples/AccountInfo}
   [:IBAN {:optional true} [:maybe string?]]
   [:BBAN {:optional true} [:maybe string?]]
   [:OwnerName {:optional true} [:maybe string?]]
   [:TransactionOwnerName {:optional true} [:maybe string?]]
   [:InstitutionName {:optional true} [:maybe string?]]
   [:Balance {:optional true} [:maybe number?]]
   [:BalanceLastUpdatedAt {:optional true} [:maybe string?]]])

(def CounterpartAccountInfo
  [:map
   {:json-schema/example examples/CounterpartAccountInfo}
   [:IBAN {:optional true} [:maybe string?]]
   [:BBAN {:optional true} [:maybe string?]]
   [:OwnerName {:optional true} [:maybe string?]]
   [:TransactionOwnerName {:optional true} [:maybe string?]]
   [:InstitutionName {:optional true} [:maybe string?]]])

(def TransactionSettledPayload
  [:map
   {:json-schema/example examples/TransactionSettledPayload}
   [:TransactionId string?]
   [:Status string?]
   [:Scheme [:enum "Transfer" "FasterPayments" "Bacs" "Chaps"]]
   [:EndToEndTransactionId string?]
   [:Amount number?]
   [:TimestampSettled {:optional true} [:maybe string?]]
   [:TimestampCreated {:optional true} [:maybe string?]]
   [:CurrencyCode string?]
   [:DebitCreditCode [:enum "Credit" "Debit"]]
   [:Reference {:optional true} [:maybe string?]]
   [:IsReturn boolean?]
   [:Account [:ref "AccountInfo"]]
   [:CounterpartAccount [:ref "CounterpartAccountInfo"]]
   [:ActualEndToEndTransactionId {:optional true}
    [:maybe string?]]
   [:TransactionSource {:optional true} [:maybe string?]]])

(def TransactionRejectedPayload
  [:map
   {:json-schema/example examples/TransactionRejectedPayload}
   [:TransactionId string?]
   [:Status string?]
   [:Scheme {:optional true}
    [:maybe [:enum "Transfer" "FasterPayments" "Chaps"]]]
   [:EndToEndTransactionId string?]
   [:Amount {:optional true} [:maybe number?]]
   [:TimestampModified {:optional true} [:maybe string?]]
   [:CurrencyCode {:optional true} [:maybe string?]]
   [:DebitCreditCode {:optional true}
    [:maybe [:enum "Credit" "Debit"]]]
   [:Reference {:optional true} [:maybe string?]]
   [:IsReturn {:optional true} [:maybe boolean?]]
   [:CancellationReason {:optional true} [:maybe string?]]
   [:CancellationCode {:optional true} [:maybe string?]]
   [:Account {:optional true}
    [:maybe [:ref "CounterpartAccountInfo"]]]
   [:CounterpartAccount {:optional true}
    [:maybe [:ref "CounterpartAccountInfo"]]]])

(def TransactionSettledWebhook
  [:map
   {:json-schema/example examples/TransactionSettledWebhook}
   [:Type [:= "TransactionSettled"]]
   [:Version int?]
   [:Payload [:ref "TransactionSettledPayload"]]
   [:Nonce int?]])

(def TransactionRejectedWebhook
  [:map
   {:json-schema/example examples/TransactionRejectedWebhook}
   [:Type [:= "TransactionRejected"]]
   [:Version int?]
   [:Payload [:ref "TransactionRejectedPayload"]]
   [:Nonce int?]])

(def webhook-components-registry
  (components-registry [#'AccountInfo #'CounterpartAccountInfo
                        #'TransactionSettledPayload #'TransactionRejectedPayload
                        #'TransactionSettledWebhook
                        #'TransactionRejectedWebhook]))

(def webhook-examples-registry
  (examples-registry
   [#'examples/TransactionSettledWebhook #'examples/TransactionRejectedWebhook
    #'examples/TransactionSettledPayload #'examples/TransactionRejectedPayload
    #'examples/AccountInfo #'examples/CounterpartAccountInfo]))
