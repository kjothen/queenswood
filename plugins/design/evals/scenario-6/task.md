# Let an admin toggle a scheduler job on and off

## Background

`inputs/src/com/repldriven/mono/bank_scheduler/core.clj` needs a
`set-job-enabled!` function for an internal admin UI. It updates a
single job record's `:enabled?` flag via
`inputs/src/com/repldriven/mono/bank_scheduler/store.clj`'s
`save-job`. No other brick needs to react to this change, there's no
risk from a duplicate call, and the admin UI wants to see its own
write reflected immediately.

## Task

Implement `set-job-enabled!`, taking `txn`, `record-db`, `job-id`, and
`enabled?`, returning the updated job record.

Edit `inputs/src/com/repldriven/mono/bank_scheduler/core.clj` in
place.
