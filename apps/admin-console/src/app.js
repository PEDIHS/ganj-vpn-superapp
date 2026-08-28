import { AdminApiClient, AdminApiError, createMemoryTokenProvider } from './api.js';
import { clearSensitiveInput, consumeFreeConfig, requireAuditReason } from './ephemeral-import.js';

const tokenStore = createMemoryTokenProvider();
const hostSession = window.__GANJ_ADMIN_SESSION__;
if (hostSession && typeof hostSession.accessToken === 'function') tokenStore.provider = async () => hostSession.accessToken();

const api = new AdminApiClient({
  baseUrl: window.__GANJ_ADMIN_CONFIG__?.apiBaseUrl ?? window.location.origin,
  accessTokenProvider: tokenStore.provider,
});

const ui = {
  grid: document.querySelector('#serverGrid'), empty: document.querySelector('#emptyState'), banner: document.querySelector('#statusBanner'),
  search: document.querySelector('#searchInput'), refresh: document.querySelector('#refreshButton'), openCreate: document.querySelector('#openCreateButton'),
  pageTitle: document.querySelector('#pageTitle'), pageSubtitle: document.querySelector('#pageSubtitle'),
  sessionDialog: document.querySelector('#sessionDialog'), sessionForm: document.querySelector('#sessionForm'), tokenInput: document.querySelector('#tokenInput'),
  serverDialog: document.querySelector('#serverDialog'), serverForm: document.querySelector('#serverForm'), save: document.querySelector('#saveServerButton'), title: document.querySelector('#serverDialogTitle'),
  id: document.querySelector('#serverId'), code: document.querySelector('#serverCode'), name: document.querySelector('#serverName'), country: document.querySelector('#serverCountry'), city: document.querySelector('#serverCity'), tier: document.querySelector('#serverTier'), status: document.querySelector('#serverStatus'), load: document.querySelector('#serverLoad'), latency: document.querySelector('#serverLatency'), protocolField: document.querySelector('#protocolField'), secret: document.querySelector('#serverSecretReference'), secretField: document.querySelector('#secretReferenceField'), freeFields: document.querySelector('#freeImportFields'), config: document.querySelector('#serverConfig'), reason: document.querySelector('#serverReason'),
  connectorList: document.querySelector('#connectorList'), reloadConnectors: document.querySelector('#reloadConnectors'), diagnosticForm: document.querySelector('#diagnosticForm'), diagnosticServiceId: document.querySelector('#diagnosticServiceId'), diagnosticResult: document.querySelector('#diagnosticResult'),
  reloadBotSync: document.querySelector('#reloadBotSync'), sourceList: document.querySelector('#sourceList'), conflictList: document.querySelector('#conflictList'), conflictCount: document.querySelector('#conflictCount'),
  accountForm: document.querySelector('#accountForm'), accountUserId: document.querySelector('#accountUserId'), accountResult: document.querySelector('#accountResult'),
  metrics: { total: document.querySelector('#metricTotal'), active: document.querySelector('#metricActive'), free: document.querySelector('#metricFree'), attention: document.querySelector('#metricAttention') },
};

const state = { servers: [], tier: 'all', query: '', loading: false, saving: false, view: 'servers' };
const statusLabels = Object.freeze({ active: 'فعال', busy: 'شلوغ', maintenance: 'نگهداری', disabled: 'غیرفعال' });
const tierLabels = Object.freeze({ free: 'FREE', premium: 'PREMIUM', vip: 'VIP' });
const viewMeta = Object.freeze({
  servers: ['Server Registry', 'مدیریت Free / Premium / VIP از یک Control Plane مشترک'],
  pasarguard: ['PasarGuard Operations', 'سلامت Connectorها و تشخیص وضعیت زنده سرویس‌های پولی'],
  'bot-sync': ['Bot Sync', 'وضعیت Projection، checkpoint و Conflictهای همگام‌سازی ربات'],
  accounts: ['Shared Accounts', 'بررسی Telegram link، دستگاه‌ها، سرویس‌ها و reconciliation حساب'],
});

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>'"]/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[char]);
}
function safeJson(value) { return JSON.stringify(value, null, 2); }
function setBanner(message, error = false) { ui.banner.textContent = message; ui.banner.classList.toggle('error', error); ui.banner.classList.toggle('hidden', !message); }
function requireSession() { if (hostSession && typeof hostSession.accessToken === 'function') return true; if (tokenStore.get()) return true; ui.sessionDialog.showModal(); return false; }

function renderMetrics() {
  ui.metrics.total.textContent = String(state.servers.length);
  ui.metrics.active.textContent = String(state.servers.filter((item) => item.status === 'active').length);
  ui.metrics.free.textContent = String(state.servers.filter((item) => item.tier === 'free').length);
  ui.metrics.attention.textContent = String(state.servers.filter((item) => ['maintenance', 'disabled'].includes(item.status)).length);
}
function filteredServers() {
  const query = state.query.trim().toLocaleLowerCase('fa');
  return state.servers.filter((server) => {
    if (state.tier !== 'all' && server.tier !== state.tier) return false;
    if (!query) return true;
    return [server.name, server.code, server.country_code, server.city].filter(Boolean).some((value) => String(value).toLocaleLowerCase('fa').includes(query));
  });
}
function serverCard(server) {
  const load = `${Math.round(Number(server.load_ratio ?? 0) * 100)}%`;
  const latency = server.latency_hint_ms == null ? '—' : `${server.latency_hint_ms} ms`;
  const protocols = (server.protocols ?? []).map((item) => `<span>${escapeHtml(item.toUpperCase())}</span>`).join('');
  const nextStatus = server.status === 'disabled' ? 'maintenance' : 'disabled';
  const toggleLabel = server.status === 'disabled' ? 'برگرداندن به نگهداری' : 'غیرفعال فوری';
  const secretConfigured = server.secret_configured ?? server.secret_ref_configured;
  return `<article class="server-card glass" data-server-id="${escapeHtml(server.id)}"><div class="server-head"><div class="flag">${escapeHtml(server.country_code)}</div><div class="server-title"><h3>${escapeHtml(server.name)}</h3><p>${escapeHtml(server.code)} · ${escapeHtml(server.city || server.country_code)}</p></div><span class="status-pill ${escapeHtml(server.status)}">${escapeHtml(statusLabels[server.status] ?? server.status)}</span></div><div class="server-meta"><div><span>Tier</span><strong>${escapeHtml(tierLabels[server.tier] ?? server.tier)}</strong></div><div><span>Load</span><strong>${escapeHtml(load)}</strong></div><div><span>Latency</span><strong>${escapeHtml(latency)}</strong></div></div><div class="protocol-list">${protocols}</div><div class="card-actions"><span class="secret-state">${secretConfigured ? '● اتصال امن ثبت‌شده' : '○ اتصال امن نامشخص'}</span><div><button type="button" data-action="toggle" data-next-status="${nextStatus}">${toggleLabel}</button><button type="button" data-action="edit">ویرایش</button></div></div></article>`;
}
function render() { renderMetrics(); const servers = filteredServers(); ui.grid.innerHTML = servers.map(serverCard).join(''); ui.empty.classList.toggle('hidden', servers.length !== 0 || state.loading); }

async function refreshServers() {
  if (state.loading || !requireSession()) return;
  state.loading = true; ui.refresh.disabled = true; setBanner('در حال دریافت وضعیت Control Plane…');
  try { state.servers = await api.listServers(); setBanner(''); }
  catch (error) { if (error instanceof AdminApiError && error.status === 401 && !hostSession) { tokenStore.clear(); ui.sessionDialog.showModal(); } setBanner(error instanceof Error ? error.message : 'دریافت اطلاعات ناموفق بود.', true); }
  finally { state.loading = false; ui.refresh.disabled = false; render(); }
}

function setCreateMode() {
  const editing = Boolean(ui.id.value), isFreeImport = !editing && ui.tier.value === 'free';
  ui.freeFields.classList.toggle('hidden', !isFreeImport); ui.protocolField.classList.toggle('hidden', isFreeImport); ui.secretField.classList.toggle('hidden', editing || isFreeImport);
  ui.config.required = isFreeImport; ui.reason.required = isFreeImport; ui.secret.required = !editing && !isFreeImport; if (!isFreeImport) clearSensitiveInput(ui.config);
}
function resetForm() { clearSensitiveInput(ui.config); ui.serverForm.reset(); ui.id.value = ''; ui.code.disabled = false; ui.status.value = 'maintenance'; ui.load.value = '0'; document.querySelector('input[name="protocol"][value="vless"]').checked = true; setCreateMode(); }
function openCreate() { if (!requireSession()) return; resetForm(); ui.title.textContent = 'سرور جدید'; ui.serverDialog.showModal(); }
function openEdit(server) { resetForm(); ui.title.textContent = 'ویرایش سرور'; ui.id.value = server.id; ui.code.value = server.code; ui.code.disabled = true; ui.name.value = server.name; ui.country.value = server.country_code; ui.city.value = server.city ?? ''; ui.tier.value = server.tier; ui.status.value = server.status; ui.load.value = String(server.load_ratio ?? 0); ui.latency.value = server.latency_hint_ms ?? ''; for (const checkbox of document.querySelectorAll('input[name="protocol"]')) checkbox.checked = (server.protocols ?? []).includes(checkbox.value); setCreateMode(); ui.serverDialog.showModal(); }
function commonPayload() { return { name: ui.name.value.trim(), country_code: ui.country.value.trim().toUpperCase(), city: ui.city.value.trim() || null, status: ui.status.value, load_ratio: Number(ui.load.value), latency_hint_ms: ui.latency.value === '' ? null : Number(ui.latency.value) }; }
function managedServerPayload() { const protocols = [...document.querySelectorAll('input[name="protocol"]:checked')].map((item) => item.value); return { ...commonPayload(), tier: ui.tier.value, protocols }; }
async function saveServer() {
  if (state.saving) return; const id = ui.id.value, isFreeImport = !id && ui.tier.value === 'free'; state.saving = true; ui.save.disabled = true;
  try {
    if (id) await api.updateServer(id, managedServerPayload());
    else if (isFreeImport) { const reason = requireAuditReason(ui.reason.value); await consumeFreeConfig(ui.config, (config) => api.importFreeServer({ ...commonPayload(), code: ui.code.value.trim(), config, reason })); }
    else await api.createServer({ ...managedServerPayload(), code: ui.code.value.trim(), secret_reference: ui.secret.value.trim() });
    ui.serverDialog.close(); await refreshServers(); setBanner(id ? 'تغییرات سرور ذخیره شد.' : isFreeImport ? 'کانفیگ رایگان در Secret Store ثبت و از فرم پاک شد.' : 'اتصال PasarGuard سرور پولی در Registry ثبت شد.');
  } catch (error) { setBanner(error instanceof Error ? error.message : 'ذخیره سرور ناموفق بود.', true); }
  finally { if (isFreeImport) clearSensitiveInput(ui.config); state.saving = false; ui.save.disabled = false; }
}
async function toggleServer(server, status) { try { const saved = await api.updateServer(server.id, { status, ...(status === 'disabled' ? { load_ratio: 0 } : {}) }); state.servers[state.servers.findIndex((item) => item.id === saved.id)] = saved; setBanner(status === 'disabled' ? 'سرور فوراً از دسترس کاربران خارج شد.' : 'سرور به حالت نگهداری برگشت.'); render(); } catch (error) { setBanner(error instanceof Error ? error.message : 'تغییر وضعیت ناموفق بود.', true); } }

function connectorCard(item) { return `<div class="ops-row"><div><strong>${escapeHtml(item.connector_ref)}</strong><small>${escapeHtml(item.endpoint_origin)}${escapeHtml(item.base_path)}</small></div><div class="ops-tags"><span>${item.credential_configured ? 'Credential ✓' : 'Credential ✕'}</span><span>${escapeHtml(item.country_code ?? '—')}</span><button class="button secondary small" data-connector-health="${escapeHtml(item.connector_ref)}">Health</button></div></div>`; }
async function loadConnectors() { if (!requireSession()) return; ui.connectorList.innerHTML = '<p class="ops-copy">در حال دریافت…</p>'; try { const data = await api.listPasarGuardConnectors(); ui.connectorList.innerHTML = data.length ? data.map(connectorCard).join('') : '<p class="ops-copy">Connector ثبت‌شده‌ای وجود ندارد.</p>'; } catch (error) { ui.connectorList.innerHTML = `<p class="ops-error">${escapeHtml(error.message)}</p>`; } }
async function probeConnector(ref, button) { button.disabled = true; try { const data = await api.probePasarGuard(ref); setBanner(`${ref}: healthy · ${data.latency_ms} ms`); } catch (error) { setBanner(error.message, true); } finally { button.disabled = false; } }
async function runDiagnostic(event) { event.preventDefault(); if (!requireSession()) return; ui.diagnosticResult.textContent = 'در حال بررسی…'; try { ui.diagnosticResult.textContent = safeJson(await api.diagnosePasarGuardService(ui.diagnosticServiceId.value.trim())); } catch (error) { ui.diagnosticResult.textContent = error.message; } }

function sourceCard(item) { const unhealthy = item.last_error_code || item.open_conflicts > 0 || item.failed_events > 0; return `<div class="ops-row"><div><strong>${escapeHtml(item.source_key)}</strong><small>${item.enabled ? 'فعال' : 'غیرفعال'} · checkpoint ${item.checkpoint_present ? '✓' : '—'}</small></div><div class="ops-tags"><span class="${unhealthy ? 'warn' : ''}">conflict ${Number(item.open_conflicts ?? 0)}</span><span>failed ${Number(item.failed_events ?? 0)}</span><span>pending ${Number(item.pending_events ?? 0)}</span></div></div>`; }
function conflictCard(item) { return `<div class="ops-row"><div><strong>${escapeHtml(item.conflict_code)}</strong><small>${escapeHtml(item.source_key)} · ${escapeHtml(item.entity_type)} · ${escapeHtml(String(item.external_entity_digest ?? '').slice(0, 12))}</small></div><span class="status-pill ${item.status === 'open' ? 'maintenance' : ''}">${escapeHtml(item.status)}</span></div>`; }
async function loadBotSync() { if (!requireSession()) return; ui.sourceList.innerHTML = ui.conflictList.innerHTML = '<p class="ops-copy">در حال دریافت…</p>'; try { const [sources, conflicts] = await Promise.all([api.listReconciliationSources(), api.listReconciliationConflicts({ status: 'open', limit: 100 })]); ui.sourceList.innerHTML = sources.length ? sources.map(sourceCard).join('') : '<p class="ops-copy">Source ثبت‌شده‌ای وجود ندارد.</p>'; ui.conflictList.innerHTML = conflicts.length ? conflicts.map(conflictCard).join('') : '<p class="ops-copy">Conflict بازی وجود ندارد.</p>'; ui.conflictCount.textContent = String(conflicts.length); } catch (error) { ui.sourceList.innerHTML = `<p class="ops-error">${escapeHtml(error.message)}</p>`; ui.conflictList.innerHTML = ''; } }
async function loadAccount(event) { event.preventDefault(); if (!requireSession()) return; ui.accountResult.textContent = 'در حال دریافت…'; try { ui.accountResult.textContent = safeJson(await api.getSharedAccount(ui.accountUserId.value.trim())); } catch (error) { ui.accountResult.textContent = error.message; } }

async function refreshCurrentView() { if (state.view === 'servers') return refreshServers(); if (state.view === 'pasarguard') return loadConnectors(); if (state.view === 'bot-sync') return loadBotSync(); }
function switchView(view) { if (!viewMeta[view]) return; state.view = view; document.querySelectorAll('[data-console-view]').forEach((item) => item.classList.toggle('hidden', item.dataset.consoleView !== view)); document.querySelectorAll('[data-view]').forEach((item) => item.classList.toggle('active', item.dataset.view === view)); ui.pageTitle.textContent = viewMeta[view][0]; ui.pageSubtitle.textContent = viewMeta[view][1]; ui.openCreate.classList.toggle('hidden', view !== 'servers'); setBanner(''); refreshCurrentView(); }

ui.sessionForm.addEventListener('submit', (event) => { event.preventDefault(); try { tokenStore.set(ui.tokenInput.value.trim()); ui.tokenInput.value = ''; ui.sessionDialog.close(); refreshCurrentView(); } catch (error) { setBanner(error.message, true); } });
ui.serverForm.addEventListener('submit', (event) => { event.preventDefault(); saveServer(); });
ui.serverDialog.addEventListener('cancel', () => clearSensitiveInput(ui.config)); ui.serverDialog.addEventListener('close', () => clearSensitiveInput(ui.config)); ui.tier.addEventListener('change', setCreateMode);
ui.refresh.addEventListener('click', refreshCurrentView); ui.openCreate.addEventListener('click', openCreate); ui.search.addEventListener('input', () => { state.query = ui.search.value; render(); });
document.querySelector('.filters').addEventListener('click', (event) => { const chip = event.target.closest('[data-tier]'); if (!chip) return; state.tier = chip.dataset.tier; document.querySelectorAll('[data-tier]').forEach((item) => item.classList.toggle('active', item === chip)); render(); });
document.querySelector('.nav').addEventListener('click', (event) => { const item = event.target.closest('[data-view]'); if (item) switchView(item.dataset.view); });
ui.grid.addEventListener('click', (event) => { const card = event.target.closest('[data-server-id]'), action = event.target.closest('[data-action]'); if (!card || !action) return; const server = state.servers.find((item) => item.id === card.dataset.serverId); if (!server) return; if (action.dataset.action === 'edit') openEdit(server); if (action.dataset.action === 'toggle') toggleServer(server, action.dataset.nextStatus); });
ui.connectorList.addEventListener('click', (event) => { const button = event.target.closest('[data-connector-health]'); if (button) probeConnector(button.dataset.connectorHealth, button); });
ui.reloadConnectors.addEventListener('click', loadConnectors); ui.diagnosticForm.addEventListener('submit', runDiagnostic); ui.reloadBotSync.addEventListener('click', loadBotSync); ui.accountForm.addEventListener('submit', loadAccount);
window.addEventListener('pagehide', () => { clearSensitiveInput(ui.config); tokenStore.clear(); }, { once: true });
refreshServers();
