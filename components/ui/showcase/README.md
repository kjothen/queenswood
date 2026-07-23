# ui showcase

A living spec for `@queenswood/ui`. Every section mounts the real
exported Svelte component — there is no port, no second source of truth.

## Where this lives

These four files belong at `ui/showcase/` inside the Polylith brick.
Copy them in next to `src/`:

```
ui/
  src/
    index.js
    tokens.css
    Logo.svelte
    Wordmark.svelte
    AppNav.svelte
  showcase/        ← this folder
    index.html
    main.js
    Showcase.svelte
    vite.config.js
```

## Running it

The showcase is a tiny Vite app. It depends on `svelte`, `vite`, and
`@sveltejs/vite-plugin-svelte` — already in the workspace via
`console`, so the easiest path is to add a script to
`ui/package.json`:

```json
"scripts": {
  "showcase": "vite --config showcase/vite.config.js"
}
```

Then from the workspace root:

```sh
yarn workspace @queenswood/ui showcase
```

Or just `cd ui && yarn showcase`.

## Adding a new component

1. Build the component in `ui/src/Foo.svelte`.
2. Export it from `ui/src/index.js`.
3. Add a section to `Showcase.svelte` — copy one of the existing
   `<section>` blocks and replace the content. Sections register in the
   left rail via the `SECTIONS` array at the top of the file.

The point of the page is that the spec and the implementation cannot
drift, because the spec **is** the implementation rendering itself.
