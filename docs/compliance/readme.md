# Compliance

What Queenswood is obliged to do, and how it meets each obligation.

## What belongs here

A sixth register, alongside `adr/`, `recipes/`, `tdd/`, `prd/` and
`plan/`. The others describe things this project decides; these
describe things it does not. An obligation arrives from a regulator or
a standards body, changes on their schedule rather than ours, and is
satisfied somewhere else in the tree.

So a document here states the requirement, names every regime that
states it, and links to the recipe that addresses it. It holds no
procedure of its own — following a link is how you find out what to
actually do.

Two consequences worth stating, because they are what make the register
worth having:

- **Recipes carry no regulation.** A recipe explains how to do
  something and why the shape is what it is, on its own terms. The
  citation for that shape lives here. That keeps a recipe stable when a
  regulation moves, and it keeps the regulation in one place rather
  than scattered across whichever recipes happened to mention it.
- **A gap is visible.** A requirement with no link is a requirement
  nothing addresses. That cannot be seen from inside a recipe, which
  can only describe what it covers, and it is most of the value of
  writing any of this down.

It also gives declarations a home. A recovery objective, a retention
period, a frequency justified by criticality — these are commitments
rather than procedures or decisions, and nothing else in the tree is
shaped to hold one.

## What does not belong here

A compliance claim. These documents say where something is not met, in
the same list and the same voice as where it is. A document asserting
coverage it does not have is worse than no document, and the honest
version is the one that stays useful during an audit rather than
failing one.

Nothing here carries a `tessl-plugin` label. An obligation is not a
rule for writing code; the operational rules derived from these
obligations live in the recipes, and they are what an agent reads.

## The documents

- [data-recovery](data-recovery.md) — backup, restoration and recovery.
  DORA Articles 11 and 12, CIS Control 11, GDPR Article 32(1)(c), NIS2
  Article 21(2)(c), ISO 22301 and ISO/IEC 27031.
