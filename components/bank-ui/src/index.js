// Public entry point of the @queenswood/bank-ui design system.
// Consumers reach Svelte components via named imports here, the design
// tokens via the side-effect import, and the theme state via the
// `theme.svelte.js` re-exports:
//
//   import { Logo, Wordmark, AppNav, ThemeToggle } from "@queenswood/bank-ui";
//   import { bootstrapTheme, setTheme, themeState, resolvedTheme }
//     from "@queenswood/bank-ui";
//   import "@queenswood/bank-ui/tokens.css";

export { default as Logo } from "./Logo.svelte";
export { default as Wordmark } from "./Wordmark.svelte";
export { default as AppNav } from "./AppNav.svelte";
export { default as ThemeToggle } from "./ThemeToggle.svelte";

export {
  themeState,
  setTheme,
  bootstrapTheme,
  resolvedTheme,
} from "./theme.svelte.js";
