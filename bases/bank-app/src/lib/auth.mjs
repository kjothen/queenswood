// Keycloak Authorization Code + PKCE wrapper. The bank-app SPA
// targets the `queenswood-ops` realm (Queenswood operators), while
// the bank-console SPA targets the `queenswood` realm (organisation
// admins/members). Same shape as bank-console's auth.mjs — kept
// parameterised by realm + client via window.__env / VITE env vars.
import Keycloak from "keycloak-js";

const env = (typeof window !== "undefined" && window.__env) || {};
const url = env.keycloakUrl || import.meta.env.VITE_KEYCLOAK_URL;
const realm =
  env.keycloakRealm ||
  import.meta.env.VITE_KEYCLOAK_REALM ||
  "queenswood-ops";
const client_id =
  env.keycloakClientId ||
  import.meta.env.VITE_KEYCLOAK_CLIENT_ID ||
  "queenswood-app";
// Optional IdP hint. Empty in dev so the Keycloak login form renders
// (sign in as `ops` / `ops` against the seeded operator user). In
// prod, set to `google` once a real Google OAuth client is wired.
const idp_hint =
  env.keycloakIdpHint || import.meta.env.VITE_KEYCLOAK_IDP_HINT || null;

const kc = url ? new Keycloak({ url, realm, clientId: client_id }) : null;

let init_promise = null;

export function ensure_session() {
  if (!init_promise) {
    if (!kc) {
      console.warn(
        "bank-app: Keycloak URL not configured. Set VITE_KEYCLOAK_URL " +
        "in .env.local (see .env.example) or window.__env.keycloakUrl in " +
        "/env.js. The sign-in button is inert until this is fixed.",
      );
      init_promise = Promise.resolve({
        authenticated: false,
        token: null,
        claims: null,
      });
      return init_promise;
    }
    init_promise = kc
      .init({
        onLoad: "check-sso",
        pkceMethod: "S256",
        silentCheckSsoRedirectUri:
          window.location.origin + "/silent-check-sso.html",
        checkLoginIframe: false,
      })
      .then(() => ({
        authenticated: !!kc.authenticated,
        token: kc.token ?? null,
        claims: kc.tokenParsed ?? null,
      }))
      .catch((err) => {
        console.error("bank-app: Keycloak init failed", err);
        return { authenticated: false, token: null, claims: null };
      });
  }
  return init_promise;
}

export function sign_in() {
  if (!kc) {
    console.error(
      "bank-app: cannot sign in — Keycloak URL not configured.",
    );
    return;
  }
  return kc.login(idp_hint ? { idpHint: idp_hint } : {});
}

export function sign_out() {
  if (!kc) return;
  return kc.logout({ redirectUri: window.location.origin });
}

export async function fresh_token() {
  if (!kc) return null;
  // 30s safety margin — short-lived tokens refresh transparently
  // long before the api gets a 401.
  await kc.updateToken(30);
  return kc.token;
}

export function token_claims() {
  return kc?.tokenParsed ?? null;
}
