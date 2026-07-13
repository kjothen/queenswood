# Add account-closure logic

## Background

`inputs/src/com/repldriven/mono/bank_account/core.clj` is a Queenswood
`bank-account` component's core namespace.

## Task

Add a `close-account` function to
`inputs/src/com/repldriven/mono/bank_account/core.clj`. Closing an
account is only allowed when its balance is exactly zero. On success,
return the account with `:status` set to `:closed`. When the balance is
non-zero, the close must fail.

Edit `inputs/src/com/repldriven/mono/bank_account/core.clj` in place.
