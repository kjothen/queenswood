(ns com.repldriven.mono.bank-api.shared.components
  "Cross-cutting malli schemas shared across API surfaces — timestamps,
  date / country / currency primitives, payment address fragments, and
  the idempotency-key header. Registered globally in `api.clj` so any
  `[:ref \"X\"]` resolves the same definition everywhere."
  (:require
    [com.repldriven.mono.bank-api.schema :refer [components-registry]])
  (:import
    (java.time Instant LocalDate ZoneOffset)
    (java.time.format DateTimeParseException)))

(defn- iso-date->yyyymmdd
  [s]
  (when-let [[_ y mo d]
             (re-matches
              #"^([0-9]{4})-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$"
              s)]
    (+ (* (Integer/parseInt y) 10000)
       (* (Integer/parseInt mo) 100)
       (Integer/parseInt d))))

(defn- parse-timestamp
  [s]
  (when (string? s)
    (try (.toEpochMilli (Instant/parse s))
         (catch DateTimeParseException _
           (try (.toEpochMilli
                 (.toInstant (.atStartOfDay (LocalDate/parse s)
                                            ZoneOffset/UTC)))
                (catch DateTimeParseException _ s))))))

(defn- yyyymmdd->iso-date
  [n]
  (format "%04d-%02d-%02d"
          (quot n 10000)
          (mod (quot n 100) 100)
          (mod n 100)))

(def AccountNumber
  [:re
   {:title "AccountNumber" :json-schema/example "12345678"}
   #"^[0-9]{8}$"])

(def Amount
  "Monetary amount paired with its currency — mirrors the `Amount`
  proto in `schemas/amounts/amount.proto` (int64 minor units + ISO
  4217 currency)."
  [:map
   [:value [:ref "MinorUnits"]]
   [:currency [:ref "Currency"]]])

(def Bban
  [:re
   {:title "Bban" :json-schema/example "04000412345678"}
   #"^[0-9]{14}$"])

(def CountryCode
  "ISO 3166-1 alpha-2 country code: two uppercase ASCII letters.
  Matches the `nationality` field contract declared in
  `person-identification.proto`."
  [:re
   {:title "CountryCode" :json-schema/example "GB"}
   #"^[A-Z]{2}$"])

(def Currency
  "Closed enum of currencies the system natively supports. Stricter
  than `CurrencyCode` — request bodies use this to reject unsupported
  ISO codes at coercion time."
  [:enum {:json-schema/example "EUR"} "EUR" "GBP" "USD"])

(def CurrencyCode
  "ISO 4217 currency code: exactly three uppercase ASCII letters.

  Looser than the `Currency` enum (EUR/GBP/USD) — use for response
  fields that may carry historical or out-of-catalog currencies we
  must not reject at response-coercion time."
  [:re
   {:title "CurrencyCode" :json-schema/example "GBP"}
   #"^[A-Z]{3}$"])

(def Date
  "ISO 8601 calendar date (YYYY-MM-DD). Kept as a string end-to-end —
  distinct from Timestamp, which carries epoch-millis and encodes to
  a date-time string only at the API boundary.

  Pattern enforces month 01-12 and day 01-31 so boundary values like
  `0000-00-00` are rejected at coercion. Month-specific day counts
  (Feb 30, Apr 31) are not validated by the regex; validators that
  honour `format: date` pick up the stricter semantic check."
  [:re
   {:title "Date" :json-schema/format "date" :json-schema/example "2025-01-01"}
   #"^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$"])

(def DateOfBirth
  "ISO 8601 calendar date at the API boundary, stored as a packed
  `YYYYMMDD` integer internally (matches the `int32` field on the
  `PersonIdentification` proto). Decoded/encoded via `:api` so the
  storage format never leaks to clients."
  [:int
   {:decode/api (fn [v]
                  (cond (int? v)
                        v
                        (string? v)
                        (or (iso-date->yyyymmdd v) v)
                        :else
                        v))
    :encode/api (fn [n] (when (int? n) (yyyymmdd->iso-date n)))
    :json-schema {:type "string"
                  :format "date"
                  :pattern "^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$"
                  :example "1950-07-27"}}])

(def IdempotencyKey
  "Client-generated idempotency key. 16-255 chars of URL-safe ASCII
  (letters, digits, `_`, `-`) — UUID v4 or ULID both fit."
  [:and
   [:string
    {:min 16
     :max 255
     :description "Client-generated idempotency key. UUID v4 recommended."
     :json-schema/format "idempotency-key"}]
   [:re #"^[A-Za-z0-9_\-]+$"]])

(def IdempotencyKeyHeader
  "Header schema fragment declaring a required `Idempotency-Key`.
  Attach via `:parameters {:header IdempotencyKeyHeader ...}` on any
  route that also uses the `require-idempotency-key` interceptor so
  OpenAPI documents the requirement for clients and fuzzers."
  [:map
   [:idempotency-key
    {:json-schema {:example "01jsx6k7h0abfdv8qpm2ytn3we"}}
    [:ref "IdempotencyKey"]]])

(def MinorUnits
  [:int
   {:min 0
    :max 10000000000000
    :json-schema/format "int64"
    :description
    "Monetary quantity in the smallest denomination of the associated currency."}])

(def Name
  "Non-empty printable text up to 140 chars. Used wherever the
  API exposes a human-readable name (party display names, payment
  creditor names, organization / product / cash-account / api-key
  names) — same shape as the CoP spec's CreditorAccountName.

  The regex excludes ASCII C0/C1 control characters via explicit
  byte ranges (`\\x00-\\x1F\\x7F-\\x9F`) rather than `\\p{Cc}` so
  Python-based consumers (schemathesis / jsonschema) can compile
  it."
  [:re
   {:title "Name" :json-schema/example "Arthur Dent"}
   #"^(?!\s*$)[^\x00-\x1F\x7F-\x9F]{1,140}$"])

(def SignedAmount
  "Signed monetary amount paired with its currency — same shape as
  `Amount` but permits negative values (e.g. available balances that
  may dip below zero)."
  [:map
   [:value [:ref "SignedMinorUnits"]]
   [:currency [:ref "Currency"]]])

(def SignedBasisPoints
  [:int
   {:min -1000000
    :max 1000000
    :json-schema/format "int32"
    :description "Signed rate in basis points. Negative rates permitted."}])

(def SignedMinorUnits
  [:int
   {:min -10000000000000
    :max 10000000000000
    :json-schema/format "int64"
    :description
    "Signed monetary quantity in the smallest denomination of the associated currency."}])

(def SortCode
  [:re
   {:title "SortCode" :json-schema/example "040004"}
   #"^[0-9]{6}$"])

(def Timestamp
  [:int
   {:decode/api (fn [v]
                  (cond (int? v)
                        v
                        (string? v)
                        (or (parse-timestamp v) v)
                        :else
                        v))
    :encode/api (fn [ms] (when (pos? ms) (str (Instant/ofEpochMilli ms))))
    :json-schema {:type "string" :format "date-time"}}])

(def registry
  (components-registry [#'AccountNumber #'Amount #'Bban #'CountryCode #'Currency
                        #'CurrencyCode #'Date #'DateOfBirth #'IdempotencyKey
                        #'IdempotencyKeyHeader #'MinorUnits #'Name
                        #'SignedAmount #'SignedBasisPoints #'SignedMinorUnits
                        #'SortCode #'Timestamp]))
