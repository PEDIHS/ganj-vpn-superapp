#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
OPENAPI = ROOT / "api" / "openapi.yaml"

PATH_BEGIN = "  # BEGIN AUTH_SESSION_V1_PATHS\n"
PATH_END_ANCHOR = "  /me:\n"
SCHEMA_BEGIN = "    # BEGIN AUTH_SESSION_V1_SCHEMAS\n"
SCHEMA_END = "    # END AUTH_SESSION_V1_SCHEMAS\n"
SCHEMA_INSERT_ANCHOR = "    DeviceRegistration:\n"

AUTH_PATHS = r'''  # BEGIN AUTH_SESSION_V1_PATHS
  /auth/guest:
    post:
      summary: Create a device-bound guest session
      description: >-
        Registers the installation P-256 signing identity and X25519 encryption identity only
        after a valid GDP1 proof over the canonical unsigned request body. No manual VPN
        configuration or raw credential is accepted.
      tags: [Auth]
      operationId: createGuestSession
      security: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              additionalProperties: false
              required:
                [device_id, key_version, signing_public_key_spki, encryption_public_key_raw, device_proof]
              properties:
                device_id: { type: string, format: uuid }
                key_version: { type: string, pattern: '^v[1-9][0-9]{0,8}$' }
                signing_public_key_spki: { type: string, minLength: 80, maxLength: 512 }
                encryption_public_key_raw:
                  type: string
                  minLength: 43
                  maxLength: 43
                  pattern: '^[A-Za-z0-9_-]{43}$'
                device_proof: { $ref: '#/components/schemas/Gdp1DeviceProof' }
      responses:
        '201':
          description: Guest user/device session with rotating credentials.
          content:
            application/json:
              schema: { $ref: '#/components/schemas/AuthSessionResponse' }
        '400': { $ref: '#/components/responses/BadRequest' }
        '409': { $ref: '#/components/responses/Conflict' }
  /auth/telegram/start:
    post:
      summary: Start Telegram OIDC authorization for the authenticated device
      description: >-
        Creates a short-lived, one-time state bound to the currently authenticated Ganj user and
        device. Only S256 PKCE and server-allowlisted redirect URIs are accepted.
      tags: [Auth]
      operationId: startTelegramOidc
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              additionalProperties: false
              required: [code_challenge, redirect_uri]
              properties:
                code_challenge:
                  type: string
                  minLength: 43
                  maxLength: 43
                  pattern: '^[A-Za-z0-9_-]{43}$'
                redirect_uri:
                  type: string
                  format: uri
                  maxLength: 2048
      responses:
        '200':
          description: OIDC authorization URL and one-time anti-CSRF state.
          content:
            application/json:
              schema: { $ref: '#/components/schemas/TelegramStartResponse' }
        '400': { $ref: '#/components/responses/BadRequest' }
        '401': { $ref: '#/components/responses/Unauthorized' }
  /auth/telegram/exchange:
    post:
      summary: Exchange a one-time Telegram authorization code
      description: >-
        Consumes the stored state under a database lock, validates S256 PKCE, verifies the Telegram
        ID token and atomically links the authenticated device to the resolved Ganj account.
      tags: [Auth]
      operationId: exchangeTelegramCode
      security: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              additionalProperties: false
              required: [code, state, code_verifier]
              properties:
                code: { type: string, minLength: 8, maxLength: 2048 }
                state: { type: string, minLength: 32, maxLength: 512 }
                code_verifier: { type: string, minLength: 43, maxLength: 128 }
      responses:
        '200':
          description: Telegram-linked device-bound session.
          content:
            application/json:
              schema: { $ref: '#/components/schemas/AuthSessionResponse' }
        '400': { $ref: '#/components/responses/BadRequest' }
        '401': { $ref: '#/components/responses/Unauthorized' }
  /auth/refresh:
    post:
      summary: Rotate the device-bound access and refresh session
      description: >-
        Consumes the current opaque refresh token once, validates a fresh GDP1 proof from the same
        device and rotates both credentials. Refresh-token reuse revokes the entire session family.
      tags: [Auth]
      operationId: refreshSession
      security: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              additionalProperties: false
              required: [refresh_token, device_id, device_proof]
              properties:
                refresh_token:
                  type: string
                  minLength: 43
                  maxLength: 43
                  pattern: '^[A-Za-z0-9_-]{43}$'
                device_id: { type: string, format: uuid }
                device_proof: { $ref: '#/components/schemas/Gdp1DeviceProof' }
      responses:
        '200':
          description: Rotated access and refresh credentials.
          content:
            application/json:
              schema: { $ref: '#/components/schemas/AuthSessionResponse' }
        '401': { $ref: '#/components/responses/Unauthorized' }
        '409': { $ref: '#/components/responses/Conflict' }
  /auth/logout:
    post:
      summary: Revoke the current session family
      tags: [Auth]
      operationId: logoutSession
      responses:
        '200':
          description: Current access session and its refresh-token family were revoked.
          content:
            application/json:
              schema: { $ref: '#/components/schemas/LogoutResponse' }
        '401': { $ref: '#/components/responses/Unauthorized' }
'''

AUTH_SCHEMAS = r'''    # BEGIN AUTH_SESSION_V1_SCHEMAS
    Gdp1DeviceProof:
      type: string
      minLength: 80
      maxLength: 1024
      pattern: '^gdp1\.[0-9]+\.[A-Za-z0-9_-]{22,128}\.[A-Za-z0-9_-]{43}\.v[1-9][0-9]{0,8}\.[A-Za-z0-9_-]{80,128}$'
      description: >-
        Replay-resistant P-256/ES256 device proof bound to HTTP method, full /v1 path and query,
        canonical unsigned body SHA-256, timestamp, nonce and registered device key version.
    AuthSessionData:
      type: object
      additionalProperties: false
      required:
        [user_id, device_id, access_token, access_token_expires_at, refresh_token, refresh_token_expires_at, token_type]
      properties:
        user_id: { type: string, format: uuid }
        device_id: { type: string, format: uuid }
        access_token:
          type: string
          minLength: 32
          maxLength: 8192
          description: Short-lived EdDSA bearer JWT; never log or persist outside the secure session vault.
        access_token_expires_at: { type: string, format: date-time }
        refresh_token:
          type: string
          minLength: 43
          maxLength: 43
          pattern: '^[A-Za-z0-9_-]{43}$'
          description: Opaque rotating credential; server storage is SHA-256 digest only.
        refresh_token_expires_at: { type: string, format: date-time }
        token_type: { type: string, enum: [Bearer] }
    AuthSessionResponse:
      type: object
      required: [data, meta, error]
      properties:
        data: { $ref: '#/components/schemas/AuthSessionData' }
        meta: { $ref: '#/components/schemas/Meta' }
        error: { type: 'null' }
    LogoutResponse:
      type: object
      required: [data, meta, error]
      properties:
        data:
          type: object
          additionalProperties: false
          required: [logged_out]
          properties:
            logged_out: { type: boolean, const: true }
        meta: { $ref: '#/components/schemas/Meta' }
        error: { type: 'null' }
    # END AUTH_SESSION_V1_SCHEMAS
'''


def synchronize(text: str) -> str:
    route_start = text.find(PATH_BEGIN)
    if route_start < 0:
        route_start = text.find("  /auth/guest:\n")
    if route_start < 0:
        raise RuntimeError("Could not locate /auth/guest block")
    route_end = text.find(PATH_END_ANCHOR, route_start)
    if route_end < 0:
        raise RuntimeError("Could not locate /me anchor after Auth block")
    text = text[:route_start] + AUTH_PATHS + text[route_end:]

    schema_start = text.find(SCHEMA_BEGIN)
    if schema_start >= 0:
        schema_end = text.find(SCHEMA_END, schema_start)
        if schema_end < 0:
            raise RuntimeError("Auth schema end marker is missing")
        schema_end += len(SCHEMA_END)
        text = text[:schema_start] + AUTH_SCHEMAS + text[schema_end:]
    else:
        schema_anchor = text.find(SCHEMA_INSERT_ANCHOR)
        if schema_anchor < 0:
            raise RuntimeError("Could not locate DeviceRegistration schema anchor")
        text = text[:schema_anchor] + AUTH_SCHEMAS + text[schema_anchor:]
    return text


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="Fail instead of writing when Auth v1 contract is out of sync")
    args = parser.parse_args()

    current = OPENAPI.read_text(encoding="utf-8")
    expected = synchronize(current)
    if args.check:
        if expected != current:
            print("api/openapi.yaml Auth Session v1 block is out of sync; run .github/scripts/sync-auth-openapi.py", file=sys.stderr)
            return 1
        return 0
    if expected != current:
        OPENAPI.write_text(expected, encoding="utf-8")
        print("Synchronized api/openapi.yaml Auth Session v1 contract")
    else:
        print("Auth Session v1 contract already synchronized")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
