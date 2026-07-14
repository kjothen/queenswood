# Register the new bank-widget processor

## Background

`bank-widget` is a new command processor that posts entries to the
ledger — a financial-shaped operation. Two group bases already exist:
`inputs/src/com/repldriven/mono/bank_financial_processors/main.clj`
(payment, transaction, interest, payee-check today) and
`inputs/src/com/repldriven/mono/bank_operational_processors/main.clj`
(bank, party, cash-account, cash-account-product, idv today). Each
bare-requires its group's component system namespaces to register
their multimethods at startup.

## Task

Add the bare-require for `bank-widget`'s system namespace
(`com.repldriven.mono.bank-widget.system`) to whichever group base it
belongs in, given that it posts to the ledger. Don't create a new
base or project for it.

Edit exactly one of the two `main.clj` files.
