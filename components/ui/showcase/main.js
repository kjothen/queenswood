import { mount } from "svelte";
import "../src/tokens.css";
import { bootstrapTheme } from "../src/theme.svelte.js";
import Showcase from "./Showcase.svelte";

// Apply the persisted theme before mounting so the first paint already
// has the right data-theme attribute. Without this the showcase would
// flash light-on-cream on reload for users who have picked dark.
bootstrapTheme();

mount(Showcase, { target: document.getElementById("app") });
