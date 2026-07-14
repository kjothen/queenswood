# Register the widget component's processor

## Background

`inputs/src/com/repldriven/mono/widget/system.clj` defines a
`processor` component map (`:system/start` / `:system/config` /
`:system/instance-schema`) but never registers it.
`inputs/src/com/repldriven/mono/widget/interface.clj` is the
component's public API — it doesn't yet load the system namespace, so
requiring the component doesn't extend its multimethods.

## Task

- In `system.clj`, register `processor` under the `:widget` group via
  `system/defcomponents`, keyed `:processor`.
- In `interface.clj`, make sure requiring the component triggers that
  registration.

Edit both files in place.
