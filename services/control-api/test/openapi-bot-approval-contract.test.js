import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const openapi = readFileSync(new URL('../../../api/openapi.yaml', import.meta.url), 'utf8');

function count(value) {
  return openapi.split(value).length - 1;
}

test('canonical OpenAPI exposes Bot Approval as the primary Telegram login contract', () => {
  assert.match(openapi, /version: 0\.3\.0/);
  assert.equal(count('operationId: startTelegramBotApproval'), 1);
  assert.equal(count('operationId: getTelegramBotApprovalStatus'), 1);
  assert.equal(count('operationId: exchangeTelegramBotApproval'), 1);
  assert.equal(count('operationId: applyTelegramBotApprovalDecision'), 1);

  assert.match(openapi, /\/auth\/telegram\/bot\/start:/);
  assert.match(openapi, /\/auth\/telegram\/bot\/requests\/\{request_id\}:/);
  assert.match(openapi, /\/auth\/telegram\/bot\/exchange:/);
  assert.match(openapi, /\/internal\/telegram\/bot-approval:/);
});

test('Bot Approval contract preserves state PKCE and owner-bound response shapes', () => {
  assert.match(openapi, /required: \[request_id, bot_url, state, expires_at\]/);
  assert.match(openapi, /required: \[request_id, status, expires_at\]/);
  assert.match(openapi, /status: \{ type: string, enum: \[pending, approved, denied, consumed, expired\] \}/);
  assert.match(openapi, /required: \[request_id, state, code_verifier\]/);
  assert.match(openapi, /pattern: '\^\[A-Za-z0-9\._~-\]\+\$'/);
});

test('internal Bot decision contract is HMAC-authenticated and not an end-user bearer route', () => {
  const internalStart = openapi.indexOf('  /internal/telegram/bot-approval:');
  const nextRoute = openapi.indexOf('\n  /auth/telegram/start:', internalStart);
  assert.ok(internalStart >= 0 && nextRoute > internalStart);
  const internalBlock = openapi.slice(internalStart, nextRoute);

  assert.match(internalBlock, /security: \[\]/);
  assert.match(internalBlock, /name: X-Ganj-Bot-Timestamp/);
  assert.match(internalBlock, /name: X-Ganj-Bot-Signature/);
  assert.match(internalBlock, /HMAC-SHA256/);
  assert.match(internalBlock, /required:\n\s+\[approval_token, event_id, decision, telegram_subject, telegram_username, telegram_display_name\]/);
  assert.match(internalBlock, /decision: \{ type: string, enum: \[approve, deny\] \}/);
});

test('OIDC remains documented only as a guarded fallback', () => {
  const oidcStart = openapi.indexOf('  /auth/telegram/start:');
  const oidcExchange = openapi.indexOf('  /auth/telegram/exchange:');
  assert.ok(oidcStart >= 0 && oidcExchange > oidcStart);
  const startBlock = openapi.slice(oidcStart, oidcExchange);
  assert.match(startBlock, /guarded fallback/);
  assert.match(startBlock, /Product UI must\n\s+not present this endpoint as the primary Telegram login path\./);

  assert.equal(count('operationId: startTelegramOidc'), 1);
  assert.equal(count('operationId: exchangeTelegramCode'), 1);
});
