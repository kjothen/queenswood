# Publish a transaction-settled event

## Background

`inputs/src/com/repldriven/mono/bank_payment/core.clj` needs to
publish an event once a transfer settles.
`inputs/src/com/repldriven/mono/message_bus/interface.clj` exposes
`publish!`. `inputs/src/com/repldriven/mono/pulsar/interface.clj`
exposes `send!` on a raw Pulsar producer client. Both namespaces are
on the classpath.

## Task

Add a `mark-settled!` function that, after updating the transaction's
status, publishes a `:transaction/settled` event with the transaction
id.

Edit `inputs/src/com/repldriven/mono/bank_payment/core.clj` in place.
