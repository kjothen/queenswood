(ns com.repldriven.mono.bank-payment.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private internal-payments-store-name "internal-payments")
(def ^:private outbound-payments-store-name "outbound-payments")
(def ^:private inbound-payments-store-name "inbound-payments")

(def transact fdb/transact)
(def uniqueness-violation? fdb/uniqueness-violation?)

(defn save-internal-payment
  [txn payment]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record
      (fdb/open txn internal-payments-store-name)
      (schema/InternalPayment->java payment)))
   :payment/save-internal-payment
   "Failed to save internal payment"))

(defn save-outbound-payment
  [txn payment]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record
      (fdb/open txn outbound-payments-store-name)
      (schema/OutboundPayment->java payment)))
   :payment/save-outbound-payment
   "Failed to save outbound payment"))

(defn save-inbound-payment
  [txn payment]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record
      (fdb/open txn inbound-payments-store-name)
      (schema/InboundPayment->java payment)))
   :payment/save-inbound-payment
   "Failed to save inbound payment"))

(defn get-internal-payment
  [txn payment-id]
  (fdb/transact
   txn
   (fn [txn]
     (some-> (fdb/load-record (fdb/open txn internal-payments-store-name)
                              payment-id)
             schema/pb->InternalPayment))
   :payment/get-internal-payment
   "Failed to get internal payment"))

(defn get-outbound-payment
  [txn payment-id]
  (fdb/transact
   txn
   (fn [txn]
     (some-> (fdb/load-record (fdb/open txn outbound-payments-store-name)
                              payment-id)
             schema/pb->OutboundPayment))
   :payment/get-outbound-payment
   "Failed to get outbound payment"))

(defn get-inbound-payment
  [txn scheme-transaction-id]
  (fdb/transact
   txn
   (fn [txn]
     (some-> (fdb/query-record
              (fdb/open txn inbound-payments-store-name)
              "InboundPayment"
              "scheme_transaction_id"
              scheme-transaction-id
              {:index "InboundPayment_by_scheme_transaction_id"})
             schema/pb->InboundPayment))
   :payment/get-inbound-payment
   "Failed to get inbound payment"))
