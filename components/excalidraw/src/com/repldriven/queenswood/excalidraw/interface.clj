(ns com.repldriven.queenswood.excalidraw.interface
  "Placeholder interface for the excalidraw brick.

  excalidraw is a JS-only Polylith component: its source lives under
  `src/` as ES modules that drive Excalidraw's own `exportToSvg` in a
  headless browser, turning the `.excalidraw` scenes under
  `docs/diagrams` into the light and dark SVGs committed beside them.
  Nothing on the classpath consumes it and no service ships it. This
  namespace exists solely so `poly` recognises the brick and reports its
  npm dependencies in `poly libs`.")
