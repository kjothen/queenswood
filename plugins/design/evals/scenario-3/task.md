# Activate a party once identity verification completes

## Background

`inputs/src/com/repldriven/mono/bank_idv/core.clj` saves an IDV check
result once it completes.
`inputs/src/com/repldriven/mono/bank_party/watcher.clj` is
`bank-party`'s changelog watcher — its handler for IDV-store changes
is currently a stub that does nothing.

## Task

When an IDV check completes successfully, the party it belongs to
should transition from `pending` to `active`. Implement this reaction.
Check the party's current status before acting, so a replayed or
duplicate change doesn't reactivate an already-active party.

Only edit `inputs/src/com/repldriven/mono/bank_party/watcher.clj`.
