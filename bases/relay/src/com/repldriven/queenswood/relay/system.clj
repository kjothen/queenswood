(ns com.repldriven.queenswood.relay.system
  "Bare-require bundle for the relay service — the singleton tier that
  owns every store's changelog cursor and republishes each committed
  entry to the message bus. The group is defined by an ownership
  constraint rather than a domain boundary (ADR-0019): a cursor has
  exactly one owner, so this service runs at `replicas: 1`.

  Every store writes the shared envelope, so one generic handler
  decodes them all and the adapter relay bricks are no longer needed
  here — the outbox protos reuse `ChangelogEvent`'s field numbers, so
  their entries decode as one.

  Loaded by main.clj before `system/start`; nothing else lives here.
  The service's composition is the project's application.yml."
  (:require
    [com.repldriven.queenswood.changelog-relay.interface]
    [com.repldriven.queenswood.fdb.interface]
    [com.repldriven.queenswood.schema.interface]

    [com.repldriven.mono.avro.interface]
    [com.repldriven.mono.kafka.interface]
    [com.repldriven.mono.message-bus.interface]
    [com.repldriven.mono.telemetry.interface]))
