# Ganj VPN Admin Console

Operator-facing Control Plane for the shared Ganj VPN server registry.

## Current scope

- List Free / Premium / VIP servers from `GET /v1/admin/control-plane/servers`.
- Add a server using an opaque secret-store reference only.
- Edit safe routing metadata such as name, country, tier, protocols, load and status.
- Emergency-disable a server without changing or exposing its secret binding.
- Responsive RTL web interface for desktop and mobile operation.

PasarGuard diagnostics, Bot Sync and Shared Account modules remain separate follow-up slices. They must use the same Control API and authoritative data sources rather than introduce a second registry/database.

## Security contract

The console must never receive or render reusable VPN connection material. Responses intentionally omit:

- `secret_ref` / secret-store paths;
- VLESS/VMess/Trojan/Shadowsocks URIs;
- subscription URLs;
- UUID/password/credential material;
- Xray JSON;
- PasarGuard administrator tokens.

The UI receives only `secret_configured: boolean` for secret-binding state.

The API requires the authenticated `admin:control-plane` scope in addition to the normal Control API authentication boundary.

## Admin session

Production should inject a short-lived administrative access token using:

```js
window.__GANJ_ADMIN_SESSION__ = {
  accessToken: async () => obtainShortLivedTokenFromTrustedHostSession(),
};
```

The fallback token dialog is for controlled deployments/development. Its token is held in memory only and is cleared on page exit. The application does not use `localStorage` or `sessionStorage` for credentials.

An API origin can be supplied before loading the module:

```js
window.__GANJ_ADMIN_CONFIG__ = {
  apiBaseUrl: 'https://control.example.com',
};
```

Remote API origins must use HTTPS. Same-origin deployment behind the authenticated operator gateway is preferred.

## Local checks

```bash
npm --prefix apps/admin-console run check
npm --prefix apps/admin-console test
npm --prefix apps/admin-console run serve
```

The development server binds to `127.0.0.1:4173` by default and emits no-store, CSP, frame-deny, no-referrer and restricted Permissions-Policy headers.

## Production deployment

Serve the static console only behind the administrative authentication gateway. Preserve or strengthen these headers:

- `Cache-Control: no-store`
- `Content-Security-Policy`
- `Referrer-Policy: no-referrer`
- `X-Frame-Options: DENY` or equivalent `frame-ancestors 'none'`
- `X-Content-Type-Options: nosniff`
- `Cross-Origin-Opener-Policy: same-origin`

Do not publish this console as an anonymous public static site with a reusable bearer token embedded in HTML, JavaScript, environment files or build artifacts.
