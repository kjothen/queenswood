# Add a deduplicated entry lookup

## Background

`inputs/src/com/repldriven/mono/bank_ledger/core.clj` is a Queenswood
`bank-ledger` component's core namespace. The upstream payment-scheme
adapter occasionally redelivers the same webhook twice within a short
window — a known quirk of its retry policy, not a bug in this ledger.
A redelivered webhook must not produce two ledger entries.

## Task

Add an `entries-for` function to
`inputs/src/com/repldriven/mono/bank_ledger/core.clj` that returns all
ledger entries matching a given `tx-type`, deduplicated by `:id` so a
redelivered webhook doesn't yield a duplicate entry. The redelivery
cause isn't obvious from the code alone — capture it appropriately for
the next person reading this file.

Edit `inputs/src/com/repldriven/mono/bank_ledger/core.clj` in place.
