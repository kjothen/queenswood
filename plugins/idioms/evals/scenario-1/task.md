# Add an account constructor

## Background

`inputs/src/com/repldriven/mono/bank_account/core.clj` is a Queenswood
`bank-account` component's core namespace.

## Task

Add an `open-account` function to
`inputs/src/com/repldriven/mono/bank_account/core.clj` that takes
`customer-id` and `initial-balance` and returns a new account map
shaped like:

```clojure
{:id          <freshly generated id>
 :owner       customer-id
 :balance     initial-balance
 :status      :open
 :opened-at   <current timestamp>}
```

Edit `inputs/src/com/repldriven/mono/bank_account/core.clj` in place.
