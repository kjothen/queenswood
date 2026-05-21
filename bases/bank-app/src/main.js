import { mount } from "svelte";
import "@queenswood/bank-ui/tokens.css";
import { bootstrapTheme } from "@queenswood/bank-ui";
import App from "./App.svelte";

// Apply the persisted theme preference before mount so the page
// doesn't flash light-then-dark on reload for users who have chosen
// a manual override. Without this, CSS still handles `auto` via
// `color-scheme`, but the localStorage choice wouldn't be honoured.
bootstrapTheme();

const app = mount(App, { target: document.getElementById("app") });

export default app;
