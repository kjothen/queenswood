# Wire up the widget-service base's entry point

## Background

`inputs/src/com/repldriven/mono/widget_service/main.clj` is a new
base's entry namespace — currently just the `ns` form. Two files for
the `widget` component are also given:
`inputs/src/com/repldriven/mono/widget/interface.clj`, which exposes
`start-widget-processor`, and
`inputs/src/com/repldriven/mono/widget/core.clj`, which holds that
same function's implementation.

## Task

Finish `main.clj`: define `-main` so it calls the `widget` component's
`start-widget-processor` with the (already-parsed) `config` argument,
then blocks so the JVM stays alive while the processor runs. Expose
`-main` as a Java entry point.

Edit `inputs/src/com/repldriven/mono/widget_service/main.clj` in
place.
