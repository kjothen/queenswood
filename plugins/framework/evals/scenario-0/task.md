# Add a customer-checked account constructor

## Background

`inputs/src/com/repldriven/mono/bank_account/core.clj` and
`inputs/src/com/repldriven/mono/bank_customers/` are two separate
Queenswood components. `bank-customers` has two files:
`interface.clj`, which exposes `customer-exists?`, and `core.clj`,
which holds that same function's implementation.

## Task

Add an `open-account` function to
`inputs/src/com/repldriven/mono/bank_account/core.clj` that takes
`system`, `customer-id`, and `initial-balance`. Before creating the
account, use the `bank-customers` component to confirm the customer
exists; if the customer is not on record, the operation must fail. On
success, return a new account map with `:owner customer-id`, `:balance
initial-balance`, and `:status :open`.

Edit `inputs/src/com/repldriven/mono/bank_account/core.clj` in place.
