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
  banner: document.querySelector('#supportBanner'),
  refresh: document.querySelector('#refreshSupport'),
  search: document.querySelector('#ticketSearch'),
  statusFilter: document.querySelector('#ticketStatusFilter'),
  list: document.querySelector('#ticketList'),
  empty: document.querySelector('#ticketEmpty'),
  placeholder: document.querySelector('#conversationPlaceholder'),
  content: document.querySelector('#conversationContent'),
  meta: document.querySelector('#ticketMeta'),
  subject: document.querySelector('#ticketSubject'),
  status: document.querySelector('#ticketStatus'),
  messages: document.querySelector('#messageList'),
  replyForm: document.querySelector('#replyForm'),
  replyBody: document.querySelector('#replyBody'),
  sendReply: document.querySelector('#sendReply'),
  sessionDialog: document.querySelector('#supportSessionDialog'),
  sessionForm: document.querySelector('#supportSessionForm'),
  tokenInput: document.querySelector('#supportTokenInput'),
};

const state = {
  tickets: [],
  selected: null,
  loading: false,
  mutationBusy: false,
  query: '',
  status: 'all',
  replyClientId: crypto.randomUUID(),
};

const statusLabels = Object.freeze({
  open: 'باز',
  waiting_user: 'منتظر کاربر',
  waiting_support: 'منتظر پشتیبانی',
  resolved: 'حل‌شده',
  closed: 'بسته',
});
const categoryLabels = Object.freeze({
  connection: 'اتصال', billing: 'پرداخت', account: 'حساب', security: 'امنیت', feedback: 'پیشنهاد', other: 'سایر',
});
const priorityLabels = Object.freeze({ urgent: 'فوری', high: 'زیاد', normal: 'عادی' });

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

function requireSession() {
  if (hostSession && typeof hostSession.accessToken === 'function') return true;
  if (tokenStore.get()) return true;
  ui.sessionDialog.showModal();
  return false;
}

function filteredTickets() {
  const query = state.query.trim().toLocaleLowerCase('fa');
  return state.tickets.filter((ticket) => {
    if (state.status !== 'all' && ticket.status !== state.status) return false;
    if (!query) return true;
    return [ticket.public_code, ticket.subject, categoryLabels[ticket.category], priorityLabels[ticket.priority]]
      .filter(Boolean)
      .some((value) => String(value).toLocaleLowerCase('fa').includes(query));
  });
}

function ticketCard(ticket) {
  const selected = state.selected?.id === ticket.id;
  return `<button type="button" class="ticket-card ${selected ? 'selected' : ''}" data-ticket-id="${escapeHtml(ticket.id)}">
    <span class="ticket-card-top"><strong>${escapeHtml(ticket.subject)}</strong><span class="support-status ${escapeHtml(ticket.status)}">${escapeHtml(statusLabels[ticket.status] ?? ticket.status)}</span></span>
    <span class="ticket-card-meta">${escapeHtml(ticket.public_code)} · ${escapeHtml(categoryLabels[ticket.category] ?? ticket.category)} · ${escapeHtml(priorityLabels[ticket.priority] ?? ticket.priority)}</span>
    <span class="ticket-card-time">${escapeHtml(ticket.updated_at)}</span>
  </button>`;
}

function renderTickets() {
  const tickets = filteredTickets();
  ui.list.innerHTML = tickets.map(ticketCard).join('');
  ui.empty.classList.toggle('hidden', tickets.length !== 0 || state.loading);
}

function messageCard(message) {
  const sender = message.sender_role === 'support' ? 'پشتیبانی گنج' : message.sender_role === 'user' ? 'کاربر' : 'سیستم';
  return `<article class="support-message ${escapeHtml(message.sender_role)}">
    <div><strong>${escapeHtml(sender)}</strong><time>${escapeHtml(message.created_at)}</time></div>
    <p>${escapeHtml(message.body)}</p>
  </article>`;
}

function renderConversation() {
  const detail = state.selected;
  ui.placeholder.classList.toggle('hidden', Boolean(detail));
  ui.content.classList.toggle('hidden', !detail);
  if (!detail) return;

  ui.meta.textContent = `${detail.public_code} · ${categoryLabels[detail.category] ?? detail.category} · ${priorityLabels[detail.priority] ?? detail.priority}`;
  ui.subject.textContent = detail.subject;
  ui.status.value = ['open'].includes(detail.status) ? 'waiting_support' : detail.status;
  ui.messages.innerHTML = (detail.messages ?? []).map(messageCard).join('');
  const closed = detail.status === 'closed';
  ui.replyBody.disabled = state.mutationBusy || closed;
  ui.sendReply.disabled = state.mutationBusy || closed || ui.replyBody.value.trim().length === 0;
  if (closed) ui.replyBody.placeholder = 'تیکت بسته است؛ ابتدا وضعیت را تغییر دهید.';
  else ui.replyBody.placeholder = 'پاسخ را بدون اطلاعات محرمانه بنویسید';
}

function render() {
  renderTickets();
  renderConversation();
  ui.refresh.disabled = state.loading || state.mutationBusy;
  ui.status.disabled = state.mutationBusy || !state.selected;
}

async function refreshTickets({ preserveSelection = true } = {}) {
  if (state.loading || !requireSession()) return;
  state.loading = true;
  setBanner('در حال دریافت تیکت‌ها…');
  render();
  try {
    state.tickets = await api.listSupportTickets();
    setBanner('');
    if (preserveSelection && state.selected) {
      const exists = state.tickets.some((ticket) => ticket.id === state.selected.id);
      if (exists) await openTicket(state.selected.id, { preserveDraft: true });
      else state.selected = null;
    }
  } catch (error) {
    if (error instanceof AdminApiError && error.status === 401 && !hostSession) {
      tokenStore.clear();
      ui.sessionDialog.showModal();
    }
    setBanner(error instanceof Error ? error.message : 'دریافت تیکت‌ها ناموفق بود.', true);
  } finally {
    state.loading = false;
    render();
  }
}

async function openTicket(ticketId, { preserveDraft = false } = {}) {
  if (!requireSession() || state.mutationBusy) return;
  if (!preserveDraft || state.selected?.id !== ticketId) {
    ui.replyBody.value = '';
    state.replyClientId = crypto.randomUUID();
  }
  setBanner('در حال دریافت گفت‌وگو…');
  try {
    state.selected = await api.getSupportTicket(ticketId);
    setBanner('');
  } catch (error) {
    setBanner(error instanceof Error ? error.message : 'دریافت گفت‌وگو ناموفق بود.', true);
  }
  render();
}

async function sendReply() {
  if (!state.selected || state.mutationBusy) return;
  const body = ui.replyBody.value.trim();
  if (!body) return;
  const clientMessageId = state.replyClientId;
  state.mutationBusy = true;
  setBanner('در حال ثبت پاسخ…');
  render();
  try {
    await api.replySupportTicket(state.selected.id, clientMessageId, body);
    ui.replyBody.value = '';
    state.replyClientId = crypto.randomUUID();
    state.selected = await api.getSupportTicket(state.selected.id);
    const index = state.tickets.findIndex((ticket) => ticket.id === state.selected.id);
    if (index >= 0) state.tickets[index] = { ...state.tickets[index], ...state.selected };
    setBanner('پاسخ پشتیبانی ثبت شد.');
  } catch (error) {
    setBanner(error instanceof Error ? error.message : 'ارسال پاسخ ناموفق بود.', true);
  } finally {
    state.mutationBusy = false;
    render();
  }
}

async function changeStatus(status) {
  if (!state.selected || state.mutationBusy || state.selected.status === status) return;
  state.mutationBusy = true;
  setBanner('در حال تغییر وضعیت تیکت…');
  render();
  try {
    const updated = await api.updateSupportTicketStatus(state.selected.id, status);
    state.selected = { ...state.selected, ...updated };
    const index = state.tickets.findIndex((ticket) => ticket.id === updated.id);
    if (index >= 0) state.tickets[index] = { ...state.tickets[index], ...updated };
    setBanner(`وضعیت تیکت روی «${statusLabels[updated.status] ?? updated.status}» قرار گرفت.`);
  } catch (error) {
    setBanner(error instanceof Error ? error.message : 'تغییر وضعیت ناموفق بود.', true);
  } finally {
    state.mutationBusy = false;
    render();
  }
}

ui.sessionForm.addEventListener('submit', (event) => {
  event.preventDefault();
  try {
    tokenStore.set(ui.tokenInput.value.trim());
    ui.tokenInput.value = '';
    ui.sessionDialog.close();
    refreshTickets({ preserveSelection: false });
  } catch (error) {
    setBanner(error.message, true);
  }
});
ui.refresh.addEventListener('click', () => refreshTickets());
ui.search.addEventListener('input', () => { state.query = ui.search.value; renderTickets(); });
ui.statusFilter.addEventListener('change', () => { state.status = ui.statusFilter.value; renderTickets(); });
ui.list.addEventListener('click', (event) => {
  const ticket = event.target.closest('[data-ticket-id]');
  if (ticket) openTicket(ticket.dataset.ticketId);
});
ui.replyForm.addEventListener('submit', (event) => { event.preventDefault(); sendReply(); });
ui.replyBody.addEventListener('input', renderConversation);
ui.status.addEventListener('change', () => changeStatus(ui.status.value));
window.addEventListener('pagehide', () => tokenStore.clear(), { once: true });

refreshTickets({ preserveSelection: false });
