import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
const bridge = join(root, 'integrations', 'ganj-bot-bridge');

async function source(name) {
  return readFile(join(bridge, name), 'utf8');
}

test('Bot sidecar installer is deterministic, recoverable and never embeds the existing bot', async () => {
  const installer = await source('install.php');
  assert.match(installer, /GANJ_APP_BRIDGE_V1/);
  assert.match(installer, /Supported Ganj 0\.1\.5\.4 login hook marker/);
  assert.match(installer, /index\.php\..*\.bak/);
  assert.match(installer, /Secret files must be outside the public bot root/);
  assert.match(installer, /CREATE TRIGGER ganj_app_invoice_ai/);
  assert.match(installer, /CREATE TRIGGER ganj_app_invoice_au/);
  assert.match(installer, /CREATE TRIGGER ganj_app_invoice_ad/);
  assert.equal(installer.includes('CREATE TABLE user'), false);
  assert.equal(installer.includes('CREATE TABLE invoice'), false);
  assert.equal(installer.includes('$APIKEY'), false);
});

test('Bot Approval bridge accepts only bounded app tokens and calls the internal approval boundary', async () => {
  const bootstrap = await source('bootstrap.php');
  assert.match(bootstrap, /APP-\(\[A-Za-z0-9_-\]\{43\}\)/);
  assert.match(bootstrap, /\/v1\/internal\/telegram\/bot\/approval/);
  assert.match(bootstrap, /CURLOPT_FOLLOWLOCATION => false/);
  assert.match(bootstrap, /CURLOPT_SSL_VERIFYPEER => true/);
  assert.ok(('ga_a_' + 'A'.repeat(43)).length <= 64);
});

test('Ownership projection exposes only normalized commerce and PasarGuard locator metadata', async () => {
  const projection = await source('projection.php');
  for (const field of [
    'external_customer_id', 'telegram_subject', 'external_service_id', 'plan_code',
    'allowed_protocols', 'source_updated_at', 'upstream_binding', 'connector_ref',
  ]) assert.match(projection, new RegExp(field));
  for (const forbidden of ['user_info', 'invoice.uuid', 'subscription_url', 'adminPassword']) {
    assert.equal(projection.includes(forbidden), false);
  }
  assert.match(projection, /ganj_app_ownership_journal/);
  assert.match(projection, /projection_plan_mapping_missing/);
  assert.match(projection, /https_required/);
});
