# Assemble the widget-service deployable project

## Background

`inputs/projects/widget-service/deps.edn` is a new deployable
project's `deps.edn`, currently pinning only the Clojure version. The
project should bundle the `widget` component and the `widget-service`
base — both exist at `../../components/widget` and
`../../bases/widget-service` relative to the project directory — and
be buildable into an uberjar via the shared `bases/build` machinery
(at `../../bases/build`), targeting `com.repldriven.mono.widget-service.main`
as the entry namespace.

## Task

Complete `inputs/projects/widget-service/deps.edn` so it:

- Lists the `widget` component and `widget-service` base as
  `:local/root` dependencies.
- Has a `:build` alias depending on `bases/build`, with `:exec-args`
  naming the project's lib coordinate
  (`com.repldriven.mono/widget-service`), `:main
  com.repldriven.mono.widget-service.main`, and a major-minor version.

Don't add any Clojure source files — a project holds only
configuration.

Edit `inputs/projects/widget-service/deps.edn` in place.
