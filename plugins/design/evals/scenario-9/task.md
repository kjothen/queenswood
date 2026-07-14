# Add a close-account transition to the test model

## Background

`inputs/src/com/repldriven/mono/bank_test_model/accounts.clj` is
`bank-test-model`'s pure re-implementation of account state
transitions, used by the fugato-driven property test alongside the
real system. `inputs/src/com/repldriven/mono/bank_cash_account/core.clj`
is the real, production `bank-cash-account` component's
`close-account` implementation, given for reference on what the rule
actually does: closing is only allowed when the balance is exactly
zero, and it sets `:status :closed`.

## Task

Add a `close-account` transition function to the model, mirroring the
same rule the production component enforces, expressed purely over
the model's own account map (no FDB, no anomalies, no production
imports).

Edit `inputs/src/com/repldriven/mono/bank_test_model/accounts.clj` in
place.
