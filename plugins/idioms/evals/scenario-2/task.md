# Add a deposit test

## Background

`inputs/test/com/repldriven/mono/bank_account/core_test.clj` is an
empty Queenswood test namespace for the `bank-account` component's
`deposit` function, which takes a `system` map, an `account-id`, and an
`amount`. In this codebase, `system` is always produced by starting the
project's system from its config — tests never hand-construct a system
map by writing out a literal like `{:accounts {...}}` themselves.

## Task

Add a test, `deposit-test`, to
`inputs/test/com/repldriven/mono/bank_account/core_test.clj`. It should
start an account `"acc-1"` at balance `100`, deposit `50` via
`bank-account/deposit`, and assert the resulting balance is `150`.

Edit `inputs/test/com/repldriven/mono/bank_account/core_test.clj` in
place.
