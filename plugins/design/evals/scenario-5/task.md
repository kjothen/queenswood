# Add the interest-accrued event schema

## Background

`inputs/src/com/repldriven/mono/bank_schema/avro.clj` holds the
message-bus event schemas, defined with Lancaster
(`l/def-record-schema`) — see the existing
`transaction-settled-schema` for the pattern. A new event,
`interest-accrued`, needs a schema alongside it, with fields
`account-id` (string) and `amount` (double).

## Task

Add `interest-accrued-schema` to `avro.clj`, following the same
pattern and field-naming convention as the existing schema.

Edit `inputs/src/com/repldriven/mono/bank_schema/avro.clj` in place.
