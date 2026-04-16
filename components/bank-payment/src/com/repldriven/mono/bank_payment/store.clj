(ns com.repldriven.mono.bank-payment.store
  (:require
    [com.repldriven.mono.bank-schema.interface :as schema]

    [com.repldriven.mono.fdb.interface :as fdb]))

(def ^:private internal-payments-store-name "internal-payments")
(def ^:private outbound-payments-store-name "outbound-payments")
(def ^:private inbound-payments-store-name "inbound-payments")

(def transact fdb/transact)

(defn save-internal-payment
  "Saves an internal payment. Returns nil or anomaly."
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
  "Saves an outbound payment. Returns nil or anomaly."
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
  "Saves an inbound payment. Returns nil or anomaly."
  [txn payment]
  (fdb/transact
   txn
   (fn [txn]
     (fdb/save-record
      (fdb/open txn inbound-payments-store-name)
      (schema/InboundPayment->java payment)))
   :payment/save-inbound-payment
   "Failed to save inbound payment"))

(defn get-inbound-payment
  "Returns the inbound payment matching the given
  scheme-transaction-id, or nil."
  [txn scheme-transaction-id]
  (fdb/transact
   txn
   (fn [txn]
     (some-> (fdb/query-record
              (fdb/open txn inbound-payments-store-name)
              "InboundPayment"
              "scheme_transaction_id"
              scheme-transaction-id)
             schema/pb->InboundPayment))
   :payment/get-inbound-payment
   "Failed to get inbound payment"))

(defn get-outbound-payment
  "Returns the outbound payment matching the given
  end-to-end-id, or nil."
  [txn end-to-end-id]
  (fdb/transact
   txn
   (fn [txn]
     (some-> (fdb/query-record
              (fdb/open txn outbound-payments-store-name)
              "OutboundPayment"
              "end_to_end_id"
              end-to-end-id)
             schema/pb->OutboundPayment))
   :payment/get-outbound-payment
   "Failed to get outbound payment"))
