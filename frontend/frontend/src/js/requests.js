// Модуль интерфейса заявок на посещение.
// Встроен в существующую панель: тот же api.js, те же тосты, та же навигация.
import { Api } from './api.js';
import { showToast, escapeHtml } from './utils.js';

const STATUS_META = {
  DRAFT: { label: 'Черновик', cls: 'vr-pill-draft' },
  SUBMITTED: { label: 'На согласовании', cls: 'vr-pill-wait' },
  APPROVED: { label: 'Одобрена', cls: 'vr-pill-ok' },
  REJECTED: { label: 'Отклонена', cls: 'vr-pill-bad' },
  CANCELLED: { label: 'Отменена', cls: 'vr-pill-muted' }
};

const ACTION_LABELS = {
  CREATE: 'Создана',
  UPDATE: 'Изменена',
  SUBMIT: 'Отправлена на согласование',
  APPROVE: 'Одобрена',
  REJECT: 'Отклонена',
  CANCEL: 'Отменена',
  ARCHIVE: 'Сотрудник в архиве'
};

const state = {
  bound: false,
  user: null,
  tab: 'mine',
  search: '',
  status: 'ALL',
  date: '',
  hostsLoaded: false,
  rejectId: null,
  editingId: null,
  cache: { mine: [], queue: [], expected: [], inbox: [] }
};

const dateTimeFormat = new Intl.DateTimeFormat('ru-RU', {
  day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit'
});

const $ = (id) => document.getElementById(id);

function todayIso() {
  const now = new Date();
  const pad = (value) => String(value).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

function formatMoment(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : dateTimeFormat.format(date);
}

function formatRange(request) {
  return `${formatMoment(request.plannedFrom)} — ${formatMoment(request.plannedTo)}`;
}

function visitorName(request) {
  return request.visitorFullName
    || [request.visitorLastName, request.visitorFirstName, request.visitorMiddleName]
      .filter(Boolean).join(' ');
}

function statusPill(status) {
  const meta = STATUS_META[status] || { label: status || '—', cls: 'vr-pill-muted' };
  return `<span class="vr-pill ${meta.cls}">${escapeHtml(meta.label)}</span>`;
}

function statusLabel(status) {
  return STATUS_META[status]?.label || status || '—';
}

function actionLabel(action) {
  return ACTION_LABELS[action] || action || '—';
}

function emptyState(title, hint = '') {
  return `
    <div class="vr-empty">
      <svg class="w-8 h-8 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.6" d="M9 12h6m-6 4h4M7 4h10a2 2 0 012 2v14l-3.5-2-3.5 2-3.5-2L5 20V6a2 2 0 012-2z"/>
      </svg>
      <p class="font-medium text-slate-500">${escapeHtml(title)}</p>
      ${hint ? `<p class="text-xs text-slate-400">${escapeHtml(hint)}</p>` : ''}
    </div>`;
}

function skeletons(count = 3) {
  return Array.from({ length: count }, () => '<div class="vr-skeleton"></div>').join('');
}

function setHtml(id, html) {
  const node = $(id);
  if (node) node.innerHTML = html;
}

// ------------------------------------------------------------------ инициализация

export async function initVisitRequests() {
  bindOnce();
  await loadUser();
  applyRoleVisibility();
  await loadTab(state.tab);
}

export async function refreshRequestBadges() {
  await loadUser();
  try {
    const unread = await Api.notificationsUnread();
    let queueCount = 0;
    if (canApprove()) {
      state.cache.queue = await Api.visitRequestsQueue();
      queueCount = state.cache.queue.length;
    }
    paintBadges(Number(unread?.unread || 0), queueCount);

    if (canSeeExpected()) {
      state.cache.expected = await Api.visitRequestsExpected(state.date || todayIso());
      const stat = $('vr-stat-expected');
      if (stat) stat.textContent = state.cache.expected.length;
    }
  } catch (_) {
    // Бейджи — вспомогательная часть, ошибку не показываем
  }
}

function canApprove() {
  const roleId = Number(state.user?.roleId ?? 0);
  return Boolean(state.user?.isAdmin) || roleId === 1 || roleId === 2;
}

function canSeeExpected() {
  const roleId = Number(state.user?.roleId ?? 0);
  return Boolean(state.user?.isAdmin) || roleId === 1 || roleId === 2 || roleId === 3;
}

function isAdmin() {
  return Boolean(state.user?.isAdmin) || Number(state.user?.roleId ?? 0) === 1;
}

async function loadUser() {
  if (state.user) return state.user;
  try {
    state.user = await Api.getCurrentUser();
  } catch (_) {
    state.user = { roleId: 0, isAdmin: false };
  }
  return state.user;
}

function applyRoleVisibility() {
  const tabs = {
    all: canApprove(),
    queue: canApprove(),
    expected: canSeeExpected(),
    audit: isAdmin()
  };

  Object.entries(tabs).forEach(([tab, allowed]) => {
    const button = document.querySelector(`[data-vr-tab="${tab}"]`);
    if (button) button.classList.toggle('hidden', !allowed);
    if (!allowed && state.tab === tab) state.tab = 'mine';
  });

  activateTab(state.tab);
}

function bindOnce() {
  if (state.bound) return;
  state.bound = true;

  document.querySelectorAll('[data-vr-tab]').forEach(button => {
    button.addEventListener('click', () => {
      const tab = button.dataset.vrTab;
      if (button.classList.contains('hidden')) return;
      activateTab(tab);
      loadTab(tab);
    });
  });

  $('vr-new')?.addEventListener('click', () => openCreateModal());
  $('vr-refresh')?.addEventListener('click', () => loadTab(state.tab, true));

  let searchTimer = null;
  $('vr-search')?.addEventListener('input', (event) => {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
      state.search = event.target.value.trim().toLowerCase();
      renderMine();
    }, 200);
  });

  $('vr-status')?.addEventListener('change', (event) => {
    state.status = event.target.value;
    renderMine();
  });

  const dateInput = $('vr-date');
  if (dateInput) {
    dateInput.value = state.date = todayIso();
    dateInput.addEventListener('change', () => {
      state.date = dateInput.value;
      loadExpected();
    });
  }

  $('vr-read-all')?.addEventListener('click', markAllRead);
  $('vr-audit-type')?.addEventListener('change', loadAudit);
  $('vr-audit-actor')?.addEventListener('change', loadAudit);

  $('vr-create-form')?.addEventListener('submit', submitCreateForm);
  $('vr-reject-confirm')?.addEventListener('click', confirmReject);

  $('vr-all-status')?.addEventListener('change', loadAll);

  document.querySelectorAll('[data-vr-close]').forEach(button => {
    button.addEventListener('click', () => closeModal(button.dataset.vrClose));
  });

  document.querySelectorAll('.modal-overlay-base').forEach(overlay => {
    overlay.addEventListener('click', (event) => {
      if (event.target === overlay) closeModal(overlay.id);
    });
  });

  document.addEventListener('keydown', (event) => {
    if (event.key !== 'Escape') return;
    document.querySelectorAll('.modal-overlay-base:not(.hidden)').forEach(overlay => closeModal(overlay.id));
  });

  const root = $('requests');
  root?.addEventListener('click', handleRootClick);

  $('vr-bell')?.addEventListener('click', () => {
    if (typeof window.navigateTo === 'function') window.navigateTo('requests');
    activateTab('inbox');
    loadTab('inbox');
  });
}

function activateTab(tab) {
  state.tab = tab;
  document.querySelectorAll('[data-vr-tab]').forEach(button => {
    const active = button.dataset.vrTab === tab;
    button.classList.toggle('active', active);
    button.setAttribute('aria-selected', active ? 'true' : 'false');
  });
  document.querySelectorAll('.vr-panel').forEach(panel => {
    panel.classList.toggle('active', panel.id === `vr-panel-${tab}`);
  });
}

async function loadTab(tab, force = false) {
  if (!force && tab === 'mine' && state.cache.mine.length) {
    renderMine();
    return;
  }
  if (tab === 'mine') return loadMine();
  if (tab === 'all') return loadAll();
  if (tab === 'queue') return loadQueue();
  if (tab === 'expected') return loadExpected();
  if (tab === 'inbox') return loadInbox();
  if (tab === 'audit') return loadAudit();
  return undefined;
}

// ------------------------------------------------------------------ данные

async function loadMine() {
  setHtml('vr-mine-list', skeletons(3));
  try {
    state.cache.mine = await Api.visitRequestsMine();
    renderMine();
  } catch (error) {
    setHtml('vr-mine-list', emptyState('Не удалось загрузить заявки', error.message));
  }
}

async function loadQueue() {
  setHtml('vr-queue-list', skeletons(3));
  try {
    state.cache.queue = await Api.visitRequestsQueue();
    renderQueue();
    paintBadges(undefined, state.cache.queue.length);
  } catch (error) {
    setHtml('vr-queue-list', emptyState('Не удалось загрузить очередь', error.message));
  }
}

async function loadExpected() {
  setHtml('vr-expected-list', skeletons(2));
  try {
    state.cache.expected = await Api.visitRequestsExpected(state.date);
    renderExpected();
  } catch (error) {
    setHtml('vr-expected-list', emptyState('Не удалось загрузить список', error.message));
  }
}

async function loadInbox() {
  setHtml('vr-inbox-list', skeletons(3));
  try {
    state.cache.inbox = await Api.notifications();
    renderInbox();
    updateUnreadStats();
  } catch (error) {
    setHtml('vr-inbox-list', emptyState('Не удалось загрузить уведомления', error.message));
  }
}

async function loadAudit() {
  setHtml('vr-audit-list', skeletons(3));
  const params = {
    entityType: $('vr-audit-type')?.value || '',
    actor: $('vr-audit-actor')?.value?.trim() || '',
    size: 50
  };
  try {
    const page = await Api.auditLog(params);
    renderAudit(Array.isArray(page?.content) ? page.content : []);
  } catch (error) {
    setHtml('vr-audit-list', emptyState('Журнал недоступен', error.message));
  }
}

// ------------------------------------------------------------------ отрисовка

function renderMine() {
  const list = state.cache.mine.filter(request => {
    const statusOk = state.status === 'ALL' || request.status === state.status;
    if (!statusOk) return false;
    if (!state.search) return true;
    const haystack = `${visitorName(request)} ${request.organization || ''} ${request.purpose || ''}`.toLowerCase();
    return haystack.includes(state.search);
  });

  $('vr-stat-mine').textContent = state.cache.mine.length;

  if (!list.length) {
    setHtml('vr-mine-list', emptyState(
      state.cache.mine.length ? 'Ничего не найдено' : 'Заявок пока нет',
      state.cache.mine.length ? 'Измените фильтр или поисковый запрос' : 'Создайте первую заявку — кнопка справа сверху'));
    return;
  }

  setHtml('vr-mine-list', list.map(request => {
    const canSubmit = request.status === 'DRAFT';
    const canCancel = ['DRAFT', 'SUBMITTED', 'APPROVED'].includes(request.status);
    return `
      <article class="vr-card vr-fade">
        <div class="vr-card-main">
          <div class="vr-card-head">
            <span class="vr-id">№${request.id}</span>
            ${statusPill(request.status)}
            ${request.decisionComment ? `<span class="text-xs text-slate-400">${escapeHtml(request.decisionComment)}</span>` : ''}
          </div>
          <h4 class="vr-visitor">${escapeHtml(visitorName(request))}</h4>
          <p class="vr-meta">${escapeHtml(request.organization || 'Без организации')} · ${escapeHtml(request.phone || 'телефон не указан')}</p>
          <p class="vr-meta">${escapeHtml(formatRange(request))}</p>
          ${request.purpose ? `<p class="vr-purpose">${escapeHtml(request.purpose)}</p>` : ''}
        </div>
        <div class="vr-card-side">
          <button type="button" class="vr-btn vr-btn-ghost" data-vr-action="details" data-id="${request.id}">История</button>
          ${canSubmit ? `<button type="button" class="vr-btn" data-vr-action="edit" data-id="${request.id}">Изменить</button>` : ''}
          ${canSubmit ? `<button type="button" class="vr-btn" data-vr-action="submit" data-id="${request.id}">Отправить</button>` : ''}
          ${canCancel ? `<button type="button" class="vr-btn vr-btn-danger" data-vr-action="cancel" data-id="${request.id}">Отменить</button>` : ''}
        </div>
      </article>`;
  }).join(''));
}

function renderQueue() {
  const list = state.cache.queue;
  if (!list.length) {
    setHtml('vr-queue-list', emptyState('Очередь пуста', 'Отправленные заявки появятся здесь'));
    return;
  }

  setHtml('vr-queue-list', list.map(request => `
    <article class="vr-card vr-fade">
      <div class="vr-card-main">
        <div class="vr-card-head">
          <span class="vr-id">№${request.id}</span>
          ${statusPill(request.status)}
        </div>
        <h4 class="vr-visitor">${escapeHtml(visitorName(request))}</h4>
        <p class="vr-meta">${escapeHtml(request.organization || 'Без организации')} · автор: ${escapeHtml(request.authorEmail || '—')}</p>
        <p class="vr-meta">${escapeHtml(formatRange(request))}</p>
        ${request.purpose ? `<p class="vr-purpose">${escapeHtml(request.purpose)}</p>` : ''}
      </div>
      <div class="vr-card-side">
        <button type="button" class="vr-btn vr-btn-ghost" data-vr-action="details" data-id="${request.id}">История</button>
        <button type="button" class="vr-btn vr-btn-primary" data-vr-action="approve" data-id="${request.id}">Одобрить</button>
        <button type="button" class="vr-btn vr-btn-danger" data-vr-action="reject" data-id="${request.id}">Отклонить</button>
      </div>
    </article>`).join(''));
}

function renderExpected() {
  const list = state.cache.expected;
  $('vr-stat-expected').textContent = list.length;

  if (!list.length) {
    setHtml('vr-expected-list', emptyState('На эту дату никто не ожидается', 'Показаны только одобренные заявки'));
    return;
  }

  setHtml('vr-expected-list', list.map(request => {
    const visitStatus = request.visitStatus;
    const visitPill = visitStatus === 'ACTIVE'
      ? '<span class="vr-pill vr-pill-warn">На территории</span>'
      : (visitStatus === 'FINISHED'
        ? '<span class="vr-pill vr-pill-muted">Ушёл</span>'
        : '<span class="vr-pill vr-pill-ok">Ожидается</span>');

    const visitAction = visitStatus === 'PLANNED'
      ? `<button type="button" class="vr-btn vr-btn-primary" data-vr-action="visit-start" data-visit="${request.visitId}">Гость пришёл</button>`
      : (visitStatus === 'ACTIVE'
        ? `<button type="button" class="vr-btn" data-vr-action="visit-finish" data-visit="${request.visitId}">Гость ушёл</button>`
        : '');

    return `
    <article class="vr-card vr-fade">
      <div class="vr-card-main">
        <div class="vr-card-head">
          <span class="vr-id">№${request.id}</span>
          ${visitPill}
          <span class="text-xs text-slate-400">${escapeHtml(formatMoment(request.plannedFrom))}</span>
        </div>
        <h4 class="vr-visitor">${escapeHtml(visitorName(request))}</h4>
        <p class="vr-meta">${escapeHtml(request.organization || 'Без организации')} · хост: ${escapeHtml(request.host?.fullName || '—')}</p>
        ${request.purpose ? `<p class="vr-purpose">${escapeHtml(request.purpose)}</p>` : ''}
      </div>
      <div class="vr-card-side">
        ${visitAction}
        <button type="button" class="vr-btn vr-btn-ghost" data-vr-action="details" data-id="${request.id}">История</button>
      </div>
    </article>`;
  }).join(''));
}

function renderInbox() {
  const list = state.cache.inbox;
  if (!list.length) {
    setHtml('vr-inbox-list', emptyState('Уведомлений нет', 'Здесь появятся решения по заявкам'));
    return;
  }

  setHtml('vr-inbox-list', list.map(item => `
    <article class="vr-card vr-fade">
      <div class="vr-card-main">
        <div class="vr-card-head">
          ${item.read ? '' : '<span class="vr-pill vr-pill-wait">Новое</span>'}
          <span class="text-xs text-slate-400">${escapeHtml(formatMoment(item.createdAt))}</span>
        </div>
        <h4 class="vr-visitor">${escapeHtml(item.title)}</h4>
        ${item.body ? `<p class="vr-purpose">${escapeHtml(item.body)}</p>` : ''}
      </div>
      <div class="vr-card-side">
        ${item.read ? '' : `<button type="button" class="vr-btn" data-vr-action="read" data-id="${item.id}">Прочитано</button>`}
      </div>
    </article>`).join(''));
}

function renderAudit(rows) {
  if (!rows.length) {
    setHtml('vr-audit-list', emptyState('Записей нет', 'Попробуйте изменить фильтры'));
    return;
  }

  const body = rows.map(row => {
    const before = row.beforeJson ? `<details><summary class="text-xs text-indigo-600 cursor-pointer">Было</summary><pre class="text-xs whitespace-pre-wrap mt-1 text-slate-500">${escapeHtml(prettyJson(row.beforeJson))}</pre></details>` : '';
    const after = row.afterJson ? `<details><summary class="text-xs text-indigo-600 cursor-pointer">Стало</summary><pre class="text-xs whitespace-pre-wrap mt-1 text-slate-500">${escapeHtml(prettyJson(row.afterJson))}</pre></details>` : '';
    return `
      <tr>
        <td>${escapeHtml(formatMoment(row.createdAt))}</td>
        <td>${escapeHtml(row.entityType)} №${escapeHtml(row.entityId)}</td>
        <td>${escapeHtml(actionLabel(row.action))}</td>
        <td>${escapeHtml(row.actorEmail || '—')}</td>
        <td>${escapeHtml(row.reason || '')}${before}${after}</td>
      </tr>`;
  }).join('');

  setHtml('vr-audit-list', `
    <div class="vr-card">
      <div class="w-full overflow-x-auto">
        <table class="vr-report-table">
          <thead><tr><th>Когда</th><th>Объект</th><th>Действие</th><th>Автор</th><th>Детали</th></tr></thead>
          <tbody>${body}</tbody>
        </table>
      </div>
    </div>`);
}

function prettyJson(raw) {
  if (!raw) return '';
  try {
    return JSON.stringify(JSON.parse(raw), null, 1);
  } catch (_) {
    return raw;
  }
}

// ------------------------------------------------------------------ действия

async function handleRootClick(event) {
  const button = event.target.closest('[data-vr-action]');
  if (!button) return;

  const id = button.dataset.id;
  const action = button.dataset.vrAction;
  button.disabled = true;

  try {
    if (action === 'details') {
      await openDetailsModal(id);
    } else if (action === 'edit') {
      const request = state.cache.mine.find(item => String(item.id) === String(id));
      if (request) await openCreateModal(request);
    } else if (action === 'submit') {
      await Api.visitRequestSubmit(id);
      showToast('Заявка отправлена на согласование', 'success');
      await loadMine();
    } else if (action === 'cancel') {
      if (!window.confirm('Отменить заявку?')) return;
      await Api.visitRequestCancel(id);
      showToast('Заявка отменена', 'success');
      await loadMine();
    } else if (action === 'approve') {
      await Api.visitRequestApprove(id, 'Согласовано');
      showToast('Заявка одобрена, гость добавлен в ожидаемые', 'success');
      await loadQueue();
      await refreshRequestBadges();
    } else if (action === 'reject') {
      state.rejectId = id;
      $('vr-reject-comment').value = '';
      openModal('vr-reject-modal');
      setTimeout(() => $('vr-reject-comment')?.focus(), 50);
    } else if (action === 'read') {
      await Api.notificationRead(id);
      await loadInbox();
    } else if (action === 'visit-start') {
      await Api.guestVisitStart(button.dataset.visit);
      showToast('Приход гостя отмечен', 'success');
      await loadExpected();
    } else if (action === 'visit-finish') {
      await Api.guestVisitFinish(button.dataset.visit);
      showToast('Уход гостя отмечен', 'success');
      await loadExpected();
    }
  } catch (error) {
    showToast(error.message || 'Не удалось выполнить действие', 'error');
  } finally {
    button.disabled = false;
  }
}

async function confirmReject() {
  const comment = $('vr-reject-comment')?.value?.trim() || '';
  if (comment.length < 3) {
    showToast('Укажите причину отказа (хотя бы 3 символа)', 'error');
    return;
  }
  const button = $('vr-reject-confirm');
  button.disabled = true;
  try {
    await Api.visitRequestReject(state.rejectId, comment);
    closeModal('vr-reject-modal');
    showToast('Заявка отклонена', 'success');
    await loadQueue();
  } catch (error) {
    showToast(error.message || 'Не удалось отклонить заявку', 'error');
  } finally {
    button.disabled = false;
  }
}

async function markAllRead() {
  try {
    await Api.notificationsReadAll();
    await loadInbox();
    showToast('Все уведомления прочитаны', 'success');
  } catch (error) {
    showToast(error.message || 'Не удалось отметить уведомления', 'error');
  }
}

// ------------------------------------------------------------------ модальные окна

function openModal(id) {
  $(id)?.classList.remove('hidden');
  document.body.classList.add('no-scroll');
}

function closeModal(id) {
  $(id)?.classList.add('hidden');
  if (!document.querySelector('.modal-overlay-base:not(.hidden)')) {
    document.body.classList.remove('no-scroll');
  }
}

async function openCreateModal(request = null) {
  await fillHosts();
  const form = $('vr-create-form');
  form?.reset();
  state.editingId = request ? request.id : null;

  const title = $('vr-create-title');
  const hostSelect = $('vr-form-host');
  const visitorFields = ['#vr-form-lastName', '#vr-form-firstName', '#vr-form-middleName',
    '#vr-form-organization', '#vr-form-phone'];

  if (request) {
    if (title) title.textContent = `Заявка №${request.id}: план посещения`;
    if (hostSelect) {
      hostSelect.value = request.host?.id || '';
      hostSelect.disabled = true;
    }
    $('vr-form-lastName').value = request.visitorLastName || '';
    $('vr-form-firstName').value = request.visitorFirstName || '';
    $('vr-form-middleName').value = request.visitorMiddleName || '';
    $('vr-form-organization').value = request.organization || '';
    $('vr-form-phone').value = request.phone || '';
    $('vr-form-purpose').value = request.purpose || '';
    $('vr-form-from').value = toLocalInput(request.plannedFrom);
    $('vr-form-to').value = toLocalInput(request.plannedTo);
    // Данные посетителя после черновика не меняются — только время и цель
    visitorFields.forEach(selector => { const field = $(selector.slice(1)); if (field) field.disabled = true; });
  } else {
    if (title) title.textContent = 'Новая заявка на посещение';
    if (hostSelect) hostSelect.disabled = false;
    visitorFields.forEach(selector => { const field = $(selector.slice(1)); if (field) field.disabled = false; });
  }

  openModal('vr-create-modal');
  $('vr-form-from')?.focus();
}

function toLocalInput(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (number) => String(number).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

async function fillHosts() {
  if (state.hostsLoaded) return;
  const select = $('vr-form-host');
  if (!select) return;
  try {
    const data = await Api.personnelList({ size: 200 });
    const people = Array.isArray(data) ? data : (data?.content || []);
    select.innerHTML = people
      .map(person => `<option value="${person.id}">${escapeHtml(person.fullName || person.id)}</option>`)
      .join('');
    state.hostsLoaded = people.length > 0;
    if (!people.length) {
      select.innerHTML = '<option value="">Нет сотрудников в справочнике</option>';
    }
  } catch (_) {
    select.innerHTML = '<option value="">Не удалось загрузить сотрудников</option>';
  }
}

async function submitCreateForm(event) {
  event.preventDefault();
  const submitButton = event.target.querySelector('button[type="submit"]');
  const hostId = $('vr-form-host')?.value;
  const from = $('vr-form-from')?.value;
  const to = $('vr-form-to')?.value;

  if (!hostId) {
    showToast('Выберите сотрудника, к которому идёт посетитель', 'error');
    return;
  }
  if (!from || !to || new Date(to) <= new Date(from)) {
    showToast('Проверьте период: окончание должно быть позже начала', 'error');
    return;
  }

  submitButton.disabled = true;
  try {
    if (state.editingId) {
      await Api.visitRequestUpdatePlan(state.editingId, {
        plannedFrom: new Date(from).toISOString(),
        plannedTo: new Date(to).toISOString(),
        purpose: $('vr-form-purpose').value.trim() || null
      });
      closeModal('vr-create-modal');
      showToast('План посещения обновлён', 'success');
      activateTab('mine');
      await loadMine();
      return;
    }

    await Api.visitRequestCreate({
      hostPersonId: hostId,
      visitorLastName: $('vr-form-lastName').value.trim(),
      visitorFirstName: $('vr-form-firstName').value.trim(),
      visitorMiddleName: $('vr-form-middleName').value.trim() || null,
      organization: $('vr-form-organization').value.trim() || null,
      phone: $('vr-form-phone').value.trim() || null,
      purpose: $('vr-form-purpose').value.trim() || null,
      plannedFrom: new Date(from).toISOString(),
      plannedTo: new Date(to).toISOString()
    });
    closeModal('vr-create-modal');
    showToast('Черновик заявки создан', 'success');
    activateTab('mine');
    await loadMine();
  } catch (error) {
    showToast(error.message || 'Не удалось создать заявку', 'error');
  } finally {
    submitButton.disabled = false;
  }
}

async function openDetailsModal(id) {
  setHtml('vr-details-body', skeletons(2));
  openModal('vr-details-modal');
  try {
    const [request, history] = await Promise.all([
      Api.visitRequestGet(id),
      Api.visitRequestHistory(id)
    ]);

    $('vr-details-title').textContent = `Заявка №${request.id}`;
    const steps = history.map(entry => `
      <li>
        <strong>${escapeHtml(actionLabel(entry.action))}</strong>
        <span>${escapeHtml(statusLabel(entry.fromStatus))} → ${escapeHtml(statusLabel(entry.toStatus))}</span>
        <span>${escapeHtml(formatMoment(entry.createdAt))} · ${escapeHtml(entry.actorEmail || '—')}</span>
        ${entry.comment ? `<div class="text-xs text-slate-600 mt-1">${escapeHtml(entry.comment)}</div>` : ''}
      </li>`).join('');

    setHtml('vr-details-body', `
      <div class="space-y-3">
        <div class="flex items-center gap-2">
          ${statusPill(request.status)}
          <span class="text-xs text-slate-400">${escapeHtml(formatRange(request))}</span>
        </div>
        <div>
          <h4 class="vr-visitor">${escapeHtml(visitorName(request))}</h4>
          <p class="vr-meta">${escapeHtml(request.organization || 'Без организации')} · ${escapeHtml(request.phone || 'телефон не указан')}</p>
          <p class="vr-meta">Хост: ${escapeHtml(request.host?.fullName || '—')}</p>
          ${request.purpose ? `<p class="vr-purpose">${escapeHtml(request.purpose)}</p>` : ''}
          ${request.decisionComment ? `<p class="vr-purpose">Комментарий решения: ${escapeHtml(request.decisionComment)}</p>` : ''}
        </div>
        <div>
          <p class="vr-stat-label mb-2">История переходов</p>
          <ul class="vr-timeline">${steps || '<li><span>Пока нет записей</span></li>'}</ul>
        </div>
      </div>`);
  } catch (error) {
    setHtml('vr-details-body', emptyState('Не удалось загрузить заявку', error.message));
  }
}

// ------------------------------------------------------------------ все заявки

async function loadAll() {
  setHtml('vr-all-list', skeletons(3));
  try {
    const status = $('vr-all-status')?.value || '';
    const list = await Api.visitRequestsAll(status);
    renderAll(list);
  } catch (error) {
    setHtml('vr-all-list', emptyState('Не удалось загрузить заявки', error.message));
  }
}

function renderAll(list) {
  if (!list.length) {
    setHtml('vr-all-list', emptyState('Заявок нет', 'Здесь видны заявки всех сотрудников'));
    return;
  }

  setHtml('vr-all-list', list.map(request => `
    <article class="vr-card vr-fade">
      <div class="vr-card-main">
        <div class="vr-card-head">
          <span class="vr-id">№${request.id}</span>
          ${statusPill(request.status)}
          <span class="text-xs text-slate-400">${escapeHtml(formatMoment(request.createdAt))}</span>
        </div>
        <h4 class="vr-visitor">${escapeHtml(visitorName(request))}</h4>
        <p class="vr-meta">${escapeHtml(request.organization || 'Без организации')} · автор: ${escapeHtml(request.authorEmail || '—')}</p>
        <p class="vr-meta">${escapeHtml(formatRange(request))}</p>
        ${request.decisionComment ? `<p class="vr-purpose">Решение: ${escapeHtml(request.decisionComment)}</p>` : ''}
      </div>
      <div class="vr-card-side">
        <button type="button" class="vr-btn vr-btn-ghost" data-vr-action="details" data-id="${request.id}">История</button>
      </div>
    </article>`).join(''));
}

// ------------------------------------------------------------------ бейджи

function paintBadges(unread, queueCount) {
  const targets = [$('vr-nav-badge'), $('vr-nav-badge-desktop'), $('vr-bell-badge')];
  if (typeof unread === 'number') {
    targets.forEach(node => {
      if (!node) return;
      node.textContent = unread > 99 ? '99+' : String(unread);
      node.classList.toggle('hidden', unread === 0);
    });
    const inboxBadge = $('vr-inbox-badge');
    if (inboxBadge) {
      inboxBadge.textContent = unread > 99 ? '99+' : String(unread);
      inboxBadge.classList.toggle('hidden', unread === 0);
    }
    const stat = $('vr-stat-unread');
    if (stat) stat.textContent = unread;
  }

  if (typeof queueCount === 'number') {
    const badge = $('vr-queue-badge');
    if (badge) {
      badge.textContent = queueCount > 99 ? '99+' : String(queueCount);
      badge.classList.toggle('hidden', queueCount === 0);
    }
    const stat = $('vr-stat-queue');
    if (stat) stat.textContent = queueCount;
  }
}

async function updateUnreadStats() {
  try {
    const unread = await Api.notificationsUnread();
    paintBadges(Number(unread?.unread || 0),
      canApprove() ? state.cache.queue.length : 0);
  } catch (_) {
    // не критично
  }
}

// ------------------------------------------------------------------ фоновые уведомления

let watcherTimer = null;
let knownUnread = null;

/**
 * Следит за счетчиком непрочитанных вне раздела заявок и показывает тост,
 * когда появляется новое уведомление (решение по заявке, новая заявка и т.п.).
 */
export function startNotificationWatcher() {
  if (watcherTimer) return;

  const tick = async () => {
    if (document.hidden) return;
    try {
      const data = await Api.notificationsUnread();
      const unread = Number(data?.unread || 0);

      if (knownUnread !== null && unread > knownUnread) {
        const list = await Api.notifications();
        list.filter(item => !item.read)
          .slice(0, 2)
          .forEach(item => showToast(item.title, 'info', 6000));
      }

      knownUnread = unread;
      paintBadges(unread);
    } catch (_) {
      // бэкенд недоступен — молча ждём следующего тика
    }
  };

  tick();
  watcherTimer = setInterval(tick, 30_000);
  window.addEventListener('focus', tick);
}
