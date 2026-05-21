/* Theme state for @queenswood/bank-ui.

   Three preferences:

     'auto'  — follow OS. No data-theme attribute on <html>; the browser
               picks via color-scheme: light dark + the OS preference.
     'light' — force light. data-theme="light".
     'dark'  — force dark.  data-theme="dark".

   Components import:

     - `themeState`       — reactive { pref, systemDark }, read in any
                            component to render the current state.
     - `setTheme(pref)`   — change the preference (and persist it).
     - `resolvedTheme()`  — get the actual rendered mode ('light' | 'dark'),
                            useful for UI that needs to know what the user
                            is actually seeing (e.g. an icon).

   Apps boot:

     - Import `bootstrapTheme` from "@queenswood/bank-ui" and call it from
       main.js BEFORE mounting your app. It reads localStorage and applies
       data-theme synchronously so there is no flash. With light-dark() in
       tokens.css, even apps that forget to call this still render correctly
       — they just won't honour the user's persisted choice on reload. */

const THEME_KEY = "queenswood.theme";
const mq = typeof window !== "undefined"
  ? window.matchMedia("(prefers-color-scheme: dark)")
  : null;

export const themeState = $state({
  pref: "auto",
  systemDark: mq?.matches ?? false,
});

if (mq) {
  mq.addEventListener("change", (e) => {
    themeState.systemDark = e.matches;
  });
}

function applyToDom(pref) {
  if (typeof document === "undefined") return;
  const root = document.documentElement;
  if (pref === "auto") root.removeAttribute("data-theme");
  else root.setAttribute("data-theme", pref);
}

function normalize(pref) {
  return pref === "light" || pref === "dark" ? pref : "auto";
}

export function bootstrapTheme() {
  let saved = "auto";
  try {
    saved = normalize(localStorage.getItem(THEME_KEY));
  } catch {
    // Private mode / storage disabled — silently fall back to auto.
  }
  themeState.pref = saved;
  applyToDom(saved);
}

export function setTheme(pref) {
  const next = normalize(pref);
  themeState.pref = next;
  applyToDom(next);
  try {
    if (next === "auto") localStorage.removeItem(THEME_KEY);
    else localStorage.setItem(THEME_KEY, next);
  } catch {
    // Persistence is best-effort; the in-memory state is what drives the UI.
  }
}

export function resolvedTheme() {
  if (themeState.pref === "light" || themeState.pref === "dark") {
    return themeState.pref;
  }
  return themeState.systemDark ? "dark" : "light";
}
