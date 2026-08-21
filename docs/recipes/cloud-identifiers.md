# Writing about an installation

<!-- tessl-plugin: deployment -->

## Problem

Everything written about this system is written somewhere permanent and
mostly somewhere public: a commit message, a pull request description,
an issue, a comment, a recipe. Working on cloud infrastructure means
having real identifiers in front of you constantly — they are in the
terminal output you are reading when you write the sentence — and the
easiest sentence to write is the one naming what you just saw.

None of them is a credential. That is exactly why they get written
down: nothing feels risky about pasting a project id. What an
organisation, folder or billing account id is good for is sounding like
somebody who already has access, on a support call or in an email, and
a public repository is where that person looks first.

There is a guard, and it is detection. It has caught real identifiers
and it has also been extended, by somebody who had read it that
morning, in a pull request whose own description carried a folder id.
Detection tells you afterwards. This is the half that is meant to stop
it being written.

## Solution

### Name the shape, never the instance

A name shape says how things are named, which is what a reader needs. A
realised one says which project, which is what nobody needs and what
somebody else wants.

```
prj-qw01-c-mgmt-xxxxxx        the shape, and what to write
folders/<folder-id>           likewise
bkt-qw01-n-test-backups-xxxxxx
```

`xxxxxx` for a suffix, `<folder-id>` and `<org-id>` and
`<project-number>` for the things that are only a number. They read as
placeholders, they cannot be mistaken for real, and `x` is not a hex
digit so the guard cannot match one either.

### What counts

- **A project id's suffix** — six hex characters closing a name.
- **A folder, organisation or project number** — nine to twelve digits,
  with or without the `folders/` in front.
- **A billing account id** — three hex groups.
- **A public IP address.** The installation's front door. A private
  range is not one of these: `10.10.0.0` is `10.10.0.0` everywhere and
  says only how a network is cut.

### What does not

Name shapes, and the rest of a name. Private ranges, loopback, the
documentation ranges. A FoundationDB version, which is nine to twelve
digits and is what this project's own support procedures print. A git
sha. A Job's name-hash, which describes one afternoon's state and reads
as though it meant something, but identifies no account.

### The habit that actually prevents it

**Mask on the way in, not on the way out.** The moment to replace a
suffix with `xxxxxx` is while writing the sentence, with the real value
still on screen — not after a check has failed, and not after a review.
Every identifier this project has had to scrub was written by somebody
who would have masked it if they had thought of it then.

Two places that is easy to forget, because the text is not yours:

- **Pasted terminal output.** An error message names the project it
  came from. Quoting it is the most natural thing in a description and
  it carries the id verbatim.
- **An example.** Writing about the guard, illustrate it with a
  placeholder. A real identifier used as a demonstration is still a
  real identifier, and it is how the last one got out.

### Where it applies

Everywhere the text can be read: the tree, a commit message, a pull
request's title and body, an issue, a comment, a review. The private
`installations` repository is where real ids belong, because that is
what it is for — but a description *of* a change to it is written in
the public one.

### When a real one belongs

Rarely, and then say so with `cloud-id-ok` on the line. It is an
assertion by the author that this value is meant, which is the point:
it makes the exception visible and attributable rather than silent.

## Rules

**MUST:**

- Write a name's shape and mask what identifies the instance —
  `xxxxxx`, `<folder-id>`, `<org-id>`, `<project-number>`.
- Mask while writing, with the real value still in front of you.
- Mask a pasted error message and a worked example the same way. Both
  are text somebody else produced, and both carry ids verbatim.

**MUST NOT:**

- Put a project id suffix, a folder, organisation or project number, a
  billing account id or a public address into anything public,
  including a pull request's description and a comment.
- Use a real identifier as an example of a masked one.

**MAY:**

- Name a private range, a version, a git sha or a Job's name-hash.
  None of them identifies an account.
- Mark a line `cloud-id-ok` where a real value genuinely belongs, which
  makes it deliberate rather than missed.

## References

- [cloud-naming](cloud-naming.md) — what things are called, and why a
  suffix exists at all
- [ADR-0023](../adr/0023-installation-naming-and-access.md) — the
  naming and access decision underneath both
- `scripts/hooks/check-cloud-ids.sh` — the detection half, and the one
  definition of what an identifier is
