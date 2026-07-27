(ns com.repldriven.queenswood.relay.system
  "Bare-require bundle for the relay service — the singleton tier that
  owns every store's changelog cursor and republishes each committed
  entry to the message bus. The group is defined by an ownership
  constraint rather than a domain boundary (ADR-0019): a cursor has
  exactly one owner, so this service runs at `replicas: 1`.

  The runner itself is generic. The two adapter relay bricks are here
  only for their bespoke `relay-handler` kinds, which decode an
  adapter-specific outbox proto; they drop out once every store writes
  the shared envelope.

  Loaded by main.clj before `system/start`; nothing else lives here.
  The service's composition is the project's application.yml."
  (:require
    com.repldriven.queenswood.changelog-relay.interface
    com.repldriven.queenswood.clearbank-relay.interface
    com.repldriven.queenswood.onfido-relay.interface
    com.repldriven.queenswood.schema.interface
    com.repldriven.mono.avro.interface
    com.repldriven.queenswood.fdb.interface
    com.repldriven.mono.kafka.interface
    com.repldriven.mono.message-bus.interface
    com.repldriven.mono.telemetry.interface))
