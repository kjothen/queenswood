# Add a transfer that debits an account and writes a ledger entry

## Background

`inputs/src/com/repldriven/mono/bank_ledger/core.clj` needs a
`transfer!` function. A transfer touches two separate FDB record
stores: `accounts` (via `save-account`) and `ledger` (via
`save-ledger-entry`), both already available on `store`.
`inputs/src/com/repldriven/mono/fdb/interface.clj` exposes a
`transact` macro that runs a body inside a single FDB transaction,
taking the store's `record-db` and binding the live transaction.

## Task

Implement `transfer!` so debiting the account and writing the ledger
entry happen atomically — both commit or neither does. Use `fdb`'s
`transact` macro rather than two independent calls.

Edit `inputs/src/com/repldriven/mono/bank_ledger/core.clj` in place.
