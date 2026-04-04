(ns com.repldriven.mono.bank-clearbank.webhook.examples)

(def TransactionSettledWebhook
  {:Type "TransactionSettled"
   :Version 6
   :Payload {:TransactionId "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
             :Status "Settled"
             :Scheme "FasterPayments"
             :EndToEndTransactionId "e2e-001"
             :Amount 100.00
             :CurrencyCode "GBP"
             :DebitCreditCode "Debit"
             :TimestampSettled "2026-04-01T12:00:00Z"
             :TimestampCreated "2026-04-01T12:00:00Z"
             :Reference "Payment for invoice 123"
             :IsReturn false
             :Account {}
             :CounterpartAccount {}}
   :Nonce 123456789})

(def TransactionSettledPayload (:Payload TransactionSettledWebhook))

(def TransactionRejectedWebhook
  {:Type "TransactionRejected"
   :Version 2
   :Payload {:TransactionId "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
             :Status "Rejected"
             :Scheme "FasterPayments"
             :EndToEndTransactionId "e2e-001"
             :CancellationCode "AM09"
             :CancellationReason "Insufficient funds"
             :DebitCreditCode "Debit"
             :IsReturn false
             :Account {}
             :CounterpartAccount {}}
   :Nonce 123456789})

(def TransactionRejectedPayload (:Payload TransactionRejectedWebhook))

(def AccountInfo
  {:IBAN "GB29NWBK60161331926819"
   :BBAN "NWBK60161331926819"
   :OwnerName "Jane Doe"
   :TransactionOwnerName "Jane Doe"
   :InstitutionName "National Westminster Bank"})

(def CounterpartAccountInfo
  {:IBAN "GB82WEST12345698765432"
   :BBAN "WEST12345698765432"
   :OwnerName "John Smith"
   :TransactionOwnerName "John Smith"
   :InstitutionName "Westpac Banking Corporation"})
