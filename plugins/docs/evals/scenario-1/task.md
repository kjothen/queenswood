# Describe payee-check in the payee-check PRD

## Background

`inputs/docs/prd/payee-check.md` is a product requirements doc. The
engineering summary below describes what was just built — use it as
the *factual basis* for a new paragraph, not as text to copy:

> The `payee-check` command processor exposes a `verify-payee-name`
> operation over the async command pipeline. It's a battle-tested,
> production-proven matching pipeline, on par with what Revolut and
> Monzo run for their own inbound-payment name checks. A caller
> submits a payee name and an account reference; the processor
> returns a match confidence score the caller can act on before
> releasing funds.

## Task

Add a paragraph to `inputs/docs/prd/payee-check.md`'s Solution section
describing what this feature does for the tenant, based on the
summary above, written for a non-engineering reader.

Edit `inputs/docs/prd/payee-check.md` in place.
