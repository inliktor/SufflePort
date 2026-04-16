// ==================== Auth Guard ====================
const _authUsername = localStorage.getItem('username');
if (!_authUsername) {
  window.location.href = '/login.html';
  throw new Error('Redirecting to login');
}

import { Api } from './api.js';
import { showToast, formatDateTime, formatDate, escapeHtml } from './utils.js';

// Global error handlers
window.addEventListener('error', (evt) => {
  try {
    console.error('Global script error:', evt.error || evt.message);
    if (typeof showToast === 'function') showToast('Внутренняя ошибка интерфейса', 'error');
  } catch (_) {}
});
window.addEventListener('unhandledrejection', (evt) => {
  try {
    console.error('Unhandled promise rejection:', evt.reason);
  } catch (_) {}
});

// ==================== State ====================
const isAdmin = localStorage.getItem('isAdmin') === 'true';
const roleId = parseInt(localStorage.getItem('roleId') || '0');

let currentPerson = null;
let personnelData = [];
let eventsData = [];
let devicesData = [];
let guestsData = [];
let positionsData = [];
let camerasData = [];
let guestVisitsTodayData = [];
let statsEventsData = [];
let scanningForPerson = null;
let guestQrScannerStream = null;
let guestQrScannerActive = false;
let guestQrScanLock = false;
let editingPersonnelOriginal = null;
let editingPersonnelPhotoBase64 = null;
let currentSection = 'dashboard';
let searchDebounceTimer = null;

function defaultPageSize(section) {
  const mobile = window.innerWidth <= 767;
  if (section === 'events') return mobile ? 8 : 14;
  return mobile ? 6 : 10;
}

const listState = {
  personnel: { page: 0, size: defaultPageSize('personnel'), totalPages: 1, totalElements: 0, query: '', sort: 'lastName', dir: 'asc' },
  guests: { page: 0, size: defaultPageSize('guests'), totalPages: 1, totalElements: 0, query: '', sort: 'lastName', dir: 'asc' },
  events: { page: 0, size: defaultPageSize('events'), totalPages: 1, totalElements: 0, sort: 'createdAt', dir: 'desc', source: '', direction: '', date: '' }
};

function normalizePageResponse(payload) {
  if (Array.isArray(payload)) {
    return {
      content: payload,
      number: 0,
      size: payload.length,
      totalPages: payload.length ? 1 : 0,
      totalElements: payload.length
    };
  }

  return {
    content: Array.isArray(payload?.content) ? payload.content : [],
    number: payload?.number ?? 0,
    size: payload?.size ?? 0,
    totalPages: payload?.totalPages ?? 0,
    totalElements: payload?.totalElements ?? 0
  };
}

function updateListState(section, pageData) {
  const state = listState[section];
  state.page = pageData.number;
  state.size = pageData.size || state.size;
  state.totalPages = Math.max(pageData.totalPages || 0, pageData.content.length ? 1 : 0);
  state.totalElements = pageData.totalElements ?? pageData.content.length;
}

function updateListMeta(section, noun) {
  const state = listState[section];
  const meta = document.getElementById(`${section}-results-meta`);
  if (!meta) return;

  if (!state.totalElements) {
    meta.textContent = `Ничего не найдено`;
    return;
  }

  const start = state.page * state.size + 1;
  const end = Math.min((state.page + 1) * state.size, state.totalElements);
  meta.textContent = `Показаны ${start}-${end} из ${state.totalElements} ${noun}`;
}

function buildPageButtons(currentPage, totalPages) {
  const pages = new Set([0, totalPages - 1, currentPage - 1, currentPage, currentPage + 1]);
  return Array.from(pages)
    .filter(page => page >= 0 && page < totalPages)
    .sort((a, b) => a - b);
}

function renderPagination(section, onPageChange) {
  const state = listState[section];
  const container = document.getElementById(`${section}-pagination`);
  if (!container) return;

  if (state.totalPages <= 1) {
    container.innerHTML = '';
    return;
  }

  const pages = buildPageButtons(state.page, state.totalPages);
  const sizeOptions = [6, 10, 20, 50]
    .map(size => `<option value="${size}" ${size === state.size ? 'selected' : ''}>${size}/стр.</option>`)
    .join('');

  container.innerHTML = `
    <div class="pagination-bar">
      <div class="pagination-summary">Страница ${state.page + 1} из ${state.totalPages}</div>
      <div class="pagination-controls">
        <button class="pagination-btn" data-page="0" ${state.page === 0 ? 'disabled' : ''}>Первая</button>
        <button class="pagination-btn" data-page="${Math.max(state.page - 1, 0)}" ${state.page === 0 ? 'disabled' : ''}>Назад</button>
        <div class="pagination-pages">
          ${pages.map(page => `<button class="pagination-btn ${page === state.page ? 'active' : ''}" data-page="${page}">${page + 1}</button>`).join('')}
        </div>
        <button class="pagination-btn" data-page="${Math.min(state.page + 1, state.totalPages - 1)}" ${state.page >= state.totalPages - 1 ? 'disabled' : ''}>Вперёд</button>
        <button class="pagination-btn" data-page="${state.totalPages - 1}" ${state.page >= state.totalPages - 1 ? 'disabled' : ''}>Последняя</button>
      </div>
      <label class="pagination-size-wrap">
        <span class="pagination-size-label">Размер</span>
        <select class="pagination-size-select" data-page-size>${sizeOptions}</select>
      </label>
    </div>
  `;

  container.querySelectorAll('[data-page]').forEach(btn => {
    btn.addEventListener('click', () => {
      const targetPage = Number(btn.dataset.page);
      if (!Number.isNaN(targetPage) && targetPage !== state.page) {
        onPageChange(targetPage);
      }
    });
  });

  container.querySelector('[data-page-size]')?.addEventListener('change', (event) => {
    state.size = Number(event.target.value) || state.size;
    state.page = 0;
    onPageChange(0);
  });
}

function scheduleListReload(callback) {
  clearTimeout(searchDebounceTimer);
  searchDebounceTimer = setTimeout(callback, 250);
}

// ==================== Mobile Auto Refresh ====================
let mobileAutoRefreshTimer = null;
let lastAutoRefreshAt = 0;
let autoRefreshInFlight = false;

async function refreshActiveSection(reason = 'manual') {
  if (window.innerWidth > 768) return;
  if (document.hidden) return;
  if (autoRefreshInFlight) return;

  autoRefreshInFlight = true;
  try {
    await loadSectionData(currentSection);
  } catch (e) {
    console.debug('Auto refresh failed:', reason, e);
  } finally {
    lastAutoRefreshAt = Date.now();
    autoRefreshInFlight = false;
  }
}

function startMobileAutoRefresh() {
  const isMobile = window.innerWidth <= 768;
  if (!isMobile) {
    if (mobileAutoRefreshTimer) { clearInterval(mobileAutoRefreshTimer); mobileAutoRefreshTimer = null; }
    return;
  }
  if (mobileAutoRefreshTimer) return;
  mobileAutoRefreshTimer = setInterval(() => refreshActiveSection('interval'), 10_000);
}

// ==================== UI Helpers ====================
function showLoading(message = 'Загрузка...') {
  const overlay = document.getElementById('loading-overlay');
  if (overlay) {
    const textEl = overlay.querySelector('p');
    if (textEl) textEl.textContent = message;
    overlay.classList.remove('hidden');
  }
}

function hideLoading() {
  document.getElementById('loading-overlay')?.classList.add('hidden');
}

function showFieldError(field, message) {
  if (!field) return;
  field.querySelectorAll?.('.error-message').forEach(e => e.remove());
  const errorDiv = document.createElement('div');
  errorDiv.className = 'error-message text-red-600 text-xs mt-1 font-medium';
  errorDiv.textContent = message;
  field.parentNode?.appendChild(errorDiv);
  field.classList.add('border-red-500');
  setTimeout(() => field.classList.remove('border-red-500'), 3000);
}

function clearErrors(container) {
  if (!container) return;
  container.querySelectorAll('.error-message').forEach(e => e.remove());
}

function asImageSrc(photoBase64) {
  if (!photoBase64) return null;
  return photoBase64.startsWith('data:') ? photoBase64 : `data:image/jpeg;base64,${photoBase64}`;
}

let photoCaptureResolver = null;
let photoCaptureRejector = null;

function normalizePhotoPayload(payload) {
  if (!payload) return null;
  const raw = String(payload).trim();
  if (!raw) return null;
  if (raw.startsWith('data:image/')) return raw;
  return `data:image/jpeg;base64,${raw}`;
}

async function capturePhotoViaAndroidBridge(timeoutMs = 15000) {
  if (!window.Android) {
    throw new Error('Android bridge недоступен');
  }

  const invoke = window.Android.capturePhoto
    || window.Android.takePhoto
    || window.Android.openCameraForPhoto;

  if (typeof invoke !== 'function') {
    throw new Error('Android bridge не содержит метода съемки фото');
  }

  if (photoCaptureResolver || photoCaptureRejector) {
    throw new Error('Съемка фото уже выполняется');
  }

  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      photoCaptureResolver = null;
      photoCaptureRejector = null;
      reject(new Error('Не удалось получить фото из Android WebView')); 
    }, timeoutMs);

    photoCaptureResolver = (value) => {
      clearTimeout(timer);
      photoCaptureResolver = null;
      photoCaptureRejector = null;
      resolve(value);
    };

    photoCaptureRejector = (err) => {
      clearTimeout(timer);
      photoCaptureResolver = null;
      photoCaptureRejector = null;
      reject(err instanceof Error ? err : new Error(String(err || 'Ошибка съемки фото')));
    };

    try {
      const immediateResult = invoke.call(window.Android);

      // Some Android bridges return image data immediately instead of invoking JS callbacks.
      if (typeof immediateResult === 'string' && immediateResult.trim()) {
        const normalized = normalizePhotoPayload(immediateResult);
        photoCaptureResolver(normalized || immediateResult);
        return;
      }

      // Some bridges return a Promise-like object.
      if (immediateResult && typeof immediateResult.then === 'function') {
        immediateResult
          .then((value) => {
            if (!value) return;
            const normalized = normalizePhotoPayload(value);
            photoCaptureResolver(normalized || value);
          })
          .catch((err) => {
            photoCaptureRejector(err);
          });
      }
    } catch (err) {
      photoCaptureRejector(err);
    }
  });
}

async function capturePhotoFromCamera() {
  if (!window.isSecureContext && window.Android) {
    return capturePhotoViaAndroidBridge();
  }

  if (!navigator.mediaDevices?.getUserMedia) {
    if (window.Android) {
      return capturePhotoViaAndroidBridge();
    }
    throw new Error('Камера не поддерживается в этом браузере/WebView');
  }

  let stream = null;
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
    const video = document.createElement('video');
    video.srcObject = stream;
    video.playsInline = true;
    await video.play();
    await new Promise(resolve => setTimeout(resolve, 350));

    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth || 720;
    canvas.height = video.videoHeight || 540;
    const ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL('image/jpeg', 0.88);
  } catch (err) {
    if (window.Android) {
      return capturePhotoViaAndroidBridge();
    }
    throw err;
  } finally {
    stream?.getTracks?.().forEach(track => track.stop());
  }
}

// ==================== NFC Mobile Callback ====================
window.onNfcReceived = async function(nfcData) {
  if (scanningForPerson) {
    try {
      await Api.cardRegisterByName(nfcData, `${scanningForPerson.lastName} ${scanningForPerson.firstName}`);
      showToast(`Карта зарегистрирована для ${scanningForPerson.lastName} ${scanningForPerson.firstName}`, 'success');
      scanningForPerson = null;
      loadPersonnel();
    } catch (error) {
      console.error('Card registration error:', error);
      showToast('Ошибка регистрации карты', 'error');
      scanningForPerson = null;
    }
  } else {
    const uidInput = document.querySelector('input[name="uid"]');
    if (uidInput) {
      uidInput.value = nfcData;
      uidInput.focus();
      showToast('NFC карта отсканирована', 'success');
    }
  }
};

// ==================== Navigation ====================
function toggleMobileMenu(show) {
  const overlay = document.getElementById('mobile-menu-overlay');
  const sidebar = document.getElementById('mobile-menu-sidebar');

  if (show) {
    overlay?.classList.remove('hidden', 'opacity-0', 'pointer-events-none');
    setTimeout(() => overlay?.classList.add('opacity-100'), 10);
    sidebar?.classList.remove('mobile-sidebar-hidden');
    sidebar?.classList.add('mobile-sidebar-visible');
    document.body.classList.add('no-scroll');
  } else {
    overlay?.classList.remove('opacity-100');
    overlay?.classList.add('opacity-0', 'pointer-events-none');
    sidebar?.classList.remove('mobile-sidebar-visible');
    sidebar?.classList.add('mobile-sidebar-hidden');
    document.body.classList.remove('no-scroll');
    setTimeout(() => {
      if (sidebar?.classList.contains('mobile-sidebar-hidden')) {
        overlay?.classList.add('hidden');
      }
    }, 300);
  }
}

function navigateTo(sectionId) {
  // Update desktop nav active state
  document.querySelectorAll('.nav-item').forEach(nav => {
    nav.classList.toggle('active', nav.dataset.section === sectionId);
  });
  // Update mobile nav active state
  document.querySelectorAll('.nav-item-mobile').forEach(nav => {
    nav.classList.toggle('active', nav.dataset.section === sectionId);
  });

  // Show/hide sections
  document.querySelectorAll('.section-content').forEach(section => {
    section.classList.toggle('hidden', section.id !== sectionId);
  });

  updatePageTitle(sectionId);
  toggleMobileMenu(false);
  currentSection = sectionId;
  refreshActiveSection('navigate');

  // On desktop, refreshActiveSection is a no-op; load chart-based sections explicitly
  if (window.innerWidth > 768 && sectionId === 'stats') loadStats();

  if (window.innerWidth < 768) {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
}

function initNavigation() {
  document.querySelectorAll('[data-section]').forEach(item => {
    item.addEventListener('click', (e) => {
      e.preventDefault();
      navigateTo(item.dataset.section);
    });

    item.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        navigateTo(item.dataset.section);
      }
    });
  });

  // Nav-links inside sections (e.g. "All events" button on dashboard)
  document.querySelectorAll('.nav-link[data-section]').forEach(link => {
    link.addEventListener('click', (e) => {
      e.preventDefault();
      navigateTo(link.dataset.section);
    });
  });

  document.getElementById('mobile-menu-btn')?.addEventListener('click', () => toggleMobileMenu(true));
  document.getElementById('close-menu-btn')?.addEventListener('click', () => toggleMobileMenu(false));
  document.getElementById('mobile-menu-overlay')?.addEventListener('click', () => toggleMobileMenu(false));
}

function updatePageTitle(sectionId) {
  const titles = {
    dashboard:    { title: 'Дашборд',              subtitle: 'Обзор' },
    personnel:    { title: 'Персонал',              subtitle: 'Управление сотрудниками и картами доступа' },
    events:       { title: 'События',               subtitle: 'Журнал прохода и действий' },
    devices:      { title: 'Устройства',            subtitle: 'Терминалы, считыватели и оборудование' },
    cameras:      { title: 'Камеры',                subtitle: 'Управление IP-камерами СКУД' },
    faces:        { title: 'Распознавание лиц',     subtitle: 'Биометрическая идентификация' },
    guests:       { title: 'Гости',                 subtitle: 'Управление посетителями' },
    positions:    { title: 'Должности',             subtitle: 'Управление должностями и уровнями доступа' },
    integrations: { title: 'Интеграции',            subtitle: 'Подключение внешних систем' },
    profile:      { title: 'Профиль',               subtitle: 'Данные аккаунта и сессии' },
    stats:        { title: 'Статистика',             subtitle: 'Аналитика проходов и активности системы' }
  };
  const info = titles[sectionId] || titles.dashboard;
  const titleEl = document.getElementById('page-title');
  const subtitleEl = document.getElementById('page-subtitle');
  if (titleEl) titleEl.textContent = info.title;
  if (subtitleEl) subtitleEl.textContent = info.subtitle;
}

// ==================== Profile ====================
async function loadProfile() {
  const setText = (id, value) => {
    const el = document.getElementById(id);
    if (el) el.textContent = value ?? '—';
  };

  function setAvatar(fullName, photoBase64) {
    const initialsEl = document.getElementById('profile-avatar-initials');
    const imgEl = document.getElementById('profile-avatar-img');
    const src = asImageSrc(photoBase64);
    if (src && imgEl) {
      imgEl.src = src;
      imgEl.classList.remove('hidden');
      if (initialsEl) initialsEl.classList.add('hidden');
    } else if (initialsEl) {
      const initials = fullName ? fullName.split(' ').map(w => w[0]).filter(Boolean).slice(0, 2).join('').toUpperCase() : '?';
      initialsEl.textContent = initials;
      initialsEl.classList.remove('hidden');
      if (imgEl) { imgEl.classList.add('hidden'); imgEl.src = ''; }
    }
  }

  try {
    const me = await Api.getCurrentUser();
    setText('profile-email', me.email || '—');
    setText('profile-username', me.username || localStorage.getItem('username') || '—');

    // Role badge — just the role name, no roleId suffix
    const roleBadge = document.getElementById('profile-role-badge');
    if (roleBadge) roleBadge.textContent = me.roleName || (me.roleId != null ? `Роль #${me.roleId}` : '—');

    const isAdminVal = typeof me.isAdmin === 'boolean' ? me.isAdmin : (localStorage.getItem('isAdmin') === 'true');
    const adminEl = document.getElementById('profile-isAdmin');
    if (adminEl) {
      adminEl.innerHTML = isAdminVal
        ? '<span class="badge bg-green-100 text-green-800">Да</span>'
        : '<span class="badge bg-slate-100 text-slate-600">Нет</span>';
    }

    setText('profile-userId', me.userId != null ? String(me.userId) : '—');
    setText('profile-personId', me.personId || '—');
    setText('profile-createdAt', me.createdAt ? formatDateTime(me.createdAt) : '—');

    // If user is linked to a Personnel record, load and show edit form
    if (me.personId) {
      try {
        const person = await Api.personnelGet(me.personId);
        const fullName = person.fullName || [person.lastName, person.firstName, person.middleName].filter(Boolean).join(' ');

        // Show full name and avatar
        setText('profile-fullname', fullName || me.username || '—');
        setAvatar(fullName, person.photoBase64 || null);

        // Show the photo upload button
        const uploadBtn = document.getElementById('profile-upload-photo-btn');
        if (uploadBtn) uploadBtn.classList.remove('hidden');

        // Fill edit form
        const lnEl = document.getElementById('profile-edit-lastName');
        const fnEl = document.getElementById('profile-edit-firstName');
        const mnEl = document.getElementById('profile-edit-middleName');
        const phEl = document.getElementById('profile-edit-phone');
        if (lnEl) lnEl.value = person.lastName || '';
        if (fnEl) fnEl.value = person.firstName || '';
        if (mnEl) mnEl.value = person.middleName || '';
        if (phEl) phEl.value = person.phone || '';

        // Show edit section, hide notice
        const editSection = document.getElementById('profile-edit-section');
        if (editSection) editSection.classList.remove('hidden');
        const noticeEl = document.getElementById('profile-no-person-notice');
        if (noticeEl) noticeEl.classList.add('hidden');

        // Store personId for form submit
        const pidInput = document.getElementById('profile-person-id');
        if (pidInput) pidInput.value = me.personId;
      } catch (_) {
        setText('profile-fullname', me.username || '—');
        setAvatar(me.username, null);
        document.getElementById('profile-no-person-notice')?.classList.remove('hidden');
      }
    } else {
      setText('profile-fullname', me.username || '—');
      setAvatar(me.username, null);
      document.getElementById('profile-no-person-notice')?.classList.remove('hidden');
    }
  } catch (error) {
    console.warn('Profile load failed, using localStorage fallback:', error);
    const username = localStorage.getItem('username') || '—';
    setText('profile-email', localStorage.getItem('email') || '—');
    setText('profile-username', username);
    setText('profile-fullname', username);
    setAvatar(username, null);
    const roleIdRaw = localStorage.getItem('roleId');
    const roleBadge = document.getElementById('profile-role-badge');
    if (roleBadge) roleBadge.textContent = roleIdRaw ? `Роль #${roleIdRaw}` : '—';
    const adminEl = document.getElementById('profile-isAdmin');
    if (adminEl) {
      const rawAdmin = localStorage.getItem('isAdmin') === 'true';
      adminEl.innerHTML = rawAdmin
        ? '<span class="badge bg-green-100 text-green-800">Да</span>'
        : '<span class="badge bg-slate-100 text-slate-600">Нет</span>';
    }
    setText('profile-userId', '—');
    setText('profile-personId', '—');
    setText('profile-createdAt', '—');
    document.getElementById('profile-no-person-notice')?.classList.remove('hidden');
  }
}

function initProfileEdit() {
  document.getElementById('profile-edit-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const personId = document.getElementById('profile-person-id')?.value;
    if (!personId) return;

    const lastName = document.getElementById('profile-edit-lastName')?.value?.trim();
    const firstName = document.getElementById('profile-edit-firstName')?.value?.trim();
    const middleName = document.getElementById('profile-edit-middleName')?.value?.trim();
    const phone = document.getElementById('profile-edit-phone')?.value?.trim();

    if (!lastName || !firstName) {
      showToast('Фамилия и Имя обязательны', 'error');
      return;
    }

    try {
      showLoading('Сохранение...');
      await Api.personnelUpdate(personId, {
        lastName,
        firstName,
        middleName: middleName || null,
        phone: phone || null
      });
      hideLoading();
      showToast('Профиль обновлён', 'success');
      await loadProfile();
    } catch (err) {
      hideLoading();
      showToast(err.message || 'Ошибка сохранения', 'error');
    }
  });

  document.getElementById('profile-photo-input')?.addEventListener('change', async (e) => {
    const file = e.target.files[0];
    const personId = document.getElementById('profile-person-id')?.value;
    if (!file || !personId) return;
    if (!file.type.startsWith('image/')) { showToast('Выберите изображение', 'error'); return; }
    if (file.size > 5 * 1024 * 1024) { showToast('Файл не должен превышать 5MB', 'error'); return; }

    const reader = new FileReader();
    reader.onload = async (ev) => {
      const photoBase64 = ev.target.result;
      // Preview immediately
      const imgEl = document.getElementById('profile-avatar-img');
      const initialsEl = document.getElementById('profile-avatar-initials');
      if (imgEl) { imgEl.src = photoBase64; imgEl.classList.remove('hidden'); }
      if (initialsEl) initialsEl.classList.add('hidden');

      try {
        showLoading('Сохранение фото...');
        await Api.personnelUpdate(personId, { photoBase64 });
        hideLoading();
        showToast('Фото сохранено', 'success');
      } catch (err) {
        hideLoading();
        showToast(err.message || 'Ошибка сохранения фото', 'error');
      }
    };
    reader.readAsDataURL(file);
  });

  document.getElementById('profile-upload-photo-btn')?.addEventListener('click', () => {
    document.getElementById('profile-photo-input')?.click();
  });
}

// ==================== Dashboard ====================
async function loadDashboard() {
  try {
    const [personnelPage, eventsPage, devicesPage, activeCards] = await Promise.all([
      Api.personnelPage({ page: 0, size: 1 }).catch(() => ({ content: [], totalElements: 0 })),
      Api.eventsRecentPage({ page: 0, size: 5, sort: 'createdAt', dir: 'desc' }).catch(() => ({ content: [], totalElements: 0 })),
      Api.devicesPage({ page: 0, size: 1 }).catch(() => ({ content: [], totalElements: 0 })),
      Api.cardCountActive().catch(() => null)
    ]);

    const personnelSummary = normalizePageResponse(personnelPage);
    const eventsSummary = normalizePageResponse(eventsPage);
    const devicesSummary = normalizePageResponse(devicesPage);
    const recentEvents = eventsSummary.content;

    personnelData = personnelSummary.content;
    eventsData = recentEvents;
    devicesData = devicesSummary.content;

    const statPersonnel = document.getElementById('stat-personnel');
    const statCards = document.getElementById('stat-cards');
    const statEvents = document.getElementById('stat-events');
    const statDevices = document.getElementById('stat-devices');

    if (statPersonnel) statPersonnel.textContent = personnelSummary.totalElements;
    if (statCards) statCards.textContent = activeCards ?? '—';
    if (statEvents) statEvents.textContent = eventsSummary.totalElements;
    if (statDevices) statDevices.textContent = devicesSummary.totalElements;

    renderRecentEvents(recentEvents);
  } catch (error) {
    console.error('Dashboard load error:', error);
    showToast('Ошибка загрузки дашборда', 'error');
  }
}

async function loadSectionData(sectionId) {
  switch (sectionId) {
    case 'personnel':
      await loadPersonnel();
      break;
    case 'events':
      await loadEvents();
      break;
    case 'devices':
      await loadDevices();
      break;
    case 'guests':
      await loadGuests();
      await loadGuestVisitsToday();
      break;
    case 'positions':
      await loadPositions();
      break;
    case 'cameras':
      await loadCameras();
      break;
    case 'profile':
      await loadProfile();
      break;
    case 'stats':
      await loadStats();
      break;
    case 'dashboard':
    default:
      await loadDashboard();
      break;
  }
}

function renderRecentEvents(events) {
  const container = document.getElementById('recent-events');
  if (!container) return;

  if (!events || events.length === 0) {
    container.innerHTML = '<div class="text-center py-8 text-slate-400 text-sm">Нет событий</div>';
    return;
  }

  container.innerHTML = events.map(event => {
    const dirClass = event.direction === 'IN' ? 'bg-green-100 text-green-800' :
                     event.direction === 'OUT' ? 'bg-blue-100 text-blue-800' : 'bg-gray-100 text-gray-800';
    const dirText  = event.direction === 'IN' ? 'Вход' : event.direction === 'OUT' ? 'Выход' : 'Неизвестно';
    const srcColor = event.source === 'NFC' ? 'bg-purple-500' : event.source === 'FACE' ? 'bg-blue-500' : 'bg-gray-500';
    const svgPath  = event.source === 'FACE'
      ? '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14.828 14.828a4 4 0 01-5.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>'
      : '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"></path>';
    const personName = resolveEventPersonName(event);

    return `
      <div class="flex items-center space-x-3 p-3 rounded-xl hover:bg-slate-50 transition-colors">
        <div class="w-9 h-9 ${srcColor} rounded-xl flex items-center justify-center flex-shrink-0">
          <svg class="w-4 h-4 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">${svgPath}</svg>
        </div>
        <div class="flex-1 min-w-0">
          <div class="flex items-center justify-between">
            <p class="font-medium text-slate-800 text-sm truncate">${escapeHtml(personName)}</p>
            <span class="badge ${dirClass} ml-2 flex-shrink-0">${dirText}</span>
          </div>
          <p class="text-xs text-slate-400 mt-0.5">${formatDateTime(event.createdAt)}${event.device ? ` · ${escapeHtml(event.device.id)}` : ''}</p>
        </div>
      </div>
    `;
  }).join('');
}

function resolveEventPersonName(event) {
  const person = event?.person;
  const fromEvent = person?.fullName || [person?.lastName, person?.firstName, person?.middleName].filter(Boolean).join(' ').trim();
  if (fromEvent) return fromEvent;

  const eventPersonId = person?.id || event?.personId || null;
  if (eventPersonId && Array.isArray(personnelData) && personnelData.length) {
    const linked = personnelData.find(p => p.id === eventPersonId);
    if (linked) {
      const fromPersonnel = linked.fullName || [linked.lastName, linked.firstName, linked.middleName].filter(Boolean).join(' ').trim();
      if (fromPersonnel) return fromPersonnel;
    }
  }

  return event?.faceName || event?.card?.uid || 'Неизвестно';
}

// ==================== Personnel ====================
async function loadPersonnel() {
  try {
    const response = await Api.personnelPage({
      page: listState.personnel.page,
      size: listState.personnel.size,
      sort: listState.personnel.sort,
      dir: listState.personnel.dir,
      query: listState.personnel.query || undefined
    });
    const pageData = normalizePageResponse(response);
    updateListState('personnel', pageData);
    personnelData = pageData.content;
    renderPersonnelTable(personnelData);
    updateListMeta('personnel', 'сотрудников');
    renderPagination('personnel', (page) => {
      listState.personnel.page = page;
      loadPersonnel();
    });
    populateManualEventPersonOptions();
  } catch (error) {
    console.error('Personnel load error:', error);
    showToast('Ошибка загрузки сотрудников', 'error');
  }
}

function renderPersonnelTable(personnel) {
  const tbody = document.getElementById('personnel-table-body');
  if (!tbody) return;

  if (!personnel || personnel.length === 0) {
    tbody.innerHTML = '<tr><td colspan="5" class="text-center py-10 text-slate-400 text-sm">Нет сотрудников</td></tr>';
    return;
  }

  tbody.innerHTML = personnel.map(person => {
    const fullName = [person.lastName, person.firstName, person.middleName].filter(Boolean).join(' ');
    const positionName = person.position?.name || 'Не указана';
    const initials = `${person.lastName?.[0] || ''}${person.firstName?.[0] || ''}`;
    const avatarSrc = asImageSrc(person.photoBase64);
    const avatarHtml = avatarSrc
      ? `<img src="${avatarSrc}" alt="" class="w-8 h-8 rounded-full object-cover flex-shrink-0" />`
      : `<div class="avatar-circle avatar-sm">${escapeHtml(initials)}</div>`;

    return `
      <tr class="hover:bg-slate-50 transition-colors">
        <td data-label="ФИО">
          <div class="flex items-center space-x-3">
            ${avatarHtml}
            <div class="min-w-0 flex-1">
              <div class="font-medium text-slate-900 text-sm">${escapeHtml(fullName)}</div>
              <div class="sm:hidden mt-1 text-xs text-slate-500 leading-5">
                <div>${escapeHtml(positionName)}</div>
                <div>${escapeHtml(person.phone || 'Телефон не указан')}</div>
                <div>${escapeHtml(person.cardUid || 'Карта не назначена')}</div>
              </div>
            </div>
          </div>
        </td>
        <td data-label="Должность" class="hidden md:table-cell text-slate-600 text-sm">${escapeHtml(positionName)}</td>
        <td data-label="Телефон" class="hidden sm:table-cell text-slate-600 text-sm">${escapeHtml(person.phone || '—')}</td>
        <td data-label="Карта" class="hidden lg:table-cell">
          <span class="badge ${person.cardUid ? 'badge-blue' : 'bg-slate-100 text-slate-400'} font-mono text-xs">
            ${escapeHtml(person.cardUid || 'Нет')}
          </span>
        </td>
        <td class="text-right table-actions">
          <div class="flex items-center justify-end space-x-1">
            <button class="btn-icon text-blue-600 hover:bg-blue-50 edit-personnel-btn" data-person-id="${escapeHtml(person.id)}" title="Редактировать">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path>
              </svg>
            </button>
            <button class="btn-icon text-red-600 hover:bg-red-50 delete-personnel-btn" data-person-id="${escapeHtml(person.id)}" title="Удалить">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
              </svg>
            </button>
          </div>
        </td>
      </tr>
    `;
  }).join('');

  tbody.querySelectorAll('.edit-personnel-btn').forEach(btn => {
    btn.addEventListener('click', async () => openEditPersonnelModal(btn.dataset.personId));
  });
  tbody.querySelectorAll('.delete-personnel-btn').forEach(btn => {
    btn.addEventListener('click', () => deletePerson(btn.dataset.personId));
  });
}

function deletePerson(personId) {
  if (!confirm('Удалить этого сотрудника?')) return;
  Api.personnelDelete(personId)
    .then(() => {
      showToast('Сотрудник удалён', 'success');
      loadPersonnel();
    })
    .catch(error => {
      const msg = String(error.message || '').trim();
      showToast(msg || 'Ошибка удаления', 'error');
    });
}

function selectPerson(personOrId) {
  const person = typeof personOrId === 'string'
    ? personnelData.find(p => p.id === personOrId)
    : personOrId;
  if (!person) return;

  currentPerson = person;
  const fullName = [person.lastName, person.firstName, person.middleName].filter(Boolean).join(' ');

  const facesEl = document.getElementById('faces-current-person');
  if (facesEl) facesEl.textContent = `Выбран: ${fullName} (ID: ${person.id.substring(0, 8)}...)`;

  showToast(`Выбран: ${fullName}`, 'success');
}

window.selectPerson = selectPerson;

// ==================== Edit Personnel Modal ====================
async function openEditPersonnelModal(personId) {
  await ensurePositionsLoaded();
  const person = personnelData.find(p => p.id === personId);
  if (!person) return;

  editingPersonnelOriginal = person;

  document.getElementById('edit-personnel-id').value = person.id;
  document.getElementById('edit-p-lastName').value = person.lastName || '';
  document.getElementById('edit-p-firstName').value = person.firstName || '';
  document.getElementById('edit-p-middleName').value = person.middleName || '';
  document.getElementById('edit-p-phone').value = person.phone || '';
  document.getElementById('edit-p-dob').value = person.dateOfBirth ? String(person.dateOfBirth).slice(0, 10) : '';

  editingPersonnelPhotoBase64 = person.photoBase64 || null;
  const preview = document.getElementById('edit-p-photo-preview');
  const src = asImageSrc(person.photoBase64 || null);
  if (preview) {
    preview.innerHTML = src
      ? `<img src="${escapeHtml(src)}" alt="" class="w-full h-full object-cover" />`
      : `<svg class="w-7 h-7 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/></svg>`;
  }

  // Populate position dropdown
  const select = document.getElementById('edit-p-positionId');
  if (select) {
    select.innerHTML = '<option value="">— Не указана —</option>';
    positionsData.forEach(pos => {
      const opt = document.createElement('option');
      opt.value = pos.id;
      opt.textContent = pos.name;
      if (person.position?.id === pos.id) opt.selected = true;
      select.appendChild(opt);
    });
  }

  const cardInput = document.getElementById('edit-p-card-uid');
  if (cardInput) cardInput.value = '';

  await loadEditPersonnelCards(person.id);

  document.getElementById('edit-personnel-modal').classList.remove('hidden');
}

async function loadEditPersonnelCards(personId) {
  const list = document.getElementById('edit-p-cards-list');
  if (!list) return;

  list.innerHTML = '<div class="text-xs text-slate-400">Загрузка карт...</div>';
  try {
    const cards = await Api.personnelCards(personId);
    if (!cards.length) {
      list.innerHTML = '<div class="text-xs text-slate-400">У сотрудника пока нет карт.</div>';
      return;
    }

    list.innerHTML = cards.map(card => `
      <div class="flex items-center justify-between gap-3 rounded-xl border border-slate-200 bg-slate-50 px-3 py-2">
        <div class="min-w-0">
          <div class="font-mono text-xs text-slate-900 break-all">${escapeHtml(card.uid)}</div>
          <div class="text-xs ${card.active ? 'text-emerald-600' : 'text-amber-600'}">${card.active ? 'Активна' : 'Отключена'}</div>
        </div>
        <div class="flex items-center gap-2 flex-shrink-0">
          <button type="button" class="btn-secondary text-xs edit-p-card-toggle-btn" data-person-id="${escapeHtml(personId)}" data-card-uid="${escapeHtml(card.uid)}" data-card-active="${card.active ? '1' : '0'}">${card.active ? 'Отключить' : 'Включить'}</button>
          <button type="button" class="btn-danger text-xs edit-p-card-delete-btn" data-person-id="${escapeHtml(personId)}" data-card-uid="${escapeHtml(card.uid)}">Удалить</button>
        </div>
      </div>
    `).join('');

    list.querySelectorAll('.edit-p-card-toggle-btn').forEach(btn => {
      btn.addEventListener('click', async () => {
        const uid = btn.dataset.cardUid;
        const active = btn.dataset.cardActive === '1';
        btn.disabled = true;
        try {
          if (active) {
            await Api.cardDeactivate(uid);
            showToast('Карта отключена', 'success');
          } else {
            await Api.cardActivate(uid);
            showToast('Карта активирована', 'success');
          }
          await loadEditPersonnelCards(personId);
          await loadPersonnel();
        } catch (error) {
          showToast(error.message || 'Не удалось изменить статус карты', 'error');
          btn.disabled = false;
        }
      });
    });

    list.querySelectorAll('.edit-p-card-delete-btn').forEach(btn => {
      btn.addEventListener('click', async () => {
        const uid = btn.dataset.cardUid;
        if (!confirm(`Удалить карту ${uid}?`)) return;
        btn.disabled = true;
        try {
          await Api.personnelCardDelete(personId, uid);
          showToast('Карта удалена', 'success');
          await loadEditPersonnelCards(personId);
          await loadPersonnel();
        } catch (error) {
          showToast(error.message || 'Не удалось удалить карту', 'error');
          btn.disabled = false;
        }
      });
    });
  } catch (error) {
    list.innerHTML = '<div class="text-xs text-red-500">Не удалось загрузить карты.</div>';
  }
}

function initEditPersonnelModal() {
  const modal = document.getElementById('edit-personnel-modal');
  if (!modal) return;

  document.getElementById('edit-p-photo')?.addEventListener('change', (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (ev) => {
      editingPersonnelPhotoBase64 = ev.target.result;
      const preview = document.getElementById('edit-p-photo-preview');
      if (preview) {
        preview.innerHTML = `<img src="${escapeHtml(editingPersonnelPhotoBase64)}" alt="" class="w-full h-full object-cover" />`;
      }
    };
    reader.readAsDataURL(file);
  });

  document.getElementById('capture-edit-personnel-photo-btn')?.addEventListener('click', async () => {
    try {
      editingPersonnelPhotoBase64 = await capturePhotoFromCamera();
      const preview = document.getElementById('edit-p-photo-preview');
      if (preview) {
        preview.innerHTML = `<img src="${escapeHtml(editingPersonnelPhotoBase64)}" alt="" class="w-full h-full object-cover" />`;
      }
      showToast('Фото получено с камеры', 'success');
    } catch (error) {
      showToast(error.message || 'Камера недоступна', 'error');
    }
  });

  document.getElementById('clear-edit-personnel-photo-btn')?.addEventListener('click', () => {
    editingPersonnelPhotoBase64 = '';
    const fileInput = document.getElementById('edit-p-photo');
    const preview = document.getElementById('edit-p-photo-preview');
    if (fileInput) fileInput.value = '';
    if (preview) {
      preview.innerHTML = '<svg class="w-7 h-7 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/></svg>';
    }
    showToast('Фото будет удалено после сохранения', 'info');
  });

  document.getElementById('edit-p-add-card-btn')?.addEventListener('click', async () => {
    const personId = document.getElementById('edit-personnel-id')?.value;
    const input = document.getElementById('edit-p-card-uid');
    const uid = input?.value?.trim();
    if (!personId || !uid) {
      showToast('Введите UID карты', 'error');
      return;
    }

    const actionBtn = document.getElementById('edit-p-add-card-btn');
    if (actionBtn) actionBtn.disabled = true;
    try {
      await Api.personnelCardAssign(personId, uid);
      if (input) input.value = '';
      showToast('Карта привязана', 'success');
      await loadEditPersonnelCards(personId);
      await loadPersonnel();
    } catch (error) {
      const message = String(error.message || '');
      const looksLikeDuplicate = /существует|already|duplicate/i.test(message);
      if (looksLikeDuplicate && confirm(`Карта ${uid} уже существует. Перепривязать её к этому сотруднику?`)) {
        try {
          await Api.personnelCardReassign(personId, uid);
          if (input) input.value = '';
          showToast('Карта перепривязана', 'success');
          await loadEditPersonnelCards(personId);
          await loadPersonnel();
        } catch (reassignError) {
          showToast(reassignError.message || 'Не удалось перепривязать карту', 'error');
        }
      } else {
        showToast(message || 'Не удалось привязать карту', 'error');
      }
    } finally {
      if (actionBtn) actionBtn.disabled = false;
    }
  });

  document.getElementById('close-edit-personnel-modal')?.addEventListener('click', () => modal.classList.add('hidden'));
  document.getElementById('cancel-edit-personnel')?.addEventListener('click', () => modal.classList.add('hidden'));
  modal.addEventListener('click', (e) => { if (e.target === modal) modal.classList.add('hidden'); });

  document.getElementById('edit-personnel-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = document.getElementById('edit-personnel-id').value;
    const lastName = document.getElementById('edit-p-lastName').value.trim();
    const firstName = document.getElementById('edit-p-firstName').value.trim();
    const middleName = document.getElementById('edit-p-middleName').value.trim();
    const phone = document.getElementById('edit-p-phone').value.trim();
    const dateOfBirth = document.getElementById('edit-p-dob').value || null;
    const positionId = document.getElementById('edit-p-positionId')?.value || null;

    if (!lastName || !firstName) {
      showToast('Фамилия и Имя обязательны', 'error');
      return;
    }

    try {
      const payload = {};
      const originalMiddle = editingPersonnelOriginal?.middleName || null;
      const originalPhone = editingPersonnelOriginal?.phone || null;
      const originalDob = editingPersonnelOriginal?.dateOfBirth ? String(editingPersonnelOriginal.dateOfBirth).slice(0, 10) : null;
      const originalPhoto = editingPersonnelOriginal?.photoBase64 || null;
      const originalPositionId = editingPersonnelOriginal?.position?.id || null;

      if (editingPersonnelOriginal?.lastName !== lastName) payload.lastName = lastName;
      if (editingPersonnelOriginal?.firstName !== firstName) payload.firstName = firstName;

      const nextMiddle = middleName || null;
      if (originalMiddle !== nextMiddle) payload.middleName = nextMiddle;

      const nextPhone = phone || null;
      if (originalPhone !== nextPhone) payload.phone = nextPhone;

      const nextDob = dateOfBirth || null;
      if (originalDob !== nextDob) payload.dateOfBirth = nextDob;

      if (originalPositionId !== positionId) {
        payload.positionId = positionId;
      }

      if (editingPersonnelPhotoBase64 !== originalPhoto) {
        payload.photoBase64 = editingPersonnelPhotoBase64;
      }

      if (!Object.keys(payload).length) {
        showToast('Нет изменений для сохранения', 'info');
        modal.classList.add('hidden');
        return;
      }

      showLoading('Сохранение...');
      await Api.personnelUpdate(id, payload);
      hideLoading();
      modal.classList.add('hidden');
      showToast('Сотрудник обновлён', 'success');
      loadPersonnel();
    } catch (err) {
      hideLoading();
      showToast(err.message || 'Ошибка сохранения', 'error');
    }
  });
}

// ==================== Events ====================
async function loadEvents() {
  try {
    const response = await Api.eventsList({
      page: listState.events.page,
      size: listState.events.size,
      sort: listState.events.sort,
      dir: listState.events.dir,
      source: listState.events.source || undefined,
      direction: listState.events.direction || undefined,
      date: listState.events.date || undefined
    });
    const pageData = normalizePageResponse(response);
    updateListState('events', pageData);
    eventsData = pageData.content;
    renderEventsTable(eventsData);
    updateListMeta('events', 'событий');
    renderPagination('events', (page) => {
      listState.events.page = page;
      loadEvents();
    });
  } catch (error) {
    console.error('Events load error:', error);
    showToast('Ошибка загрузки событий', 'error');
  }
}

function applyEventFilters() {
  listState.events.source = document.getElementById('filter-source')?.value || '';
  listState.events.direction = document.getElementById('filter-direction')?.value || '';
  listState.events.date = document.getElementById('filter-date')?.value || '';
  listState.events.page = 0;
  loadEvents();
}

function renderEventsTable(events) {
  const tbody = document.getElementById('events-table-body');
  if (!tbody) return;

  if (!events || events.length === 0) {
    tbody.innerHTML = '<tr><td colspan="5" class="text-center py-10 text-slate-400 text-sm">Нет событий</td></tr>';
    return;
  }

  tbody.innerHTML = events.map(event => {
    const dirClass = event.direction === 'IN' ? 'badge-green' : event.direction === 'OUT' ? 'badge-blue' : 'badge-yellow';
    const dirText  = event.direction === 'IN' ? 'Вход' : event.direction === 'OUT' ? 'Выход' : event.direction || 'Неизвестно';
    const srcClass = event.source === 'NFC' ? 'badge-purple' : event.source === 'FACE' ? 'badge-blue' : 'badge-yellow';
    const personName = resolveEventPersonName(event);

    return `
      <tr class="hover:bg-slate-50 transition-colors">
        <td data-label="Дата/Время">
          <div class="font-medium text-slate-900 text-sm">${formatDateTime(event.createdAt)}</div>
          <div class="md:hidden mt-1 text-xs text-slate-500 leading-5">
            <div>${escapeHtml(event.source || 'N/A')} · ${escapeHtml(dirText)}</div>
            <div>${escapeHtml(event.device?.id || 'Устройство не указано')}</div>
          </div>
        </td>
        <td data-label="Сотрудник / UID">
          <div class="font-medium text-slate-800 text-sm">${escapeHtml(personName)}</div>
        </td>
        <td data-label="Источник" class="hidden md:table-cell">
          <span class="badge ${srcClass}">${escapeHtml(event.source || 'N/A')}</span>
        </td>
        <td data-label="Направление">
          <span class="badge ${dirClass}">${escapeHtml(dirText)}</span>
        </td>
        <td data-label="Устройство" class="hidden lg:table-cell text-slate-500 text-sm">${escapeHtml(event.device?.id || '—')}</td>
      </tr>
    `;
  }).join('');
}

function initEventFilters() {
  ['filter-source', 'filter-direction', 'filter-date'].forEach(id => {
    document.getElementById(id)?.addEventListener('change', applyEventFilters);
  });
  document.getElementById('refresh-events')?.addEventListener('click', loadEvents);
}

function populateManualEventPersonOptions() {
  const select = document.getElementById('manual-event-person');
  if (!select) return;
  const current = select.value;
  const options = ['<option value="">— Выберите сотрудника —</option>'];
  personnelData.forEach(person => {
    const fullName = [person.lastName, person.firstName, person.middleName].filter(Boolean).join(' ');
    options.push(`<option value="${escapeHtml(person.id)}">${escapeHtml(fullName)}</option>`);
  });
  select.innerHTML = options.join('');
  if (current) select.value = current;
}

function initManualEventForm() {
  document.getElementById('manual-event-submit')?.addEventListener('click', async () => {
    const personId = document.getElementById('manual-event-person')?.value || null;
    const cardUid = document.getElementById('manual-event-card')?.value?.trim() || null;
    const deviceId = document.getElementById('manual-event-device')?.value?.trim() || null;
    const direction = document.getElementById('manual-event-direction')?.value || 'IN';
    const reason = document.getElementById('manual-event-reason')?.value?.trim() || '';

    if (!personId && !cardUid) {
      showToast('Укажите сотрудника или UID карты', 'error');
      return;
    }
    if (!reason) {
      showToast('Причина ручной корректировки обязательна', 'error');
      return;
    }

    try {
      showLoading('Сохранение ручного события...');
      await Api.eventsManualCreate({ personId, cardUid, deviceId, direction, reason });
      hideLoading();
      showToast('Ручное событие добавлено', 'success');
      document.getElementById('manual-event-reason').value = '';
      document.getElementById('manual-event-card').value = '';
      loadEvents();
      loadDashboard();
    } catch (error) {
      hideLoading();
      showToast(error.message || 'Не удалось сохранить ручное событие', 'error');
    }
  });
}

async function loadGuestVisitsToday() {
  const filter = document.getElementById('guest-visits-status-filter')?.value || '';
  const tbody = document.getElementById('guest-visits-today-body');
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="5" class="text-center py-8 text-slate-400 text-sm">Загрузка визитов...</td></tr>';

  try {
    guestVisitsTodayData = await Api.guestVisitsToday(filter || undefined);
    renderGuestVisitsToday();
  } catch (error) {
    tbody.innerHTML = '<tr><td colspan="5" class="text-center py-8 text-red-400 text-sm">Ошибка загрузки визитов</td></tr>';
    showToast(error.message || 'Ошибка загрузки визитов на сегодня', 'error');
  }
}

function renderGuestVisitsToday() {
  const tbody = document.getElementById('guest-visits-today-body');
  if (!tbody) return;
  if (!guestVisitsTodayData.length) {
    tbody.innerHTML = '<tr><td colspan="5" class="text-center py-8 text-slate-400 text-sm">На сегодня визитов нет</td></tr>';
    return;
  }

  tbody.innerHTML = guestVisitsTodayData.map(visit => {
    const guestName = resolveVisitGuestName(visit);
    const hostName = resolveVisitHostName(visit);
    const period = `${formatDateTime(visit.plannedFrom)} - ${formatDateTime(visit.plannedTo)}`;
    const statusText = localizeVisitStatus(visit.status || 'PLANNED');
    return `
      <tr class="hover:bg-slate-50 transition-colors">
        <td data-label="Гость" class="font-medium text-slate-900 text-sm">${escapeHtml(guestName)}</td>
        <td data-label="Хост" class="hidden md:table-cell text-slate-600 text-sm">${escapeHtml(hostName)}</td>
        <td data-label="Статус"><span class="badge bg-slate-100 text-slate-700">${escapeHtml(statusText)}</span></td>
        <td data-label="Период" class="hidden lg:table-cell text-slate-500 text-xs">${escapeHtml(period)}</td>
        <td class="text-right table-actions">
          <div class="flex items-center justify-end gap-1">
            <button class="btn-icon text-green-600 hover:bg-green-50 start-visit-btn" data-visit-id="${escapeHtml(String(visit.id))}" title="Старт">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z"/></svg>
            </button>
            <button class="btn-icon text-blue-600 hover:bg-blue-50 finish-visit-btn" data-visit-id="${escapeHtml(String(visit.id))}" title="Финиш">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/></svg>
            </button>
            <button class="btn-icon text-red-600 hover:bg-red-50 cancel-visit-btn" data-visit-id="${escapeHtml(String(visit.id))}" title="Отмена">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg>
            </button>
            <button class="btn-icon text-slate-600 hover:bg-slate-100 qr-visit-btn" data-visit-id="${escapeHtml(String(visit.id))}" title="QR">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM15 15h1m2 0h2m-5 3h3m2 0h1"/></svg>
            </button>
          </div>
        </td>
      </tr>
    `;
  }).join('');

  tbody.querySelectorAll('.start-visit-btn').forEach(btn => btn.addEventListener('click', () => updateGuestVisitState(btn.dataset.visitId, 'start')));
  tbody.querySelectorAll('.finish-visit-btn').forEach(btn => btn.addEventListener('click', () => updateGuestVisitState(btn.dataset.visitId, 'finish')));
  tbody.querySelectorAll('.cancel-visit-btn').forEach(btn => btn.addEventListener('click', () => updateGuestVisitState(btn.dataset.visitId, 'cancel')));
  tbody.querySelectorAll('.qr-visit-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const visit = guestVisitsTodayData.find(v => String(v.id) === String(btn.dataset.visitId));
      if (!visit) {
        showToast('Визит не найден', 'error');
        return;
      }
      openGuestVisitQrModal(visit);
    });
  });
}

async function updateGuestVisitState(visitId, action) {
  try {
    if (action === 'start') await Api.guestVisitStart(visitId);
    if (action === 'finish') await Api.guestVisitFinish(visitId);
    if (action === 'cancel') await Api.guestVisitCancel(visitId);
    showToast('Статус визита обновлен', 'success');
    loadGuestVisitsToday();
  } catch (error) {
    showToast(error.message || 'Не удалось обновить визит', 'error');
  }
}

function initGuestVisitControls() {
  const scannerModal = document.getElementById('guest-qr-scanner-modal');
  const qrModal = document.getElementById('guest-visit-qr-modal');

  document.getElementById('guest-visits-refresh')?.addEventListener('click', loadGuestVisitsToday);
  document.getElementById('guest-visits-status-filter')?.addEventListener('change', loadGuestVisitsToday);
  document.getElementById('guest-scan-qr-btn')?.addEventListener('click', startGuestQrScanner);
  document.getElementById('close-guest-qr-scanner')?.addEventListener('click', stopGuestQrScanner);
  document.getElementById('stop-guest-qr-scanner')?.addEventListener('click', stopGuestQrScanner);
  document.getElementById('close-guest-visit-qr-modal')?.addEventListener('click', closeGuestVisitQrModal);
  document.getElementById('close-guest-visit-qr-modal-btn')?.addEventListener('click', closeGuestVisitQrModal);
  scannerModal?.addEventListener('click', (e) => {
    if (e.target === scannerModal) stopGuestQrScanner();
  });
  qrModal?.addEventListener('click', (e) => {
    if (e.target === qrModal) closeGuestVisitQrModal();
  });

  document.getElementById('guest-checkin-btn')?.addEventListener('click', async () => {
    const code = document.getElementById('guest-checkin-code')?.value?.trim() || '';
    if (!code) {
      showToast('Введите код визита', 'error');
      return;
    }
    await performGuestCheckinByCode(code, 'manual');
  });
}

function renderGuestCheckinInfo(visit, sourceLabel = 'manual') {
  const box = document.getElementById('guest-checkin-info');
  if (!box) return;

  const guestName = visit?.guest?.fullName
    || [visit?.guest?.lastName, visit?.guest?.firstName].filter(Boolean).join(' ')
    || '—';
  const hostName = visit?.host?.fullName
    || [visit?.host?.lastName, visit?.host?.firstName].filter(Boolean).join(' ')
    || '—';
  const period = visit?.plannedFrom && visit?.plannedTo
    ? `${formatDateTime(visit.plannedFrom)} - ${formatDateTime(visit.plannedTo)}`
    : '—';
  const sourceText = sourceLabel === 'scanner' ? 'QR-сканер' : 'ручной ввод';

  box.classList.remove('hidden');
  box.innerHTML = `
    <div class="font-semibold mb-1">Check-in выполнен (${escapeHtml(sourceText)})</div>
    <div>Гость: <span class="font-medium">${escapeHtml(guestName)}</span></div>
    <div>Хост: <span class="font-medium">${escapeHtml(hostName)}</span></div>
    <div>Период: ${escapeHtml(period)}</div>
    <div>Статус: <span class="font-medium">${escapeHtml(localizeVisitStatus(visit?.status || 'ACTIVE'))}</span></div>
  `;
}

async function performGuestCheckinByCode(code, sourceLabel = 'manual') {
  try {
    const result = await Api.guestVisitCheckinByCode(code);
    document.getElementById('guest-checkin-code').value = '';

    let visit = null;
    if (result?.visitId != null) {
      try {
        visit = await Api.guestVisitGet(result.visitId);
      } catch (_) {
        // keep lightweight fallback if detail endpoint is temporarily unavailable
      }
    }

    renderGuestCheckinInfo(visit || { status: result?.visitStatus || 'ACTIVE' }, sourceLabel);
    showToast('Check-in выполнен', 'success');
    await loadGuestVisitsToday();
  } catch (error) {
    showToast(error.message || 'Check-in не выполнен', 'error');
  }
}

async function startGuestQrScanner() {
  if (guestQrScannerActive) return;

  if (window.Android?.scanQRCode) {
    try {
      window.Android.scanQRCode();
      showToast('Ожидаем QR от Android-сканера...', 'info');
      return;
    } catch (_) {
      // fallback to web scanner below
    }
  }

  if (typeof BarcodeDetector === 'undefined') {
    showToast('Сканер в этом браузере не поддерживается. Введите код вручную.', 'warning');
    document.getElementById('guest-checkin-code')?.focus();
    return;
  }

  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: { ideal: 'environment' },
        width: { ideal: 1280 },
        height: { ideal: 720 }
      },
      audio: false
    });

    const video = document.getElementById('guest-qr-video');
    if (!video) {
      stream.getTracks().forEach(t => t.stop());
      showToast('Не найден видео-элемент сканера', 'error');
      return;
    }

    guestQrScannerStream = stream;
    guestQrScannerActive = true;
    guestQrScanLock = false;
    video.srcObject = stream;
    await video.play();
    document.getElementById('guest-qr-scanner-modal')?.classList.remove('hidden');

    const detector = new BarcodeDetector({ formats: ['qr_code'] });

    const loop = async () => {
      if (!guestQrScannerActive) return;
      if (guestQrScanLock) {
        requestAnimationFrame(loop);
        return;
      }

      try {
        const codes = await detector.detect(video);
        if (codes && codes.length > 0) {
          const raw = (codes[0].rawValue || '').trim();
          if (raw) {
            guestQrScanLock = true;
            document.getElementById('guest-checkin-code').value = raw;
            await stopGuestQrScanner();
            await performGuestCheckinByCode(raw, 'scanner');
            return;
          }
        }
      } catch (_) {
        // continue scanning on next frame
      }

      requestAnimationFrame(loop);
    };

    requestAnimationFrame(loop);
  } catch (error) {
    showToast(error.message || 'Не удалось открыть камеру', 'error');
    await stopGuestQrScanner();
  }
}

async function stopGuestQrScanner() {
  guestQrScannerActive = false;
  guestQrScanLock = false;
  document.getElementById('guest-qr-scanner-modal')?.classList.add('hidden');

  const video = document.getElementById('guest-qr-video');
  if (video) {
    video.pause();
    video.srcObject = null;
  }

  if (guestQrScannerStream) {
    guestQrScannerStream.getTracks().forEach(t => t.stop());
    guestQrScannerStream = null;
  }
}

window.onQrScanned = async function (code) {
  const value = (code || '').trim();
  if (!value) return;
  document.getElementById('guest-checkin-code').value = value;
  await performGuestCheckinByCode(value, 'scanner');
};

window.onPhotoCaptured = function (payload) {
  const normalized = normalizePhotoPayload(payload);
  if (!normalized) {
    photoCaptureRejector?.(new Error('Android вернул пустое фото'));
    return;
  }
  photoCaptureResolver?.(normalized);
};

window.onPhotoCaptureError = function (message) {
  photoCaptureRejector?.(new Error(message || 'Ошибка съемки фото в Android WebView'));
};

// ==================== Devices ====================
async function loadDevices() {
  try {
    const devices = await Api.devicesList();
    devicesData = devices;
    renderDevicesGrid(devices);
    populateCameraDeviceSelect(devices);
  } catch (error) {
    console.error('Devices load error:', error);
    showToast('Ошибка загрузки устройств', 'error');
  }
}

function renderDevicesGrid(devices) {
  const grid = document.getElementById('devices-grid');
  if (!grid) return;

  if (!devices || devices.length === 0) {
    grid.innerHTML = '<div class="col-span-full text-center py-12 text-slate-400 text-sm">Нет устройств</div>';
    return;
  }

  const kindLabels = {
    NFC_READER: 'NFC Reader',
    CAMERA: 'Камера',
    TURNSTILE: 'Турникет',
    DOOR_LOCK: 'Электрозамок',
    BARRIER: 'Шлагбаум'
  };

  const kindIcons = {
    NFC_READER: '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"></path>',
    CAMERA: '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"></path>',
    DEFAULT: '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"></path>'
  };

  grid.innerHTML = devices.map(device => {
    const icon = kindIcons[device.kind] || kindIcons.DEFAULT;
    const label = kindLabels[device.kind] || device.kind || 'Устройство';

    return `
      <div class="device-card">
        <div class="flex items-start justify-between mb-4">
          <div class="w-11 h-11 bg-blue-100 rounded-xl flex items-center justify-center">
            <svg class="w-6 h-6 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">${icon}</svg>
          </div>
          <div class="flex items-center space-x-1">
            <button class="btn-icon text-slate-500 hover:bg-slate-100 edit-device-btn" data-device-id="${escapeHtml(device.id)}" title="Редактировать">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path>
              </svg>
            </button>
            <button class="btn-icon text-red-500 hover:bg-red-50 delete-device-btn" data-device-id="${escapeHtml(device.id)}" title="Удалить">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
              </svg>
            </button>
          </div>
        </div>
        <h4 class="font-semibold text-slate-900 mb-1 text-sm">${escapeHtml(device.id)}</h4>
        <p class="text-xs text-slate-500 mb-1">${escapeHtml(label)}</p>
        <p class="text-xs text-slate-400">${escapeHtml(device.location || 'Расположение не указано')}</p>
      </div>
    `;
  }).join('');

  grid.querySelectorAll('.edit-device-btn').forEach(btn => {
    btn.addEventListener('click', () => openEditDeviceModal(btn.dataset.deviceId));
  });
  grid.querySelectorAll('.delete-device-btn').forEach(btn => {
    btn.addEventListener('click', () => deleteDevice(btn.dataset.deviceId));
  });
}

function deleteDevice(deviceId) {
  if (!confirm(`Удалить устройство "${deviceId}"?`)) return;
  Api.deviceDelete(deviceId)
    .then(() => { showToast('Устройство удалено', 'success'); loadDevices(); })
    .catch(err => showToast(err.message || 'Ошибка удаления', 'error'));
}

// ==================== Edit Device Modal ====================
function openEditDeviceModal(deviceId) {
  const device = devicesData.find(d => d.id === deviceId);
  if (!device) return;

  document.getElementById('edit-device-id').value = device.id;
  const kindEl = document.getElementById('edit-d-kind');
  if (kindEl) kindEl.value = device.kind || '';
  const locEl = document.getElementById('edit-d-location');
  if (locEl) locEl.value = device.location || '';

  document.getElementById('edit-device-modal').classList.remove('hidden');
}

function initEditDeviceModal() {
  const modal = document.getElementById('edit-device-modal');
  if (!modal) return;

  document.getElementById('close-edit-device-modal')?.addEventListener('click', () => modal.classList.add('hidden'));
  document.getElementById('cancel-edit-device')?.addEventListener('click', () => modal.classList.add('hidden'));
  modal.addEventListener('click', (e) => { if (e.target === modal) modal.classList.add('hidden'); });

  document.getElementById('edit-device-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = document.getElementById('edit-device-id').value;
    const kind = document.getElementById('edit-d-kind')?.value?.trim();
    const location = document.getElementById('edit-d-location')?.value?.trim();

    try {
      showLoading('Сохранение...');
      await Api.deviceUpdate(id, { kind, location: location || null });
      hideLoading();
      modal.classList.add('hidden');
      showToast('Устройство обновлено', 'success');
      loadDevices();
    } catch (err) {
      hideLoading();
      showToast('Ошибка сохранения', 'error');
    }
  });
}

// ==================== Cameras ====================
async function loadCameras() {
  try {
    const cameras = await Api.camerasList();
    camerasData = cameras;
    renderCamerasGrid(cameras);
  } catch (error) {
    console.error('Cameras load error:', error);
    showToast('Ошибка загрузки камер', 'error');
  }
}

function renderCamerasGrid(cameras) {
  const grid = document.getElementById('cameras-grid');
  if (!grid) return;

  if (!cameras || cameras.length === 0) {
    grid.innerHTML = '<div class="col-span-full text-center py-12 text-slate-400 text-sm">Нет камер</div>';
    return;
  }

  grid.innerHTML = cameras.map(cam => `
    <div class="device-card">
      <div class="flex items-start justify-between mb-4">
        <div class="w-11 h-11 bg-violet-100 rounded-xl flex items-center justify-center">
          <svg class="w-6 h-6 text-violet-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"></path>
          </svg>
        </div>
        <div class="flex items-center space-x-1">
          <button class="btn-icon text-slate-500 hover:bg-slate-100 edit-camera-btn" data-camera-id="${escapeHtml(cam.id)}" title="Редактировать">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path>
            </svg>
          </button>
          <button class="btn-icon text-red-500 hover:bg-red-50 delete-camera-btn" data-camera-id="${escapeHtml(cam.id)}" title="Удалить">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
            </svg>
          </button>
        </div>
      </div>
      <h4 class="font-semibold text-slate-900 mb-1 text-sm">${escapeHtml(cam.name || cam.id)}</h4>
      <p class="text-xs text-slate-500 mb-1 font-mono">${escapeHtml(cam.id)}</p>
      ${cam.rtspUrl ? `<p class="text-xs text-slate-400 truncate" title="${escapeHtml(cam.rtspUrl)}">${escapeHtml(cam.rtspUrl)}</p>` : ''}
      <p class="text-xs text-slate-400">${escapeHtml(cam.location || 'Расположение не указано')}</p>
      ${cam.deviceId ? `<p class="text-xs text-blue-500 mt-1">Устройство: ${escapeHtml(cam.deviceId)}</p>` : ''}
    </div>
  `).join('');

  grid.querySelectorAll('.edit-camera-btn').forEach(btn => {
    btn.addEventListener('click', () => openEditCameraModal(btn.dataset.cameraId));
  });
  grid.querySelectorAll('.delete-camera-btn').forEach(btn => {
    btn.addEventListener('click', () => deleteCamera(btn.dataset.cameraId));
  });
}

function deleteCamera(cameraId) {
  if (!confirm(`Удалить камеру "${cameraId}"?`)) return;
  Api.cameraDelete(cameraId)
    .then(() => { showToast('Камера удалена', 'success'); loadCameras(); })
    .catch(err => showToast(err.message || 'Ошибка удаления', 'error'));
}

function populateCameraDeviceSelect(devices) {
  const select = document.getElementById('cam-device');
  if (!select) return;
  const current = select.value;
  select.innerHTML = '<option value="">— Не привязывать —</option>';
  (devices || []).forEach(d => {
    const opt = document.createElement('option');
    opt.value = d.id;
    opt.textContent = `${d.id} (${d.kind || ''})`;
    select.appendChild(opt);
  });
  select.value = current;
}

function initAddCameraModal() {
  const modal = document.getElementById('add-camera-modal');
  if (!modal) return;

  document.getElementById('add-camera-btn')?.addEventListener('click', () => {
    document.getElementById('add-camera-form')?.reset();
    populateCameraDeviceSelect(devicesData);
    modal.classList.remove('hidden');
  });
  document.getElementById('close-add-camera-modal')?.addEventListener('click', () => modal.classList.add('hidden'));
  document.getElementById('cancel-add-camera')?.addEventListener('click', () => modal.classList.add('hidden'));
  modal.addEventListener('click', (e) => { if (e.target === modal) modal.classList.add('hidden'); });

  document.getElementById('add-camera-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = document.getElementById('cam-id')?.value?.trim();
    const name = document.getElementById('cam-name')?.value?.trim();
    const rtspUrl = document.getElementById('cam-rtsp')?.value?.trim();
    const location = document.getElementById('cam-location')?.value?.trim();
    const deviceId = document.getElementById('cam-device')?.value || null;

    if (!id || !name) {
      showToast('ID и Название обязательны', 'error');
      return;
    }
    if (!/^[a-zA-Z0-9\-_]+$/.test(id)) {
      showToast('ID может содержать только латиницу, цифры, дефис, подчёркивание', 'error');
      return;
    }

    try {
      showLoading('Добавление камеры...');
      await Api.cameraCreate({ id, name, rtspUrl: rtspUrl || null, location: location || null, deviceId });
      hideLoading();
      modal.classList.add('hidden');
      showToast('Камера добавлена', 'success');
      loadCameras();
    } catch (err) {
      hideLoading();
      showToast(err.message || 'Ошибка добавления', 'error');
    }
  });
}

// ==================== Edit Camera Modal ====================
function openEditCameraModal(cameraId) {
  const cam = camerasData.find(c => c.id === cameraId);
  if (!cam) return;

  document.getElementById('edit-camera-id').value = cam.id;
  const nameEl = document.getElementById('edit-cam-name');
  const rtspEl = document.getElementById('edit-cam-rtsp');
  const locEl = document.getElementById('edit-cam-location');
  if (nameEl) nameEl.value = cam.name || '';
  if (rtspEl) rtspEl.value = cam.rtspUrl || '';
  if (locEl) locEl.value = cam.location || '';

  const deviceSel = document.getElementById('edit-cam-device');
  if (deviceSel) {
    deviceSel.innerHTML = '<option value="">— Не привязывать —</option>';
    devicesData.forEach(d => {
      const opt = document.createElement('option');
      opt.value = d.id;
      opt.textContent = `${d.id} (${d.kind || ''})`;
      if (cam.deviceId === d.id) opt.selected = true;
      deviceSel.appendChild(opt);
    });
  }

  document.getElementById('edit-camera-modal').classList.remove('hidden');
}

function initEditCameraModal() {
  const modal = document.getElementById('edit-camera-modal');
  if (!modal) return;

  document.getElementById('close-edit-camera-modal')?.addEventListener('click', () => modal.classList.add('hidden'));
  document.getElementById('cancel-edit-camera')?.addEventListener('click', () => modal.classList.add('hidden'));
  modal.addEventListener('click', (e) => { if (e.target === modal) modal.classList.add('hidden'); });

  document.getElementById('edit-camera-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = document.getElementById('edit-camera-id').value;
    const original = camerasData.find(c => c.id === id);
    const name = document.getElementById('edit-cam-name')?.value?.trim();
    const rtspUrl = document.getElementById('edit-cam-rtsp')?.value?.trim();
    const location = document.getElementById('edit-cam-location')?.value?.trim();
    const deviceId = document.getElementById('edit-cam-device')?.value || null;

    if (!name) {
      showToast('Название обязательно', 'error');
      return;
    }

    try {
      showLoading('Сохранение...');
      await Api.cameraUpdate(id, { name, rtspUrl: rtspUrl || null, location: location || null });
      if ((original?.deviceId || null) !== deviceId) {
        await Api.cameraReassignDevice(id, deviceId);
      }
      hideLoading();
      modal.classList.add('hidden');
      showToast('Камера обновлена', 'success');
      loadCameras();
    } catch (err) {
      hideLoading();
      showToast(err.message || 'Ошибка сохранения', 'error');
    }
  });
}

// ==================== Faces ====================
function initFaces() {
  document.getElementById('face-toggle-btn')?.addEventListener('click', async () => {
    const faceName = document.getElementById('face-name')?.value?.trim();
    const deviceInput = document.getElementById('face-device')?.value?.trim();
    const device = deviceInput || 'default';

    if (!faceName || !/^[а-яА-ЯёЁa-zA-Z\s\-']+$/.test(faceName)) {
      showToast('Введите корректное имя (буквы, пробелы, дефисы, апострофы)', 'error');
      return;
    }
    if (deviceInput && !/^[a-zA-Z0-9\-_\s]+$/.test(deviceInput)) {
      showToast('Некорректный ID устройства', 'error');
      return;
    }

    try {
      const result = await Api.faceToggle(faceName, device);
      showToast(`Проход отмечен: ${result.direction}`, 'success');
      loadDashboard();
    } catch (error) {
      showToast('Ошибка отметки прохода', 'error');
    }
  });
}

// ==================== Guests ====================
async function loadGuests() {
  try {
    const response = await Api.guestsPage({
      page: listState.guests.page,
      size: listState.guests.size,
      sort: listState.guests.sort,
      dir: listState.guests.dir,
      query: listState.guests.query || undefined
    });
    const pageData = normalizePageResponse(response);
    updateListState('guests', pageData);
    guestsData = pageData.content;
    renderGuestsTable(guestsData);
    updateListMeta('guests', 'гостей');
    renderPagination('guests', (page) => {
      listState.guests.page = page;
      loadGuests();
    });
  } catch (error) {
    console.error('Guests load error:', error);
    showToast('Ошибка загрузки гостей', 'error');
  }
}

function renderGuestsTable(guests) {
  const tbody = document.getElementById('guests-table-body');
  if (!tbody) return;

  if (!guests || guests.length === 0) {
    tbody.innerHTML = '<tr><td colspan="6" class="text-center py-10 text-slate-400 text-sm">Нет гостей</td></tr>';
    return;
  }

  tbody.innerHTML = guests.map(guest => {
    const guestPhotoSrc = asImageSrc(guest.photoBase64);
    const photoHtml = guestPhotoSrc
      ? `<img src="${escapeHtml(guestPhotoSrc)}" alt="" class="w-8 h-8 rounded-full object-cover flex-shrink-0" />`
      : `<div class="w-8 h-8 rounded-full bg-slate-200 flex items-center justify-center flex-shrink-0 text-xs font-bold text-slate-500">${escapeHtml((guest.lastName || '?')[0].toUpperCase())}</div>`;
    return `
    <tr class="hover:bg-slate-50 transition-colors">
      <td data-label="ФИО" class="font-medium text-slate-900 text-sm">
        <div class="flex items-center space-x-2">
          ${photoHtml}
          <div class="min-w-0 flex-1">
            <span>${escapeHtml([guest.lastName, guest.firstName].filter(Boolean).join(' '))}</span>
            <div class="sm:hidden mt-1 text-xs text-slate-500 leading-5">
              <div>${escapeHtml(guest.company || 'Компания не указана')}</div>
              <div>${escapeHtml(guest.phone || 'Телефон не указан')}</div>
              <div>${escapeHtml(guest.document || 'Документ не указан')}</div>
            </div>
          </div>
        </div>
      </td>
      <td data-label="Компания" class="hidden md:table-cell text-slate-600 text-sm">${escapeHtml(guest.company || '—')}</td>
      <td data-label="Телефон" class="hidden sm:table-cell text-slate-600 text-sm">${escapeHtml(guest.phone || '—')}</td>
      <td data-label="Документ" class="hidden lg:table-cell text-slate-500 text-sm">${escapeHtml(guest.document || '—')}</td>
      <td data-label="Дата регистрации" class="hidden md:table-cell text-slate-500 text-sm">${escapeHtml(guest.createdAt ? formatDate(guest.createdAt) : '—')}</td>
      <td class="text-right table-actions">
        <div class="flex items-center justify-end space-x-1">
          <button class="btn-icon text-slate-700 hover:bg-slate-100 guest-qr-btn" data-guest-id="${escapeHtml(guest.id)}" title="QR визита">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM15 15h1m2 0h2m-5 3h3m2 0h1"/>
            </svg>
          </button>
          <button class="btn-icon text-blue-600 hover:bg-blue-50 edit-guest-btn" data-guest-id="${escapeHtml(guest.id)}" title="Редактировать">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path>
            </svg>
          </button>
          <button class="btn-icon text-red-600 hover:bg-red-50 delete-guest-btn" data-guest-id="${escapeHtml(guest.id)}" title="Удалить">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
            </svg>
          </button>
        </div>
      </td>
    </tr>
  `}).join('');

  tbody.querySelectorAll('.edit-guest-btn').forEach(btn => {
    btn.addEventListener('click', () => openEditGuestModal(btn.dataset.guestId));
  });
  tbody.querySelectorAll('.delete-guest-btn').forEach(btn => {
    btn.addEventListener('click', () => deleteGuest(btn.dataset.guestId));
  });
  tbody.querySelectorAll('.guest-qr-btn').forEach(btn => {
    btn.addEventListener('click', () => openGuestVisitQr(btn.dataset.guestId));
  });
}

async function openGuestVisitQr(guestId) {
  try {
    const visits = await Api.guestVisitsByGuest(guestId);
    if (!Array.isArray(visits) || visits.length === 0) {
      showToast('У гостя нет визитов. Сначала запланируйте визит.', 'warning');
      openGuestVisitCreateModal(guestId);
      return;
    }

    const preferred = visits.find(v => (v.status || '').toUpperCase() === 'ACTIVE')
      || visits.find(v => (v.status || '').toUpperCase() === 'PLANNED')
      || visits[0];

    if (!preferred?.id) {
      showToast('Не удалось определить визит для QR', 'error');
      return;
    }

    let detailedVisit = preferred;
    try {
      detailedVisit = await Api.guestVisitGet(preferred.id);
    } catch (_) {
      // fallback to already loaded visit when details endpoint is unavailable
    }
    openGuestVisitQrModal(detailedVisit);
  } catch (error) {
    showToast(error.message || 'Ошибка получения QR визита', 'error');
  }
}

function openGuestVisitQrModal(visit) {
  const image = document.getElementById('guest-visit-qr-image');
  const guest = document.getElementById('guest-visit-qr-guest');
  const host = document.getElementById('guest-visit-qr-host');
  const period = document.getElementById('guest-visit-qr-period');
  const status = document.getElementById('guest-visit-qr-status');
  const modal = document.getElementById('guest-visit-qr-modal');
  if (!image || !guest || !host || !period || !status || !modal) return;

  const guestName = resolveVisitGuestName(visit);
  const hostName = resolveVisitHostName(visit);
  const periodText = visit?.plannedFrom && visit?.plannedTo
    ? `${formatDateTime(visit.plannedFrom)} - ${formatDateTime(visit.plannedTo)}`
    : '—';

  image.src = Api.guestVisitQrUrl(visit.id);
  guest.textContent = guestName;
  host.textContent = hostName;
  period.textContent = periodText;
  status.textContent = localizeVisitStatus(visit?.status || 'PLANNED');
  modal.classList.remove('hidden');
}

function localizeVisitStatus(status) {
  const normalized = String(status || '').toUpperCase();
  if (normalized === 'PLANNED') return 'Ожидается';
  if (normalized === 'ACTIVE') return 'В процессе';
  if (normalized === 'FINISHED') return 'Завершен';
  if (normalized === 'CANCELLED') return 'Отменен';
  return status || '—';
}

function getVisitGuestId(visit) {
  return visit?.guest?.id || visit?.guestId || null;
}

function getVisitHostId(visit) {
  return visit?.host?.id || visit?.hostPersonId || null;
}

function resolveVisitGuestName(visit) {
  const direct = visit?.guest?.fullName || [visit?.guest?.lastName, visit?.guest?.firstName, visit?.guest?.middleName].filter(Boolean).join(' ').trim();
  if (direct) return direct;

  const guestId = getVisitGuestId(visit);
  if (guestId && Array.isArray(guestsData)) {
    const g = guestsData.find(x => x.id === guestId);
    if (g) {
      return g.fullName || [g.lastName, g.firstName, g.middleName].filter(Boolean).join(' ').trim() || '—';
    }
  }
  return '—';
}

function resolveVisitHostName(visit) {
  const direct = visit?.host?.fullName || [visit?.host?.lastName, visit?.host?.firstName, visit?.host?.middleName].filter(Boolean).join(' ').trim();
  if (direct) return direct;

  const hostId = getVisitHostId(visit);
  if (hostId && Array.isArray(personnelData)) {
    const p = personnelData.find(x => x.id === hostId);
    if (p) {
      return p.fullName || [p.lastName, p.firstName, p.middleName].filter(Boolean).join(' ').trim() || '—';
    }
  }
  return '—';
}

function closeGuestVisitQrModal() {
  const modal = document.getElementById('guest-visit-qr-modal');
  const image = document.getElementById('guest-visit-qr-image');
  if (image) image.removeAttribute('src');
  modal?.classList.add('hidden');
}

function localDateTimeToIso(value) {
  if (!value) return null;
  const dt = new Date(value);
  if (Number.isNaN(dt.getTime())) return null;
  return dt.toISOString();
}

function setDefaultGuestVisitPeriod() {
  const fromEl = document.getElementById('guest-visit-create-from');
  const toEl = document.getElementById('guest-visit-create-to');
  if (!fromEl || !toEl) return;

  const now = new Date();
  now.setMinutes(0, 0, 0);
  const plusHour = new Date(now.getTime() + 60 * 60 * 1000);
  const toInput = (d) => {
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    const hh = String(d.getHours()).padStart(2, '0');
    const mi = String(d.getMinutes()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}T${hh}:${mi}`;
  };

  fromEl.value = toInput(now);
  toEl.value = toInput(plusHour);
}

function populateGuestVisitCreateOptions(preselectedGuestId = null) {
  const guestSelect = document.getElementById('guest-visit-create-guest');
  const hostSelect = document.getElementById('guest-visit-create-host');
  if (!guestSelect || !hostSelect) return;

  guestSelect.innerHTML = '<option value="">— Выберите гостя —</option>';
  guestsData.forEach(g => {
    const option = document.createElement('option');
    option.value = g.id;
    option.textContent = [g.lastName, g.firstName, g.middleName].filter(Boolean).join(' ');
    if (preselectedGuestId && g.id === preselectedGuestId) option.selected = true;
    guestSelect.appendChild(option);
  });

  hostSelect.innerHTML = '<option value="">— Выберите сотрудника —</option>';
  personnelData.forEach(p => {
    const option = document.createElement('option');
    option.value = p.id;
    option.textContent = p.fullName || [p.lastName, p.firstName, p.middleName].filter(Boolean).join(' ');
    hostSelect.appendChild(option);
  });
}

function openGuestVisitCreateModal(preselectedGuestId = null) {
  populateGuestVisitCreateOptions(preselectedGuestId);
  setDefaultGuestVisitPeriod();
  const reason = document.getElementById('guest-visit-create-reason');
  if (reason) reason.value = '';
  document.getElementById('guest-visit-create-modal')?.classList.remove('hidden');
}

function closeGuestVisitCreateModal() {
  document.getElementById('guest-visit-create-modal')?.classList.add('hidden');
}

function initGuestVisitCreateModal() {
  const modal = document.getElementById('guest-visit-create-modal');
  if (!modal) return;

  document.getElementById('guest-visit-create-btn')?.addEventListener('click', () => openGuestVisitCreateModal());
  document.getElementById('close-guest-visit-create-modal')?.addEventListener('click', closeGuestVisitCreateModal);
  document.getElementById('cancel-guest-visit-create-modal')?.addEventListener('click', closeGuestVisitCreateModal);
  modal.addEventListener('click', (e) => {
    if (e.target === modal) closeGuestVisitCreateModal();
  });

  document.getElementById('guest-visit-create-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const guestId = document.getElementById('guest-visit-create-guest')?.value || '';
    const hostPersonId = document.getElementById('guest-visit-create-host')?.value || '';
    const plannedFromInput = document.getElementById('guest-visit-create-from')?.value || '';
    const plannedToInput = document.getElementById('guest-visit-create-to')?.value || '';
    const reason = document.getElementById('guest-visit-create-reason')?.value?.trim() || null;

    if (!guestId || !hostPersonId || !plannedFromInput || !plannedToInput) {
      showToast('Заполните обязательные поля визита', 'error');
      return;
    }

    const plannedFrom = localDateTimeToIso(plannedFromInput);
    const plannedTo = localDateTimeToIso(plannedToInput);
    if (!plannedFrom || !plannedTo) {
      showToast('Некорректные дата/время визита', 'error');
      return;
    }

    try {
      showLoading('Создание визита...');
      await Api.guestVisitCreate({ guestId, hostPersonId, plannedFrom, plannedTo, reason });
      hideLoading();
      closeGuestVisitCreateModal();
      showToast('Визит запланирован', 'success');
      await loadGuestVisitsToday();
    } catch (error) {
      hideLoading();
      showToast(error.message || 'Ошибка создания визита', 'error');
    }
  });
}

function deleteGuest(guestId) {
  if (!confirm('Удалить этого гостя?')) return;
  Api.guestDelete(guestId)
    .then(() => { showToast('Гость удалён', 'success'); loadGuests(); })
    .catch(err => showToast(err.message || 'Ошибка удаления', 'error'));
}

// ==================== Edit Guest Modal ====================
function openEditGuestModal(guestId) {
  const guest = guestsData.find(g => g.id === guestId);
  if (!guest) return;

  document.getElementById('edit-guest-id').value = guest.id;
  document.getElementById('edit-g-lastName').value = guest.lastName || '';
  document.getElementById('edit-g-firstName').value = guest.firstName || '';
  document.getElementById('edit-g-middleName').value = guest.middleName || '';
  document.getElementById('edit-g-phone').value = guest.phone || '';
  document.getElementById('edit-g-company').value = guest.company || '';
  document.getElementById('edit-g-document').value = guest.document || '';
  document.getElementById('edit-g-notes').value = guest.notes || '';

  // Photo preview
  const photoPreview = document.getElementById('edit-g-photo-preview');
  if (photoPreview) {
    const guestPhotoSrc = asImageSrc(guest.photoBase64);
    if (guestPhotoSrc) {
      photoPreview.innerHTML = `<img src="${escapeHtml(guestPhotoSrc)}" alt="" class="w-full h-full object-cover" />`;
    } else {
      photoPreview.innerHTML = `<svg class="w-8 h-8 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/></svg>`;
    }
  }
  // Reset file input
  const photoInput = document.getElementById('edit-g-photo');
  if (photoInput) photoInput.value = '';

  document.getElementById('edit-guest-modal').classList.remove('hidden');
}

function initEditGuestModal() {
  const modal = document.getElementById('edit-guest-modal');
  if (!modal) return;

  document.getElementById('close-edit-guest-modal')?.addEventListener('click', () => modal.classList.add('hidden'));
  document.getElementById('cancel-edit-guest')?.addEventListener('click', () => modal.classList.add('hidden'));
  modal.addEventListener('click', (e) => { if (e.target === modal) modal.classList.add('hidden'); });

  let pendingPhotoBase64 = null;
  document.getElementById('edit-g-photo')?.addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (ev) => {
      pendingPhotoBase64 = ev.target.result;
      const preview = document.getElementById('edit-g-photo-preview');
      if (preview) preview.innerHTML = `<img src="${escapeHtml(pendingPhotoBase64)}" alt="" class="w-full h-full object-cover" />`;
    };
    reader.readAsDataURL(file);
  });

  document.getElementById('capture-edit-guest-photo-btn')?.addEventListener('click', async () => {
    try {
      pendingPhotoBase64 = await capturePhotoFromCamera();
      const preview = document.getElementById('edit-g-photo-preview');
      if (preview) preview.innerHTML = `<img src="${escapeHtml(pendingPhotoBase64)}" alt="" class="w-full h-full object-cover" />`;
      showToast('Фото получено с камеры', 'success');
    } catch (error) {
      showToast(error.message || 'Камера недоступна', 'error');
    }
  });

  document.getElementById('edit-guest-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = document.getElementById('edit-guest-id').value;
    const lastName = document.getElementById('edit-g-lastName').value.trim();
    const firstName = document.getElementById('edit-g-firstName').value.trim();
    const middleName = document.getElementById('edit-g-middleName').value.trim();
    const phone = document.getElementById('edit-g-phone').value.trim();
    const company = document.getElementById('edit-g-company').value.trim();
    const document_ = document.getElementById('edit-g-document').value.trim();
    const notes = document.getElementById('edit-g-notes').value.trim();

    if (!lastName || !firstName) {
      showToast('Фамилия и Имя обязательны', 'error');
      return;
    }

    try {
      showLoading('Сохранение...');
      const payload = {
        lastName,
        firstName,
        middleName: middleName || null,
        phone: phone || null,
        company: company || null,
        document: document_ || null,
        notes: notes || null
      };
      if (pendingPhotoBase64) payload.photoBase64 = pendingPhotoBase64;

      await Api.guestUpdate(id, payload);
      pendingPhotoBase64 = null;
      hideLoading();
      modal.classList.add('hidden');
      showToast('Данные гостя обновлены', 'success');
      loadGuests();
    } catch (err) {
      hideLoading();
      showToast('Ошибка сохранения', 'error');
    }
  });
}

// ==================== Guest Search ====================
function initGuestSearch() {
  document.getElementById('search-guests')?.addEventListener('input', (e) => {
    listState.guests.query = e.target.value.trim();
    listState.guests.page = 0;
    scheduleListReload(() => loadGuests());
  });
}

// ==================== Positions ====================
async function loadPositions() {
  try {
    const positions = await Api.positionsList();
    positionsData = positions;
    renderPositionsTable(positions);
    updatePositionDropdowns(positions);
  } catch (error) {
    console.error('Positions load error:', error);
    showToast('Ошибка загрузки должностей', 'error');
  }
}

function renderPositionsTable(positions) {
  const tbody = document.getElementById('positions-table-body');
  if (!tbody) return;

  if (!positions || positions.length === 0) {
    tbody.innerHTML = '<tr><td colspan="3" class="text-center py-10 text-slate-400 text-sm">Нет должностей</td></tr>';
    return;
  }

  tbody.innerHTML = positions.map(pos => `
    <tr class="hover:bg-slate-50 transition-colors">
      <td data-label="Название" class="font-medium text-slate-900 text-sm">${escapeHtml(pos.name)}</td>
      <td data-label="Уровень" class="hidden sm:table-cell">
        <span class="badge bg-slate-100 text-slate-600">${escapeHtml(String(pos.accessLevel ?? 0))}</span>
      </td>
      <td class="text-right">
        <div class="flex items-center justify-end space-x-1">
          <button class="btn-icon text-blue-600 hover:bg-blue-50 edit-position-btn" data-position-id="${escapeHtml(pos.id)}" title="Редактировать">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path>
            </svg>
          </button>
          <button class="btn-icon text-red-600 hover:bg-red-50 delete-position-btn" data-position-id="${escapeHtml(pos.id)}" title="Удалить">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
            </svg>
          </button>
        </div>
      </td>
    </tr>
  `).join('');

  tbody.querySelectorAll('.edit-position-btn').forEach(btn => {
    btn.addEventListener('click', () => openEditPositionModal(btn.dataset.positionId));
  });
  tbody.querySelectorAll('.delete-position-btn').forEach(btn => {
    btn.addEventListener('click', () => deletePosition(btn.dataset.positionId));
  });
}

function updatePositionDropdowns(positions) {
  ['position-select', 'edit-p-positionId'].forEach(selectId => {
    const select = document.getElementById(selectId);
    if (!select) return;
    const currentValue = select.value;
    select.innerHTML = '<option value="">— Не указана —</option>';
    (positions || []).forEach(pos => {
      const opt = document.createElement('option');
      opt.value = pos.id;
      opt.textContent = pos.name;
      select.appendChild(opt);
    });
    select.value = currentValue;
  });
}

async function deletePosition(positionId) {
  if (!confirm('Удалить эту должность?')) return;
  try {
    await Api.positionDelete(positionId);
    showToast('Должность удалена', 'success');
    loadPositions();
  } catch (error) {
    showToast(error.message || 'Ошибка удаления должности', 'error');
  }
}

function initAddPositionModal() {
  const modal = document.getElementById('add-position-modal');
  if (!modal) return;

  document.getElementById('add-position-btn')?.addEventListener('click', () => {
    document.getElementById('add-position-form')?.reset();
    modal.classList.remove('hidden');
  });
  document.getElementById('close-add-position-modal')?.addEventListener('click', () => modal.classList.add('hidden'));
  document.getElementById('cancel-add-position')?.addEventListener('click', () => modal.classList.add('hidden'));
  modal.addEventListener('click', (e) => { if (e.target === modal) modal.classList.add('hidden'); });

  document.getElementById('add-position-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = document.getElementById('pos-name')?.value?.trim();
    const accessLevel = parseInt(document.getElementById('pos-access')?.value || '0');

    if (!name) {
      showToast('Название должности обязательно', 'error');
      return;
    }

    try {
      showLoading('Создание должности...');
      await Api.positionCreate({ name, accessLevel });
      hideLoading();
      modal.classList.add('hidden');
      showToast('Должность создана', 'success');
      loadPositions();
    } catch (err) {
      hideLoading();
      showToast(err.message || 'Ошибка создания', 'error');
    }
  });
}

// ==================== Edit Position Modal ====================
function openEditPositionModal(positionId) {
  const pos = positionsData.find(p => p.id === positionId);
  if (!pos) return;

  document.getElementById('edit-position-id').value = pos.id;
  document.getElementById('edit-pos-name').value = pos.name || '';
  document.getElementById('edit-pos-access').value = pos.accessLevel ?? 0;

  document.getElementById('edit-position-modal').classList.remove('hidden');
}

function initEditPositionModal() {
  const modal = document.getElementById('edit-position-modal');
  if (!modal) return;

  document.getElementById('close-edit-position-modal')?.addEventListener('click', () => modal.classList.add('hidden'));
  document.getElementById('cancel-edit-position')?.addEventListener('click', () => modal.classList.add('hidden'));
  modal.addEventListener('click', (e) => { if (e.target === modal) modal.classList.add('hidden'); });

  document.getElementById('edit-position-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = document.getElementById('edit-position-id').value;
    const name = document.getElementById('edit-pos-name').value.trim();
    const accessLevel = parseInt(document.getElementById('edit-pos-access').value || '0');

    if (!name) {
      showToast('Название должности обязательно', 'error');
      return;
    }

    try {
      showLoading('Сохранение...');
      await Api.positionUpdate(id, { name, accessLevel });
      hideLoading();
      modal.classList.add('hidden');
      showToast('Должность обновлена', 'success');
      loadPositions();
    } catch (err) {
      hideLoading();
      showToast(err.message || 'Ошибка сохранения', 'error');
    }
  });
}

// ==================== Integrations ====================
function initIntegrations() {
  // Load saved integration settings from localStorage
  const dahuaHost = localStorage.getItem('dahua_host') || '';
  const dahuaPort = localStorage.getItem('dahua_port') || '80';
  const dahuaUser = localStorage.getItem('dahua_user') || '';
  const webhookUrl = localStorage.getItem('webhook_url') || '';
  const webhookMethod = localStorage.getItem('webhook_method') || 'POST';
  const webhookAuth = localStorage.getItem('webhook_auth') || '';

  const hostEl = document.getElementById('dahua-host');
  const portEl = document.getElementById('dahua-port');
  const userEl = document.getElementById('dahua-user');
  if (hostEl) hostEl.value = dahuaHost;
  if (portEl) portEl.value = dahuaPort;
  if (userEl) userEl.value = dahuaUser;

  const webhookUrlEl = document.getElementById('webhook-url');
  const webhookMethodEl = document.getElementById('webhook-method');
  const webhookAuthEl = document.getElementById('webhook-auth');
  if (webhookUrlEl) webhookUrlEl.value = webhookUrl;
  if (webhookMethodEl) webhookMethodEl.value = webhookMethod;
  if (webhookAuthEl) webhookAuthEl.value = webhookAuth;

  updateIntegrationBadges();

  // Dahua save
  document.getElementById('dahua-save-btn')?.addEventListener('click', () => {
    const host = document.getElementById('dahua-host')?.value?.trim();
    const port = document.getElementById('dahua-port')?.value?.trim() || '80';
    const user = document.getElementById('dahua-user')?.value?.trim();
    const pass = document.getElementById('dahua-pass')?.value;

    if (!host) { showToast('Введите IP-адрес устройства', 'error'); return; }

    localStorage.setItem('dahua_host', host);
    localStorage.setItem('dahua_port', port);
    localStorage.setItem('dahua_user', user);
    if (pass) localStorage.setItem('dahua_pass_hint', '***saved***');

    updateIntegrationBadges();
    showToast('Настройки Dahua сохранены', 'success');
  });

  // Dahua test
  document.getElementById('dahua-test-btn')?.addEventListener('click', async () => {
    const host = document.getElementById('dahua-host')?.value?.trim();
    const port = document.getElementById('dahua-port')?.value?.trim() || '80';
    const user = document.getElementById('dahua-user')?.value?.trim();
    const pass = document.getElementById('dahua-pass')?.value;

    if (!host) { showToast('Введите IP-адрес устройства', 'error'); return; }

    const resultEl = document.getElementById('dahua-test-result');

    try {
      showLoading('Проверка соединения...');
      const result = await Api.request('/integrations/dahua/test', {
        method: 'POST',
        body: JSON.stringify({ host, port: parseInt(port), username: user, password: pass })
      }).catch(e => { throw e; });
      hideLoading();
      if (resultEl) {
        resultEl.className = 'mt-3 text-sm text-emerald-600 font-medium';
        resultEl.textContent = '✓ Соединение успешно установлено';
        resultEl.classList.remove('hidden');
      }
    } catch (err) {
      hideLoading();
      if (resultEl) {
        resultEl.className = 'mt-3 text-sm text-red-600 font-medium';
        resultEl.textContent = `✗ Ошибка: ${err.message || 'Не удалось подключиться'}`;
        resultEl.classList.remove('hidden');
      }
    }
  });

  // Webhook save
  document.getElementById('webhook-save-btn')?.addEventListener('click', () => {
    const url = document.getElementById('webhook-url')?.value?.trim();
    const method = document.getElementById('webhook-method')?.value || 'POST';
    const auth = document.getElementById('webhook-auth')?.value?.trim();

    if (!url) { showToast('Введите URL устройства', 'error'); return; }

    localStorage.setItem('webhook_url', url);
    localStorage.setItem('webhook_method', method);
    localStorage.setItem('webhook_auth', auth);

    updateIntegrationBadges();
    showToast('Настройки Webhook сохранены', 'success');
  });

  // Webhook test
  document.getElementById('webhook-test-btn')?.addEventListener('click', async () => {
    const url = document.getElementById('webhook-url')?.value?.trim();
    const method = document.getElementById('webhook-method')?.value || 'POST';
    const auth = document.getElementById('webhook-auth')?.value?.trim();

    if (!url) { showToast('Введите URL устройства', 'error'); return; }

    const resultEl = document.getElementById('webhook-test-result');

    try {
      showLoading('Отправка тест-запроса...');
      const result = await Api.request('/integrations/webhook/test', {
        method: 'POST',
        body: JSON.stringify({ url, method, authHeader: auth })
      }).catch(e => { throw e; });
      hideLoading();
      if (resultEl) {
        resultEl.className = 'mt-3 text-sm text-emerald-600 font-medium';
        resultEl.textContent = `✓ Запрос отправлен${result?.status ? `, статус: ${result.status}` : ''}`;
        resultEl.classList.remove('hidden');
      }
    } catch (err) {
      hideLoading();
      if (resultEl) {
        resultEl.className = 'mt-3 text-sm text-red-600 font-medium';
        resultEl.textContent = `✗ Ошибка: ${err.message || 'Не удалось отправить запрос'}`;
        resultEl.classList.remove('hidden');
      }
    }
  });
}

function updateIntegrationBadges() {
  const dahuaHost = localStorage.getItem('dahua_host') || '';
  const webhookUrl = localStorage.getItem('webhook_url') || '';

  const dahuaBadge = document.getElementById('dahua-status-badge');
  if (dahuaBadge) {
    if (dahuaHost) {
      dahuaBadge.className = 'badge bg-emerald-100 text-emerald-700';
      dahuaBadge.textContent = 'Настроено';
    } else {
      dahuaBadge.className = 'badge bg-slate-100 text-slate-500';
      dahuaBadge.textContent = 'Не настроено';
    }
  }

  const webhookBadge = document.getElementById('webhook-status-badge');
  if (webhookBadge) {
    if (webhookUrl) {
      webhookBadge.className = 'badge bg-emerald-100 text-emerald-700';
      webhookBadge.textContent = 'Настроено';
    } else {
      webhookBadge.className = 'badge bg-slate-100 text-slate-500';
      webhookBadge.textContent = 'Не настроено';
    }
  }
}

// ==================== User Menu ====================
function initUserMenu() {
  const username = localStorage.getItem('username') || 'User';

  ['username-display', 'username-display-mobile'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.textContent = username;
  });
  ['user-avatar', 'user-avatar-mobile'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.textContent = username.charAt(0).toUpperCase();
  });

  const logoutFn = async () => {
    try { await Api.logout(); } catch (_) {}
    ['username', 'email', 'roleId', 'isAdmin'].forEach(k => localStorage.removeItem(k));
    window.location.href = '/login.html';
  };

  ['logout-btn', 'logout-btn-mobile', 'profile-logout'].forEach(id => {
    document.getElementById(id)?.addEventListener('click', logoutFn);
  });

  document.getElementById('profile-refresh')?.addEventListener('click', loadProfile);
}

// ==================== Role-Based UI ====================
function initRoleBasedUI() {
  if (!isAdmin) {
    const adminOnly = ['personnel', 'devices', 'cameras', 'faces', 'guests', 'positions', 'integrations'];
    adminOnly.forEach(section => {
      document.querySelectorAll(`[data-section="${section}"]`).forEach(item => item.classList.add('hidden'));
    });

    document.querySelectorAll('.section-content').forEach(section => {
      if (section.id !== 'dashboard' && section.id !== 'events' && section.id !== 'profile') {
        section.classList.add('hidden');
      }
    });
  }
}

// ==================== Personnel Wizard ====================
let currentWizardStep = 1;
let wizardData = { lastName: '', firstName: '', middleName: '', phone: '', dateOfBirth: '', positionId: null, photo: null, photoBase64: null, cardUid: null };
let nfcScanPollTimer = null;
let nfcScanPollDeadline = 0;

function initWizard() {
  const modal = document.getElementById('add-personnel-modal');
  if (!modal) return;

  document.getElementById('add-personnel-btn')?.addEventListener('click', async () => {
    resetWizard();
    await ensurePositionsLoaded();
    updatePositionDropdowns(positionsData);
    modal.classList.remove('hidden');
    showWizardStep(1);
  });

  document.getElementById('next-step-btn')?.addEventListener('click', nextWizardStep);
  document.getElementById('prev-step-btn')?.addEventListener('click', prevWizardStep);
  document.getElementById('finish-wizard-btn')?.addEventListener('click', finishWizard);
  document.getElementById('close-wizard-modal')?.addEventListener('click', () => { modal.classList.add('hidden'); resetWizard(); });
  modal.addEventListener('click', (e) => { if (e.target === modal) { modal.classList.add('hidden'); resetWizard(); } });

  document.getElementById('upload-photo-btn')?.addEventListener('click', () => {
    document.getElementById('personnel-photo')?.click();
  });
  document.getElementById('capture-personnel-photo-btn')?.addEventListener('click', async () => {
    try {
      const dataUrl = await capturePhotoFromCamera();
      wizardData.photoBase64 = dataUrl;
      const previewImg = document.getElementById('photo-preview-img');
      const placeholderIcon = document.getElementById('photo-placeholder-icon');
      const container = document.getElementById('photo-preview-container');
      if (previewImg) { previewImg.src = dataUrl; previewImg.classList.remove('hidden'); }
      if (placeholderIcon) placeholderIcon.classList.add('hidden');
      if (container) container.classList.add('has-image');
      showToast('Фото получено с камеры', 'success');
    } catch (error) {
      showToast(error.message || 'Камера недоступна', 'error');
    }
  });
  document.getElementById('personnel-photo')?.addEventListener('change', handlePhotoUpload);
  document.getElementById('register-card-btn')?.addEventListener('click', handleCardRegistration);
  document.getElementById('reset-card-btn')?.addEventListener('click', resetCardSelection);
}

function showWizardStep(step) {
  document.querySelectorAll('.wizard-step-content').forEach(el => el.classList.add('hidden'));
  const active = document.querySelector(`.wizard-step-content[data-step="${step}"]`);
  if (active) active.classList.remove('hidden');

  for (let i = 1; i <= 3; i++) {
    const node = document.getElementById(`wizard-step-node-${i}`);
    if (!node) continue;
    node.classList.remove('active', 'completed');
    if (i < step) {
      node.classList.add('completed');
      node.innerHTML = '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>';
    } else if (i === step) {
      node.classList.add('active');
      node.textContent = i;
    } else {
      node.textContent = i;
    }
  }

  document.getElementById('prev-step-btn')?.classList.toggle('hidden', step === 1);
  document.getElementById('next-step-btn')?.classList.toggle('hidden', step === 3);
  document.getElementById('finish-wizard-btn')?.classList.toggle('hidden', step !== 3);
}

function nextWizardStep() {
  if (currentWizardStep < 3 && validateWizardStep(currentWizardStep)) {
    currentWizardStep++;
    showWizardStep(currentWizardStep);
  }
}

function prevWizardStep() {
  if (currentWizardStep > 1) {
    currentWizardStep--;
    showWizardStep(currentWizardStep);
  }
}

function validateWizardStep(step) {
  if (step === 1) {
    const form = document.getElementById('personnel-step1-form');
    if (!form) return true;
    clearErrors(form);
    const lastName = form.lastName?.value?.trim();
    const firstName = form.firstName?.value?.trim();
    let ok = true;
    if (!lastName) { showFieldError(form.lastName, 'Фамилия обязательна'); ok = false; }
    if (!firstName) { showFieldError(form.firstName, 'Имя обязательно'); ok = false; }
    return ok;
  }
  return true; // Steps 2 and 3 are optional
}

function handlePhotoUpload(event) {
  const file = event.target.files[0];
  if (!file) return;
  if (!file.type.startsWith('image/')) { showToast('Выберите изображение', 'error'); return; }
  if (file.size > 5 * 1024 * 1024) { showToast('Файл не должен превышать 5MB', 'error'); return; }

  wizardData.photo = file;
  const reader = new FileReader();
  reader.onload = (e) => {
    wizardData.photoBase64 = e.target.result;
    const previewImg = document.getElementById('photo-preview-img');
    const placeholderIcon = document.getElementById('photo-placeholder-icon');
    const container = document.getElementById('photo-preview-container');
    if (previewImg) { previewImg.src = e.target.result; previewImg.classList.remove('hidden'); }
    if (placeholderIcon) placeholderIcon.classList.add('hidden');
    if (container) container.classList.add('has-image');
  };
  reader.readAsDataURL(file);
  showToast('Фото выбрано', 'success');
}

async function handleCardRegistration() {
  const btn = document.getElementById('register-card-btn');
  const btnText = document.getElementById('card-btn-text');
  const iconDefault = document.getElementById('card-btn-icon-default');
  const iconLoading = document.getElementById('card-btn-icon-loading');

  if (btn) btn.disabled = true;
  if (iconDefault) iconDefault.classList.add('hidden');
  if (iconLoading) iconLoading.classList.remove('hidden');
  if (btnText) btnText.textContent = 'Ожидание карты...';

  try {
    await Api.cardRegistrationStart(45);
  } catch (_) {
    restoreCardScanButtonIdle();
    showToast('Не удалось открыть окно регистрации карты', 'error');
    return;
  }

  startNfcScanPolling();

  if (window.Android?.scanNFC) {
    window.Android.scanNFC();
  } else {
    showToast('Ожидание UID от Home Assistant...', 'info');
  }
}

function stopNfcScanPolling() {
  if (nfcScanPollTimer) {
    clearInterval(nfcScanPollTimer);
    nfcScanPollTimer = null;
  }
  nfcScanPollDeadline = 0;
}

function stopCardRegistrationWindowSilently() {
  Api.cardRegistrationStop().catch(() => {});
}

function restoreCardScanButtonIdle() {
  const btn = document.getElementById('register-card-btn');
  const btnText = document.getElementById('card-btn-text');
  const iconDefault = document.getElementById('card-btn-icon-default');
  const iconLoading = document.getElementById('card-btn-icon-loading');

  if (btn) btn.disabled = false;
  if (iconDefault) iconDefault.classList.remove('hidden');
  if (iconLoading) iconLoading.classList.add('hidden');
  if (btnText) btnText.textContent = wizardData.cardUid ? 'Пересканировать карту' : 'Сканировать карту';
}

function resetCardSelection() {
  stopNfcScanPolling();
  stopCardRegistrationWindowSilently();
  wizardData.cardUid = null;

  const statusDiv = document.getElementById('card-registration-status');
  const uidSpan = document.getElementById('registered-card-uid');
  const btn = document.getElementById('register-card-btn');
  const btnText = document.getElementById('card-btn-text');
  const iconDefault = document.getElementById('card-btn-icon-default');
  const iconLoading = document.getElementById('card-btn-icon-loading');

  if (statusDiv) statusDiv.classList.add('hidden');
  if (uidSpan) uidSpan.textContent = '';
  if (btn) {
    btn.disabled = false;
    btn.classList.remove('btn-success');
  }
  if (btnText) btnText.textContent = 'Сканировать карту';
  if (iconDefault) iconDefault.classList.remove('hidden');
  if (iconLoading) iconLoading.classList.add('hidden');
  showToast('Выберите карту повторно', 'info');
}

function startNfcScanPolling(timeoutMs = 45000) {
  stopNfcScanPolling();
  nfcScanPollDeadline = Date.now() + timeoutMs;

  nfcScanPollTimer = setInterval(async () => {
    if (Date.now() > nfcScanPollDeadline) {
      stopNfcScanPolling();
      stopCardRegistrationWindowSilently();
      restoreCardScanButtonIdle();
      showToast('Карта не получена. Проверьте automation в Home Assistant.', 'warning');
      return;
    }

    try {
      const payload = await Api.cardScanLatest();
      if (!payload || payload.status === 'idle' || !payload.uid) {
        return;
      }
      window.onNFCCardScanned(payload.uid);
    } catch (_) {
      // keep polling within timeout window
    }
  }, 1000);
}

window.onNFCCardScanned = function(uid) {
  const btn = document.getElementById('register-card-btn');
  const statusDiv = document.getElementById('card-registration-status');
  const uidSpan = document.getElementById('registered-card-uid');
  const btnText = document.getElementById('card-btn-text');
  const iconDefault = document.getElementById('card-btn-icon-default');
  const iconLoading = document.getElementById('card-btn-icon-loading');

  stopNfcScanPolling();
  stopCardRegistrationWindowSilently();

  wizardData.cardUid = uid;
  if (uidSpan) uidSpan.textContent = uid;
  if (statusDiv) { statusDiv.classList.remove('hidden'); }
  if (btn) { btn.disabled = false; }
  if (iconDefault) iconDefault.classList.add('hidden');
  if (iconLoading) iconLoading.classList.add('hidden');
  if (btnText) btnText.textContent = 'Пересканировать карту';
  btn?.classList.add('btn-success');
  showToast('Карта успешно добавлена', 'success');
};

function finishWizard() {
  const form = document.getElementById('personnel-step1-form');
  if (!form || !validateWizardStep(1)) return;

  wizardData.lastName = form.lastName?.value?.trim();
  wizardData.firstName = form.firstName?.value?.trim();
  wizardData.middleName = form.middleName?.value?.trim();
  wizardData.phone = form.phone?.value?.trim();
  wizardData.dateOfBirth = form.dateOfBirth?.value;
  wizardData.positionId = form.positionId?.value || null;

  submitPersonnelData();
}

async function submitPersonnelData() {
  try {
    showLoading('Создание сотрудника...');
    const payload = {
      lastName: wizardData.lastName,
      firstName: wizardData.firstName,
      middleName: wizardData.middleName || null,
      phone: wizardData.phone || null,
      dateOfBirth: wizardData.dateOfBirth || null,
      positionId: wizardData.positionId || null,
      photoBase64: wizardData.photoBase64 || null
    };

    const newPersonnel = await Api.personnelCreate(payload);

    if (wizardData.photo) {
      try {
        await Api.faceUpload(newPersonnel.id, wizardData.photo);
        showToast('Фото загружено в CompreFace', 'success');
      } catch (err) {
        showToast('Сотрудник создан, но фото не загрузилось', 'warning');
      }
    }

    if (wizardData.cardUid) {
      try {
        await Api.cardRegister(newPersonnel.id, wizardData.cardUid);
        showToast('Карта привязана', 'success');
      } catch (err) {
        const msg = String(err?.message || '').toLowerCase();
        const alreadyExists = msg.includes('already') || msg.includes('exists') || msg.includes('существует');
        if (alreadyExists) {
          try {
            await Api.cardReassign(wizardData.cardUid, newPersonnel.id);
            showToast('Карта перепривязана на нового сотрудника', 'success');
          } catch (_) {
            showToast('Сотрудник создан, но карту не удалось перепривязать', 'warning');
          }
        } else {
          showToast('Сотрудник создан, но карта не привязалась', 'warning');
        }
      }
    }

    hideLoading();
    document.getElementById('add-personnel-modal').classList.add('hidden');
    resetWizard();
    showToast('Сотрудник успешно создан', 'success');
    loadPersonnel();
  } catch (error) {
    hideLoading();
    console.error('Failed to create personnel:', error);
    showToast('Ошибка при создании сотрудника', 'error');
  }
}

function resetWizard() {
  stopNfcScanPolling();
  stopCardRegistrationWindowSilently();
  currentWizardStep = 1;
  wizardData = { lastName: '', firstName: '', middleName: '', phone: '', dateOfBirth: '', positionId: null, photo: null, photoBase64: null, cardUid: null };

  document.getElementById('personnel-step1-form')?.reset();

  const previewImg = document.getElementById('photo-preview-img');
  const placeholderIcon = document.getElementById('photo-placeholder-icon');
  const container = document.getElementById('photo-preview-container');
  if (previewImg) { previewImg.src = ''; previewImg.classList.add('hidden'); }
  if (placeholderIcon) placeholderIcon.classList.remove('hidden');
  if (container) container.classList.remove('has-image');

  const cardBtn = document.getElementById('register-card-btn');
  const btnText = document.getElementById('card-btn-text');
  const iconDefault = document.getElementById('card-btn-icon-default');
  const iconLoading = document.getElementById('card-btn-icon-loading');
  const statusDiv = document.getElementById('card-registration-status');

  if (cardBtn) { cardBtn.disabled = false; cardBtn.classList.replace('btn-success', 'btn-primary'); }
  if (btnText) btnText.textContent = 'Сканировать карту';
  if (iconDefault) iconDefault.classList.remove('hidden');
  if (iconLoading) iconLoading.classList.add('hidden');
  if (statusDiv) statusDiv.classList.add('hidden');

  showWizardStep(1);
}

async function ensurePositionsLoaded() {
  if (positionsData.length > 0) return;
  await loadPositions();
}

// ==================== Device Wizard ====================
let currentDeviceWizardStep = 1;

function initDeviceWizard() {
  const modal = document.getElementById('add-device-modal');
  if (!modal) return;

  document.getElementById('add-device-btn')?.addEventListener('click', openAddDeviceWizard);
  document.getElementById('close-device-wizard')?.addEventListener('click', closeDeviceWizard);
  modal.addEventListener('click', (e) => { if (e.target === modal) closeDeviceWizard(); });
  document.getElementById('device-next-btn')?.addEventListener('click', nextDeviceStep);
  document.getElementById('device-prev-btn')?.addEventListener('click', prevDeviceStep);
  document.getElementById('device-finish-btn')?.addEventListener('click', finishDeviceWizard);
}

function openAddDeviceWizard() {
  currentDeviceWizardStep = 1;
  document.getElementById('device-id') && (document.getElementById('device-id').value = '');
  document.getElementById('device-kind') && (document.getElementById('device-kind').value = '');
  document.getElementById('device-location') && (document.getElementById('device-location').value = '');
  document.getElementById('add-device-modal')?.classList.remove('hidden');
  showDeviceStep(1);
}

function closeDeviceWizard() {
  document.getElementById('add-device-modal')?.classList.add('hidden');
}

function showDeviceStep(step) {
  document.querySelectorAll('.device-wizard-step').forEach(el => el.classList.add('hidden'));
  document.querySelector(`.device-wizard-step[data-step="${step}"]`)?.classList.remove('hidden');

  for (let i = 1; i <= 2; i++) {
    const node = document.getElementById(`device-step-node-${i}`);
    if (!node) continue;
    node.classList.remove('active', 'completed');
    if (i < step) {
      node.classList.add('completed');
      node.innerHTML = '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>';
    } else if (i === step) {
      node.classList.add('active');
      node.textContent = i;
    } else {
      node.textContent = i;
    }
  }

  document.getElementById('device-prev-btn')?.classList.toggle('hidden', step === 1);
  document.getElementById('device-next-btn')?.classList.toggle('hidden', step === 2);
  document.getElementById('device-finish-btn')?.classList.toggle('hidden', step !== 2);

  if (step === 2) {
    const id = document.getElementById('device-id')?.value?.trim();
    const kind = document.getElementById('device-kind')?.value?.trim();
    const previewId = document.getElementById('device-preview-id');
    const previewKind = document.getElementById('device-preview-kind');
    if (previewId) previewId.textContent = id || '—';
    if (previewKind) previewKind.textContent = kind || '—';
  }
}

function validateDeviceStep() {
  const idEl = document.getElementById('device-id');
  const kindEl = document.getElementById('device-kind');
  const id = idEl?.value?.trim() || '';
  const kind = kindEl?.value?.trim() || '';
  let ok = true;

  if (!id) { showFieldError(idEl, 'ID обязателен'); ok = false; }
  else if (!/^[a-zA-Z0-9\-_]+$/.test(id)) { showFieldError(idEl, 'Только латиница, цифры, дефис, подчёркивание'); ok = false; }
  else if (id.length < 2) { showFieldError(idEl, 'Минимум 2 символа'); ok = false; }

  if (!kind) { showFieldError(kindEl, 'Выберите тип'); ok = false; }
  return ok;
}

function nextDeviceStep() {
  if (currentDeviceWizardStep === 1 && validateDeviceStep()) {
    currentDeviceWizardStep = 2;
    showDeviceStep(2);
  }
}

function prevDeviceStep() {
  if (currentDeviceWizardStep === 2) {
    currentDeviceWizardStep = 1;
    showDeviceStep(1);
  }
}

async function finishDeviceWizard() {
  if (!validateDeviceStep()) return;
  const id = document.getElementById('device-id')?.value?.trim();
  const kind = document.getElementById('device-kind')?.value?.trim();
  const location = document.getElementById('device-location')?.value?.trim();

  try {
    showLoading('Добавление устройства...');
    await Api.deviceCreate({ id, kind, location: location || null });
    hideLoading();
    closeDeviceWizard();
    showToast('Устройство добавлено', 'success');
    loadDevices();
  } catch (error) {
    hideLoading();
    showToast(error.message || 'Ошибка добавления', 'error');
  }
}

// ==================== Guest Wizard ====================
let currentGuestStep = 1;
let guestPhotoBase64 = null;

function initGuestWizard() {
  const modal = document.getElementById('add-guest-modal');
  if (!modal) return;

  document.getElementById('add-guest-btn')?.addEventListener('click', openGuestWizard);
  document.getElementById('close-guest-wizard')?.addEventListener('click', closeGuestWizard);
  modal.addEventListener('click', (e) => { if (e.target === modal) closeGuestWizard(); });
  document.getElementById('guest-next-btn')?.addEventListener('click', nextGuestStep);
  document.getElementById('guest-prev-btn')?.addEventListener('click', prevGuestStep);
  document.getElementById('guest-finish-btn')?.addEventListener('click', finishGuestWizard);

  document.getElementById('guest-photo')?.addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (ev) => {
      guestPhotoBase64 = ev.target.result;
      const preview = document.getElementById('guest-photo-preview');
      if (preview) preview.innerHTML = `<img src="${escapeHtml(guestPhotoBase64)}" alt="" class="w-full h-full object-cover" />`;
    };
    reader.readAsDataURL(file);
  });

  document.getElementById('capture-guest-photo-btn')?.addEventListener('click', async () => {
    try {
      guestPhotoBase64 = await capturePhotoFromCamera();
      const preview = document.getElementById('guest-photo-preview');
      if (preview) preview.innerHTML = `<img src="${escapeHtml(guestPhotoBase64)}" alt="" class="w-full h-full object-cover" />`;
      showToast('Фото получено с камеры', 'success');
    } catch (error) {
      showToast(error.message || 'Камера недоступна', 'error');
    }
  });
}

function openGuestWizard() {
  currentGuestStep = 1;
  guestPhotoBase64 = null;
  ['guest-lastName', 'guest-firstName', 'guest-middleName', 'guest-phone', 'guest-company', 'guest-photo'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.value = '';
  });
  const preview = document.getElementById('guest-photo-preview');
  if (preview) preview.innerHTML = `<svg class="w-6 h-6 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/></svg>`;
  document.getElementById('add-guest-modal')?.classList.remove('hidden');
  showGuestStep(1);
}

function closeGuestWizard() {
  document.getElementById('add-guest-modal')?.classList.add('hidden');
}

function showGuestStep(step) {
  document.querySelectorAll('.guest-wizard-step').forEach(el => el.classList.add('hidden'));
  document.querySelector(`.guest-wizard-step[data-step="${step}"]`)?.classList.remove('hidden');

  for (let i = 1; i <= 2; i++) {
    const node = document.getElementById(`guest-step-node-${i}`);
    if (!node) continue;
    node.classList.remove('active', 'completed');
    if (i < step) {
      node.classList.add('completed');
      node.innerHTML = '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>';
    } else if (i === step) {
      node.classList.add('active');
      node.textContent = i;
    } else {
      node.textContent = i;
    }
  }

  document.getElementById('guest-prev-btn')?.classList.toggle('hidden', step === 1);
  document.getElementById('guest-next-btn')?.classList.toggle('hidden', step === 2);
  document.getElementById('guest-finish-btn')?.classList.toggle('hidden', step !== 2);

  if (step === 2) {
    const ln = document.getElementById('guest-lastName')?.value?.trim() || '';
    const fn = document.getElementById('guest-firstName')?.value?.trim() || '';
    const mn = document.getElementById('guest-middleName')?.value?.trim() || '';
    const preview = document.getElementById('guest-preview-name');
    if (preview) preview.textContent = [ln, fn, mn].filter(Boolean).join(' ') || '—';
  }
}

function validateGuestStep1() {
  const lnEl = document.getElementById('guest-lastName');
  const fnEl = document.getElementById('guest-firstName');
  const nameRegex = /^[а-яА-ЯёЁa-zA-Z\s\-']+$/;
  let ok = true;

  const ln = lnEl?.value?.trim() || '';
  const fn = fnEl?.value?.trim() || '';

  if (!ln) { showFieldError(lnEl, 'Фамилия обязательна'); ok = false; }
  else if (!nameRegex.test(ln)) { showFieldError(lnEl, 'Только буквы, пробелы, дефисы и апострофы'); ok = false; }

  if (!fn) { showFieldError(fnEl, 'Имя обязательно'); ok = false; }
  else if (!nameRegex.test(fn)) { showFieldError(fnEl, 'Только буквы, пробелы, дефисы и апострофы'); ok = false; }

  return ok;
}

function nextGuestStep() {
  if (currentGuestStep === 1 && validateGuestStep1()) {
    currentGuestStep = 2;
    showGuestStep(2);
  }
}

function prevGuestStep() {
  if (currentGuestStep === 2) {
    currentGuestStep = 1;
    showGuestStep(1);
  }
}

async function finishGuestWizard() {
  if (!validateGuestStep1()) return;

  const lastName = document.getElementById('guest-lastName')?.value?.trim();
  const firstName = document.getElementById('guest-firstName')?.value?.trim();
  const middleName = document.getElementById('guest-middleName')?.value?.trim();
  const phone = document.getElementById('guest-phone')?.value?.trim();
  const company = document.getElementById('guest-company')?.value?.trim();

  const phoneEl = document.getElementById('guest-phone');
  if (phone && !/^(\+7|8)?[\s\-\(\)]*(\d[\s\-\(\)]*){10}$/.test(phone)) {
    showFieldError(phoneEl, 'Неверный формат телефона');
    return;
  }

  try {
    showLoading('Добавление гостя...');
    await Api.guestCreate({
      lastName, firstName,
      middleName: middleName || null,
      phone: phone || null,
      company: company || null,
      photoBase64: guestPhotoBase64 || null
    });
    hideLoading();
    closeGuestWizard();
    showToast('Гость добавлен', 'success');
    loadGuests();
  } catch (error) {
    hideLoading();
    showToast(error.message || 'Ошибка добавления', 'error');
  }
}

// ==================== Personnel Search ====================
function initPersonnelSearch() {
  document.getElementById('search-personnel')?.addEventListener('input', (e) => {
    listState.personnel.query = e.target.value.trim();
    listState.personnel.page = 0;
    scheduleListReload(() => loadPersonnel());
  });
}

// ==================== Mobile Touch Feedback ====================
function initMobileTouchFeedback() {
  if (window.innerWidth > 768) return;
  document.querySelectorAll('button, a, [role="button"], .btn-primary, .btn-secondary').forEach(el => {
    if (!(el instanceof HTMLElement) || el.dataset.touchEnhanced === '1') return;
    el.dataset.touchEnhanced = '1';
    el.addEventListener('touchstart', () => el.classList.add('active-touch'), { passive: true });
    el.addEventListener('touchend', () => setTimeout(() => el.classList.remove('active-touch'), 150), { passive: true });
    el.addEventListener('touchcancel', () => el.classList.remove('active-touch'), { passive: true });
  });
}

// ==================== Refresh All ====================
function initRefreshAll() {
  document.getElementById('refresh-all')?.addEventListener('click', async () => {
    showToast('Обновление данных...', 'info');
    await Promise.all([
      loadDashboard(),
      currentSection !== 'dashboard' ? loadSectionData(currentSection) : Promise.resolve(),
      currentSection === 'profile' ? Promise.resolve() : loadProfile()
    ]);
    showToast('Данные обновлены', 'success');
  });
}

// ==================== Statistics ====================
let statsCharts = {};

async function loadStats() {
  const period = parseInt(document.getElementById('stats-period')?.value || '7', 10);
  try {
    const events = await Api.eventsFrom(period);
    statsEventsData = Array.isArray(events) ? events : [];
    renderStats(events, period);
  } catch (err) {
    console.error('Stats load error:', err);
    showToast('Ошибка загрузки статистики', 'error');
  }
}

function exportStatsCsv(period) {
  const toCsvCell = (value) => {
    const normalized = String(value ?? '').replace(/[\r\n]+/g, ' ').trim();
    return `"${normalized.replaceAll('"', '""')}"`;
  };

  const rows = [];
  const sourceLabel = (src) => {
    if (src === 'NFC') return 'Карта (NFC)';
    if (src === 'FACE') return 'Лицо (FACE)';
    if (src === 'MANUAL') return 'Ручное событие';
    return src || '';
  };
  const directionLabel = (dir) => {
    if (dir === 'IN') return 'Вход';
    if (dir === 'OUT') return 'Выход';
    return dir || '';
  };
  rows.push(['Отчет', 'Статистика проходов']);
  rows.push(['Период (дни)', String(period)]);
  rows.push(['Сформирован', new Date().toISOString()]);
  rows.push([]);
  rows.push(['Дата/время', 'Источник', 'Направление', 'Сотрудник', 'Карта', 'Лицо', 'Устройство']);

  statsEventsData.forEach(e => {
    const personName = e.person ? [e.person.lastName, e.person.firstName, e.person.middleName].filter(Boolean).join(' ') : '';
    rows.push([
      formatDateTime(e.createdAt),
      sourceLabel(e.source),
      directionLabel(e.direction),
      personName || 'Неизвестно',
      e.card?.uid || e.uid || '',
      e.faceName || '',
      e.device?.id || ''
    ]);
  });

  const csv = rows
    .map(r => r.map(c => toCsvCell(c)).join(';'))
    .join('\r\n');

  const blob = new Blob(["\uFEFF" + csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `stats-report-${new Date().toISOString().slice(0, 10)}.csv`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

function renderStats(events, period) {
  const totalEvents = events.length;
  const inCount = events.filter(e => e.direction === 'IN').length;
  const outCount = events.filter(e => e.direction === 'OUT').length;
  const uniquePersons = new Set(events.map(e => e.person?.id).filter(Boolean));

  const setText = (id, v) => { const el = document.getElementById(id); if (el) el.textContent = v; };
  setText('stat-total-events', totalEvents);
  setText('stat-total-in', inCount);
  setText('stat-total-out', outCount);
  setText('stat-active-personnel', uniquePersons.size);

  // ——— Chart 1: Events by day ———
  const days = [];
  const inPerDay = [];
  const outPerDay = [];
  for (let i = period - 1; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    days.push(d.toLocaleDateString('ru-RU', { month: 'short', day: 'numeric' }));
    inPerDay.push(0);
    outPerDay.push(0);
  }
  events.forEach(e => {
    const label = new Date(e.createdAt).toLocaleDateString('ru-RU', { month: 'short', day: 'numeric' });
    const idx = days.indexOf(label);
    if (idx !== -1) {
      if (e.direction === 'IN') inPerDay[idx]++;
      else if (e.direction === 'OUT') outPerDay[idx]++;
    }
  });

  const ctx1 = document.getElementById('chart-events-by-day');
  if (ctx1 && typeof Chart !== 'undefined') {
    if (statsCharts.byDay) statsCharts.byDay.destroy();
    statsCharts.byDay = new Chart(ctx1, {
      type: 'bar',
      data: {
        labels: days,
        datasets: [
          { label: 'Вход',  data: inPerDay,  backgroundColor: 'rgba(34,197,94,0.75)',  borderRadius: 4 },
          { label: 'Выход', data: outPerDay, backgroundColor: 'rgba(59,130,246,0.75)', borderRadius: 4 }
        ]
      },
      options: {
        responsive: true,
        plugins: { legend: { position: 'top' } },
        scales: { x: { stacked: false }, y: { beginAtZero: true, ticks: { precision: 0 } } }
      }
    });
  }

  // ——— Chart 2: IN vs OUT doughnut ———
  const other = totalEvents - inCount - outCount;
  const ctx2 = document.getElementById('chart-in-vs-out');
  if (ctx2 && typeof Chart !== 'undefined') {
    if (statsCharts.inVsOut) statsCharts.inVsOut.destroy();
    const inVsOutData = { labels: [], data: [], colors: [] };
    if (inCount)  { inVsOutData.labels.push('Вход');   inVsOutData.data.push(inCount);  inVsOutData.colors.push('rgba(34,197,94,0.8)'); }
    if (outCount) { inVsOutData.labels.push('Выход');  inVsOutData.data.push(outCount); inVsOutData.colors.push('rgba(59,130,246,0.8)'); }
    if (other > 0){ inVsOutData.labels.push('Прочее'); inVsOutData.data.push(other);    inVsOutData.colors.push('rgba(148,163,184,0.6)'); }
    if (inVsOutData.data.length === 0) {
      inVsOutData.labels.push('Нет данных'); inVsOutData.data.push(1); inVsOutData.colors.push('rgba(226,232,240,0.8)');
    }
    statsCharts.inVsOut = new Chart(ctx2, {
      type: 'doughnut',
      data: {
        labels: inVsOutData.labels,
        datasets: [{ data: inVsOutData.data, backgroundColor: inVsOutData.colors, borderWidth: 2, borderColor: '#fff' }]
      },
      options: { responsive: true, plugins: { legend: { position: 'bottom' } }, cutout: '60%' }
    });
  }

  // ——— Chart 3: Top 10 personnel ———
  const personCounts = {};
  events.forEach(e => {
    let key;
    if (e.person) key = `${e.person.lastName || ''} ${e.person.firstName || ''}`.trim() || e.person.id;
    else if (e.uid) key = `Карта: ${e.uid}`;
    else if (e.faceName) key = `Лицо: ${e.faceName}`;
    else return;
    personCounts[key] = (personCounts[key] || 0) + 1;
  });
  const topPersonnel = Object.entries(personCounts).sort((a, b) => b[1] - a[1]).slice(0, 10);

  const ctx3 = document.getElementById('chart-top-personnel');
  if (ctx3 && typeof Chart !== 'undefined') {
    if (statsCharts.topPersonnel) statsCharts.topPersonnel.destroy();
    statsCharts.topPersonnel = new Chart(ctx3, {
      type: 'bar',
      data: {
        labels: topPersonnel.map(([name]) => name),
        datasets: [{ label: 'Проходов', data: topPersonnel.map(([, c]) => c), backgroundColor: 'rgba(99,102,241,0.75)', borderRadius: 4 }]
      },
      options: {
        indexAxis: 'y',
        responsive: true,
        plugins: { legend: { display: false } },
        scales: { x: { beginAtZero: true, ticks: { precision: 0 } } }
      }
    });
  }

  // ——— Chart 4: By device ———
  const deviceCounts = {};
  events.forEach(e => {
    const devId = e.device?.id || 'Неизвестно';
    deviceCounts[devId] = (deviceCounts[devId] || 0) + 1;
  });
  const deviceEntries = Object.entries(deviceCounts).sort((a, b) => b[1] - a[1]);

  const ctx4 = document.getElementById('chart-by-device');
  if (ctx4 && typeof Chart !== 'undefined') {
    if (statsCharts.byDevice) statsCharts.byDevice.destroy();
    statsCharts.byDevice = new Chart(ctx4, {
      type: 'bar',
      data: {
        labels: deviceEntries.map(([id]) => id),
        datasets: [{ label: 'Проходов', data: deviceEntries.map(([, c]) => c), backgroundColor: 'rgba(245,158,11,0.75)', borderRadius: 4 }]
      },
      options: {
        responsive: true,
        plugins: { legend: { display: false } },
        scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
      }
    });
  }
}

function initStats() {
  document.getElementById('stats-period')?.addEventListener('change', loadStats);
  document.getElementById('stats-refresh-btn')?.addEventListener('click', loadStats);
  document.getElementById('stats-export-btn')?.addEventListener('click', () => {
    const period = parseInt(document.getElementById('stats-period')?.value || '7', 10);
    if (!statsEventsData.length) {
      showToast('Нет данных для отчета. Сначала обновите статистику.', 'warning');
      return;
    }
    exportStatsCsv(period);
    showToast('Отчет выгружен в CSV', 'success');
  });
}

// ==================== Inline Field Hints & Blur Validation ====================
function showInlineError(field, message) {
  clearInlineError(field);
  const errorEl = document.createElement('p');
  errorEl.className = 'inline-field-error text-xs text-red-600 mt-1 font-medium';
  errorEl.textContent = message;
  field.classList.add('border-red-400');
  field.parentNode.appendChild(errorEl);
}

function clearInlineError(field) {
  field.parentNode.querySelector('.inline-field-error')?.remove();
  field.classList.remove('border-red-400');
}

function initFieldHints() {
  const today = new Date();
  const maxDate = today.toISOString().split('T')[0];
  const minYear = today.getFullYear() - 120;
  const minDate = `${minYear}-01-01`;
  const currentYear = today.getFullYear();

  // ── Дата рождения (wizard добавления сотрудника)
  const dobField = document.getElementById('field-dob');
  if (dobField) {
    dobField.max = maxDate;
    dobField.min = minDate;
    const minYearSpan = document.getElementById('dob-min-year');
    const maxYearSpan = document.getElementById('dob-max-year');
    if (minYearSpan) minYearSpan.textContent = minYear;
    if (maxYearSpan) maxYearSpan.textContent = currentYear;

    dobField.addEventListener('blur', () => {
      const val = dobField.value;
      if (!val) { clearInlineError(dobField); return; }
      const date = new Date(val + 'T00:00:00');
      const year = date.getFullYear();
      if (year < minYear) {
        showInlineError(dobField, `Год ${year} — слишком давний. Допустимо от ${minYear} до ${currentYear}`);
      } else if (date > today) {
        showInlineError(dobField, `Год ${year} ещё не наступил — дата не может быть в будущем`);
      } else if (currentYear - year < 14) {
        showInlineError(dobField, `Сотруднику должно быть не менее 14 лет (год рождения не позднее ${currentYear - 14})`);
      } else {
        clearInlineError(dobField);
      }
    });
    dobField.addEventListener('input', () => clearInlineError(dobField));
  }

  // ── ФИО поля
  const nameRegex = /^[а-яА-ЯёЁa-zA-Z\s\-']+$/;
  [
    { id: 'field-lastName',    label: 'Фамилия' },
    { id: 'field-firstName',   label: 'Имя' },
    { id: 'field-middleName',  label: 'Отчество' },
    { id: 'edit-p-lastName',   label: 'Фамилия' },
    { id: 'edit-p-firstName',  label: 'Имя' },
    { id: 'edit-p-middleName', label: 'Отчество' },
    { id: 'guest-lastName',    label: 'Фамилия' },
    { id: 'guest-firstName',   label: 'Имя' },
    { id: 'guest-middleName',  label: 'Отчество' },
    { id: 'edit-g-lastName',   label: 'Фамилия' },
    { id: 'edit-g-firstName',  label: 'Имя' },
    { id: 'edit-g-middleName', label: 'Отчество' },
  ].forEach(({ id, label }) => {
    const el = document.getElementById(id);
    if (!el) return;
    el.addEventListener('blur', () => {
      const val = el.value.trim();
      if (!val) { clearInlineError(el); return; }
      if (!nameRegex.test(val)) {
        showInlineError(el, `${label}: только буквы, пробелы, дефисы и апострофы`);
      } else if (val.length < 2) {
        showInlineError(el, `${label} не может быть короче 2 символов`);
      } else if (val.length > 50) {
        showInlineError(el, `${label} не может быть длиннее 50 символов`);
      } else {
        clearInlineError(el);
      }
    });
    el.addEventListener('input', () => clearInlineError(el));
  });

  // ── Телефоны
  const phoneRegex = /^(\+7|8)?[\s\-\(\)]*(\d[\s\-\(\)]*){10}$/;
  ['field-phone', 'edit-p-phone', 'guest-phone', 'edit-g-phone'].forEach(id => {
    const el = document.getElementById(id);
    if (!el) return;
    el.addEventListener('blur', () => {
      const val = el.value.trim();
      if (!val) { clearInlineError(el); return; }
      if (!phoneRegex.test(val)) {
        showInlineError(el, 'Введите российский номер: +7 (XXX) XXX-XX-XX или 8XXXXXXXXXX');
      } else {
        clearInlineError(el);
      }
    });
    el.addEventListener('input', () => clearInlineError(el));
  });
}

// ==================== Main Init ====================
function init() {
  initUserMenu();
  initRoleBasedUI();
  initNavigation();
  initWizard();
  initDeviceWizard();
  initGuestWizard();
  initEditPersonnelModal();
  initEditGuestModal();
  initEditDeviceModal();
  initAddCameraModal();
  initEditCameraModal();
  initAddPositionModal();
  initEditPositionModal();
  initFieldHints();
  initProfileEdit();
  initEventFilters();
  initManualEventForm();
  initGuestVisitControls();
  initGuestVisitCreateModal();
  initPersonnelSearch();
  initGuestSearch();
  initFaces();
  initIntegrations();
  initStats();
  initRefreshAll();
  initMobileTouchFeedback();

  // Load all data
  loadDashboard();
  loadPersonnel();
  loadEvents();
  loadDevices();
  loadCameras();
  loadGuests();
  loadPositions();
  loadProfile();

  // Ensure modals are hidden
  ['loading-overlay', 'add-personnel-modal', 'add-device-modal', 'add-guest-modal',
   'add-camera-modal', 'add-position-modal', 'edit-personnel-modal', 'edit-guest-modal',
   'edit-device-modal', 'edit-position-modal', 'edit-camera-modal', 'guest-visit-create-modal', 'guest-visit-qr-modal'].forEach(id => {
    document.getElementById(id)?.classList.add('hidden');
  });

  // Backend connectivity check
  Api.getCurrentUser().catch(() => {
    const offlineBanner = document.getElementById('backend-offline');
    if (offlineBanner) offlineBanner.classList.remove('hidden');
    if (window.innerWidth <= 768) {
      showToast('Не удалось подключиться к серверу', 'error');
    }
  });

  // Mobile auto refresh
  startMobileAutoRefresh();

  // Refresh on visibility change / focus
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden && Date.now() - lastAutoRefreshAt > 5_000) {
      refreshActiveSection('visibility');
    }
  });
  window.addEventListener('focus', () => {
    if (Date.now() - lastAutoRefreshAt > 5_000) refreshActiveSection('focus');
  });
  window.addEventListener('online', () => refreshActiveSection('online'));

  // Re-apply touch feedback after DOM mutations
  const observer = new MutationObserver(() => initMobileTouchFeedback());
  observer.observe(document.body, { childList: true, subtree: true });
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', init);
} else {
  init();
}
