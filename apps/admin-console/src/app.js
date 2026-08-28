import { AdminApiClient, AdminApiError, createMemoryTokenProvider } from './api.js';

const tokenStore = createMemoryTokenProvider();
const hostSession = window.__GANJ_ADMIN_SESSION__;
if (hostSession && typeof hostSession.accessToken === 'function') {
  tokenStore.provider = async () => hostSession.accessToken();
}

const api = new AdminApiClient({
  baseUrl: window.__GANJ_ADMIN_CONFIG__?.apiBaseUrl ?? window.location.origin,
  accessTokenProvider: tokenStore.provider,
});

const ui = {
  grid: document.querySelector('#serverGrid'),
  empty: document.querySelector('#emptyState'),
  banner: document.querySelector('#statusBanner'),
  search: document.querySelector('#searchInput'),
  refresh: document.querySelector('#refreshButton'),
  openCreate: document.querySelector('#openCreateButton'),
  sessionDialog: document.querySelector('#sessionDialog'),
  sessionForm: document.querySelector('#sessionForm'),
  tokenInput: document.querySelector('#tokenInput'),
  serverDialog: document.querySelector('#serverDialog'),
  serverForm: document.querySelector('#serverForm'),
  title: document.querySelector('#serverDialogTitle'),
  id: document.querySelector('#serverId'),
  code: document.querySelector('#serverCode'),
  name: document.querySelector('#serverName'),
  country: document.querySelector('#serverCountry'),
  city: document.querySelector('#serverCity'),
  tier: document.querySelector('#serverTier'),
  status: document.querySelector('#serverStatus'),
  load: document.querySelector('#serverLoad'),
  latency: document.querySelector('#serverLatency'),
  secret: document.querySelector('#serverSecretReference'),
  secretField: document.querySelector('#secretReferenceField'),
  metrics: {
    total: document.querySelector('#metricTotal'),
    active: document.querySelector('#metricActive'),
    free: document.querySelector('#metricFree'),
    attention: document.querySelector('#metricAttention'),
  },
};

const state = { servers: [], tier: 'all', query: '', loading: false };
const statusLabels = Object.freeze({ active: 'فعال', busy: 'شلوغ', maintenance: 'نگهداری', disabled: 'غیرفعال' });
const tierLabels = Object.freeze({ free: 'FREE', premium: 'PREMIUM', vip: 'VIP' });

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>'"]/g, (char) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;',
  })[char]);
}

function setBanner(message, error = false) {
  ui.banner.textContent = message;
  ui.banner.classList.toggle('error', error);
  ui.banner.classList.toggle('hidden', !message);
}

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
    return [server.name, server.code, server.country_code, server.city]
      .filter(Boolean).some((value) => String(value).toLocaleLowerCase('fa').includes(query));
  });
}

function serverCard(server) {
  const load = `${Math.round(Number(server.load_ratio ?? 0) * 100)}%`;
  const latency = server.latency_hint_ms == null ? '—' : `${server.latency_hint_ms} ms`;
  const protocols = server.protocols.map((item) => `<span>${escapeHtml(item.toUpperCase())}</span>`).join('');
  const nextStatus = server.status === 'disabled' ? 'maintenance' : 'disabled';
  const toggleLabel = server.status === 'disabled' ? 'برگرداندن به نگهداری' : 'غیرفعال فوری';
  return `<article class="server-card glass" data-server-id="${escapeHtml(server.id)}">
    <div class="server-head">
      <div class="flag">${escapeHtml(server.country_code)}</div>
      <div class="server-title"><h3>${escapeHtml(server.name)}</h3><p>${escapeHtml(server.code)} · ${escapeHtml(server.city || server.country_code)}</p></div>
      <span class="status-pill ${escapeHtml(server.status)}">${escapeHtml(statusLabels[server.status] ?? server.status)}</span>
    </div>
    <div class="server-meta">
      <div><span>Tier</span><strong>${escapeHtml(tierLabels[server.tier] ?? server.tier)}</strong></div>
      <div><span>Load</span><strong>${escapeHtml(load)}</strong></div>
      <div><span>Latency</span><strong>${escapeHtml(latency)}</strong></div>
    </div>
    <div class="protocol-list">${protocols}</div>
    <div class="card-actions">
      <span class="secret-state">${server.secret_configured ? '● Secret متصل' : '○ Secret نامشخص'}</span>
      <div><button type="button" data-action="toggle" data-next-status="${nextStatus}">${toggleLabel}</button><button type="button" data-action="edit">ویرایش</button></div>
    </div>
  </article>`;
}

function render() {
  renderMetrics();
  const servers = filteredServers();
  ui.grid.innerHTML = servers.map(serverCard).join('');
  ui.empty.classList.toggle('hidden', servers.length !== 0 || state.loading);
}

function requireSession() {
  if (hostSession && typeof hostSession.accessToken === 'function') return true;
  if (tokenStore.get()) return true;
  ui.sessionDialog.showModal();
  return false;
}

async function refresh() {
  if (state.loading || !requireSession()) return;
  state.loading = true;
  ui.refresh.disabled = true;
  setBanner('در حال دریافت وضعیت Control Plane…');
  try {
    state.servers = await api.listServers();
    setBanner('');
  } catch (error) {
    if (error instanceof AdminApiError && error.status === 401 && !hostSession) {
      tokenStore.clear();
      ui.sessionDialog.showModal();
    }
    setBanner(error instanceof Error ? error.message : 'دریافت اطلاعات ناموفق بود.', true);
  } finally {
    state.loading = false;
    ui.refresh.disabled = false;
    render();
  }
}

function resetForm() {
  ui.serverForm.reset();
  ui.id.value = '';
  ui.code.disabled = false;
  ui.secretField.classList.remove('hidden');
  ui.status.value = 'maintenance';
  ui.load.value = '0';
  document.querySelector('input[name="protocol"][value="vless"]').checked = true;
}

function openCreate() {
  if (!requireSession()) return;
  resetForm();
  ui.title.textContent = 'سرور جدید';
  ui.serverDialog.showModal();
}

function openEdit(server) {
  resetForm();
  ui.title.textContent = 'ویرایش سرور';
  ui.id.value = server.id;
  ui.code.value = server.code;
  ui.code.disabled = true;
  ui.name.value = server.name;
  ui.country.value = server.country_code;
  ui.city.value = server.city ?? '';
  ui.tier.value = server.tier;
  ui.status.value = server.status;
  ui.load.value = String(server.load_ratio ?? 0);
  ui.latency.value = server.latency_hint_ms ?? '';
  ui.secretField.classList.add('hidden');
  for (const checkbox of document.querySelectorAll('input[name="protocol"]')) checkbox.checked = server.protocols.includes(checkbox.value);
  ui.serverDialog.showModal();
}

function formPayload() {
  const protocols = [...document.querySelectorAll('input[name="protocol"]:checked')].map((item) => item.value);
  return {
    name: ui.name.value.trim(),
    country_code: ui.country.value.trim().toUpperCase(),
    city: ui.city.value.trim() || null,
    tier: ui.tier.value,
    status: ui.status.value,
    load_ratio: Number(ui.load.value),
    latency_hint_ms: ui.latency.value === '' ? null : Number(ui.latency.value),
    protocols,
  };
}

async function saveServer() {
  const id = ui.id.value;
  const payload = formPayload();
  if (!id) {
    payload.code = ui.code.value.trim();
    payload.secret_reference = ui.secret.value.trim();
  }
  try {
    const saved = id ? await api.updateServer(id, payload) : await api.createServer(payload);
    const index = state.servers.findIndex((item) => item.id === saved.id);
    if (index >= 0) state.servers[index] = saved; else state.servers.unshift(saved);
    ui.serverDialog.close();
    setBanner(id ? 'تغییرات سرور ذخیره شد.' : 'سرور جدید در Registry ثبت شد.');
    render();
  } catch (error) {
    setBanner(error instanceof Error ? error.message : 'ذخیره سرور ناموفق بود.', true);
  }
}

async function toggleServer(server, status) {
  try {
    const saved = await api.updateServer(server.id, { status, ...(status === 'disabled' ? { load_ratio: 0 } : {}) });
    state.servers[state.servers.findIndex((item) => item.id === saved.id)] = saved;
    setBanner(status === 'disabled' ? 'سرور فوراً از دسترس کاربران خارج شد.' : 'سرور به حالت نگهداری برگشت.');
    render();
  } catch (error) {
    setBanner(error instanceof Error ? error.message : 'تغییر وضعیت ناموفق بود.', true);
  }
}

ui.sessionForm.addEventListener('submit', (event) => {
  event.preventDefault();
  try {
    tokenStore.set(ui.tokenInput.value.trim());
    ui.tokenInput.value = '';
    ui.sessionDialog.close();
    refresh();
  } catch (error) {
    setBanner(error.message, true);
  }
});
ui.serverForm.addEventListener('submit', (event) => { event.preventDefault(); saveServer(); });
ui.refresh.addEventListener('click', refresh);
ui.openCreate.addEventListener('click', openCreate);
ui.search.addEventListener('input', () => { state.query = ui.search.value; render(); });
document.querySelector('.filters').addEventListener('click', (event) => {
  const chip = event.target.closest('[data-tier]');
  if (!chip) return;
  state.tier = chip.dataset.tier;
  document.querySelectorAll('[data-tier]').forEach((item) => item.classList.toggle('active', item === chip));
  render();
});
ui.grid.addEventListener('click', (event) => {
  const card = event.target.closest('[data-server-id]');
  const action = event.target.closest('[data-action]');
  if (!card || !action) return;
  const server = state.servers.find((item) => item.id === card.dataset.serverId);
  if (!server) return;
  if (action.dataset.action === 'edit') openEdit(server);
  if (action.dataset.action === 'toggle') toggleServer(server, action.dataset.nextStatus);
});

window.addEventListener('pagehide', () => tokenStore.clear(), { once: true });
refresh();
