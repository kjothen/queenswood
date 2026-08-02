# Plan: statementing, and the questions it answers about interest

## Context

Two questions come from outside the bank and cannot be answered well
today:

- *What interest was paid to this account in the tax year?* — a tax
  certificate, cut once a year.
- *What happened on this account last month?* — a statement, cut every
  month.

They look like different problems and are not. Both are "the movements
on this account, of some kind, between two dates", and interest is one
kind among fees, transfers and payments. This document is about that
shared primitive, and about what it releases in
[interest-batch-pass.md](interest-batch-pass.md), which currently
carries the questions as open caveats.

## The source of truth is the transactions

Three candidate sources were considered and two rejected.

**`InterestAccountRun` rows are not it.** They record what a run did,
account by account, and they are meant to age out. Keeping them for
seven years to answer a tax question would couple retention to
reporting, and the coupling gets worse rather than better under daily
capitalisation, where the rows arrive as fast as accrual's. Their key
is also wrong for the question: `[bank_id, business_day, kind,
account_id]` answers "one day, every account", where a statement asks
"one account, every day".

**A cumulative `interest-paid` balance is not it either.** The bucket
exists — all three customer product templates declare it, and
`fans-out?` deliberately excludes it from general-ledger roll-up, so it
was intended as a sub-ledger memo. Nothing writes it. Wiring it up
looks attractive until the period question is asked of it: a balance is
one value at one instant, so a figure between two dates needs its value
at both ends, and the only record of how it moved is the transactions.
The bucket cannot answer the question without the thing that would have
answered it anyway. It should be dropped from the templates rather than
left on every customer account with nothing writing it.

**The transactions are it.** A capitalisation posts a `Transaction` and
its legs precisely because that posting *is* the customer's statement
line — this is the one part of interest a customer ever sees. They are
durable for the same reason: a statement line cannot be aged out. The
history is already there, in the right place, for the right reason.

## What is missing is the access path, not the data

`TransactionLeg` is keyed `[account_id, transaction_id, leg_id]`, and
ids are monotonic ULIDs, so an account's legs are already stored in
chronological order and a date range is a key range rather than a
filter. That much is right.

Two things stop it being usable.

**The leg does not carry `transaction_type`.** It carries `leg_id`,
`transaction_id`, `account_id`, `balance_type`, `balance_status`,
`side`, `amount`, `currency` and `created_at` — enough to know what
moved, but not what kind of event moved it. `get-transactions` fills
the gap by loading each leg's parent `Transaction`, so narrowing to one
kind costs a point read per leg across the account's whole history.

**There is no index for the question.** The only index on
`transactions` is `Transaction_by_idempotency_key`, and
`transaction-legs` has none at all.

So the work is:

- **`TransactionLeg` gains `transaction_type`.** It is immutable once
  written, so the denormalisation cannot drift, and it removes the
  per-leg parent read outright.
- **An index on `[account_id, transaction_type, created_at]`.** The
  question then reads only the entries for that account and kind inside
  that window.
- **`get-transactions` stops truncating in silence.** It caps at 1000
  and says nothing, which is already recorded in the interest plan as a
  call site to fix. A statement that silently omits its oldest lines is
  worse than one that fails.

A grouped SUM index was considered and is not proposed. Keyed down to
`created_at` it holds an entry per leg, so it costs about what the
plain index costs to read while returning strictly less: a statement
needs the lines themselves, not only their total, and a total folds out
of the lines for free.

## A leg is one side of a posting

Summing an account's legs naively double-counts, and this is the
detail most likely to be got wrong.

Capitalisation posts two legs against the *same* account: a debit to
the interest-accrued bucket and a credit to the default bucket. Both
carry the same amount. A sum over `[account_id, transaction_type]`
therefore counts the interest twice, or nets it to zero if the sides
are signed.

So the query has to name the bucket it means. "Interest paid" is the
credit to `default`/`posted` — the money arriving where the customer
can spend it. `balance_type` and `balance_status` are what disambiguate
it, and any statement query needs them for the same reason: a statement
is the movements on the spendable bucket, not on every internal bucket
that moved alongside it.

## Periods do not align, and must not be assumed to

A tax year runs 6 April to 5 April, and a statement period is a
calendar month or a per-account cycle date. They do not share
boundaries, and a design that sums twelve stored monthly figures to
reach a tax year will be wrong wherever a capitalisation lands between
the two.

That is not hypothetical. A monthly product capitalising at month end
would get away with it; a term deposit capitalising on maturity or on
its anniversary lands on any day of the month.

Ranging over `created_at` is indifferent to this, because a
capitalisation is a point event that falls on one side of any boundary.
Stored per-period totals are not. So the range is the primitive, and
any per-period figure is derived from it rather than the other way
round.

## Daily capitalisation is a product decision with a price

Capitalising daily rather than monthly is a real product choice and the
design should allow it. Two things follow, and both are worth stating
before someone chooses it by default.

**The volume is inherent.** A million accounts capitalising daily is
about 365 million transactions and 730 million legs a year. That is the
write amplification [interest-batch-pass.md](interest-batch-pass.md)
removed from accrual, reappearing on the capitalisation side — this
time deliberately, because unlike accrual a capitalisation *is* a
statement line and the customer is meant to see it. No index makes that
cheaper. It is the cost of showing interest daily.

**The accrued bucket nearly disappears.** Sweeping every day leaves it
holding only the sub-unit carry between runs, since whole units reach
the spendable balance the day they are earned. It stops being a
month-long accumulation and becomes a remainder register. Nothing built
so far breaks, but the two-pass split is doing much less work in that
configuration, and whether a daily-capitalising product should run one
pass rather than two is worth deciding rather than inheriting.

## What this releases in the interest plan

- **Retention stops being coupled to reporting.**
  `InterestAccountRun` rows can age out on a short window, because
  nothing outside the run needs them. The interest plan lists the
  retention number as unpicked; this removes the reason it was hard.
- **The reporting cut has an answer that needs no snapshot.** The
  interest plan's one open question — whether a reporting cut that has
  to foot is separately required, and would bring back a snapshot
  artefact — is answered by the transactions, which are already the
  cut and already foot.
- **`interest-paid` can go.** It has no job once the movements answer
  the question.

## Open questions

- **Is a statement period a calendar month or a per-account cycle
  date?** This decides whether the off-cycle boundary case is real or
  merely possible.
- **Do term deposits capitalise on maturity rather than on the monthly
  run?** Same decision, from the product side.
- **Is a statement itself a record, or is it rendered on demand?**
  Rendering on demand keeps one source of truth and no reconciliation;
  storing it fixes what the customer was shown, which is usually what a
  regulator wants. The primitive above serves either, and this document
  does not choose.
- **Does anything need the figure net of withholding?** Interest is
  paid gross here, so nothing does today. It would change what a
  certificate has to carry rather than where the numbers come from.
