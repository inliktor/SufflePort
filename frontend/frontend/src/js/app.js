import { Api } from './api.js';
import { showToast, formatDateTime, formatDate } from './utils.js';

// Check authentication
if (!localStorage.getItem('username')) {
  window.location.href = '/login.html';
}

// Проверяем роль пользователя
console.log('=== APP.JS ROLE CHECK ===');
console.log('localStorage.getItem("isAdmin"):', localStorage.getItem('isAdmin'));
console.log('localStorage.getItem("roleId"):', localStorage.getItem('roleId'));
const isAdmin = localStorage.getItem('isAdmin') === 'true';
const roleId = parseInt(localStorage.getItem('roleId') || '0');
console.log('Calculated isAdmin:', isAdmin);
console.log('Calculated roleId:', roleId);

// Debug: выводим информацию о пользователе
console.log('User role check:', {
  isAdmin,
  roleId,
  rawIsAdmin: localStorage.getItem('isAdmin'),
  rawRoleId: localStorage.getItem('roleId'),
  username: localStorage.getItem('username')
});

let currentPerson = null;
let personnelData = [];
let eventsData = [];
let devicesData = [];
let guestsData = [];

// ==================== Navigation ====================
function initNavigation() {
  // Desktop navigation
  const navItems = document.querySelectorAll('.nav-item');
  const sections = document.querySelectorAll('.section-content');
  
  navItems.forEach(item => {
    // Click handler
    item.addEventListener('click', (e) => {
      e.preventDefault();
      handleNavigation(item.dataset.section);
    });
    
    // Touch feedback
    item.addEventListener('touchstart', function() {
      this.style.transform = 'scale(0.97)';
    }, { passive: true });
    
    item.addEventListener('touchend', function() {
      this.style.transform = 'scale(1)';
    }, { passive: true });
  });
  
  function handleNavigation(sectionId) {
    // Update active nav item
    navItems.forEach(nav => nav.classList.remove('active'));
    const activeNav = Array.from(navItems).find(nav => nav.dataset.section === sectionId);
    if (activeNav) activeNav.classList.add('active');
    
    // Show selected section
    sections.forEach(section => {
      if (section.id === sectionId) {
        section.classList.remove('hidden');
      } else {
        section.classList.add('hidden');
      }
    });
    
    // Update page title
    updatePageTitle(sectionId);
    
    // Close mobile menu if open
    document.getElementById('mobile-menu')?.classList.add('hidden');
    
    // Scroll to top on mobile
    if (window.innerWidth < 768) {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }

  // Mobile navigation
  const mobileNavItems = document.querySelectorAll('.nav-item-mobile');
  mobileNavItems.forEach(item => {
    item.addEventListener('click', (e) => {
      e.preventDefault();
      handleNavigation(item.dataset.section);
    });
    
    // Touch feedback for mobile items
    item.addEventListener('touchstart', function() {
      this.style.transform = 'scale(0.97)';
      this.style.backgroundColor = 'rgba(0, 0, 0, 0.05)';
    }, { passive: true });
    
    item.addEventListener('touchend', function() {
      this.style.transform = 'scale(1)';
      this.style.backgroundColor = '';
    }, { passive: true });
  });

  // Mobile menu toggle
  document.getElementById('mobile-menu-btn').addEventListener('click', () => {
    document.getElementById('mobile-menu').classList.remove('hidden');
  });

  document.getElementById('close-menu-btn').addEventListener('click', () => {
    document.getElementById('mobile-menu').classList.add('hidden');
  });

  // Close mobile menu on overlay click
  document.getElementById('mobile-menu').addEventListener('click', (e) => {
    if (e.target.id === 'mobile-menu') {
      document.getElementById('mobile-menu').classList.add('hidden');
    }
  });
}

function updatePageTitle(sectionId) {
  const titles = {
    dashboard: { title: 'Дашборд', subtitle: 'Обзор системы контроля доступа' },
    personnel: { title: 'Сотрудники', subtitle: 'Управление персоналом организации' },
    cards: { title: 'Карты доступа', subtitle: 'Регистрация и управление картами' },
    events: { title: 'События', subtitle: 'Журнал прохода и действий' },
    devices: { title: 'Устройства', subtitle: 'Терминалы, считыватели и камеры' },
    faces: { title: 'Распознавание лиц', subtitle: 'Биометрическая идентификация' },
    guests: { title: 'Гости', subtitle: 'Управление посетителями' }
  };
  
  const info = titles[sectionId] || titles.dashboard;
  document.getElementById('page-title').textContent = info.title;
  document.getElementById('page-subtitle').textContent = info.subtitle;
}

// ==================== Dashboard ====================
async function loadDashboard() {
  try {
    const [personnel, events, devices] = await Promise.all([
      Api.personnelList().catch(() => []),
      Api.eventsRecent().catch(() => []),
      Api.devicesList().catch(() => [])
    ]);

    personnelData = personnel;
    eventsData = events;
    devicesData = devices;

    // Update stats
    document.getElementById('stat-personnel').textContent = personnel.length;
    document.getElementById('stat-cards').textContent = '-'; // Would need separate API call
    document.getElementById('stat-events').textContent = events.length;
    document.getElementById('stat-devices').textContent = devices.length;

    // Show recent events
    renderRecentEvents(events.slice(0, 5));
  } catch (error) {
    console.error('Dashboard load error:', error);
    showToast('Ошибка загрузки дашборда', 'error');
  }
}

function renderRecentEvents(events) {
  const container = document.getElementById('recent-events');
  if (!events || events.length === 0) {
    container.innerHTML = '<div class="text-center py-8 text-slate-400">Нет событий</div>';
    return;
  }

  container.innerHTML = events.map(event => {
    const directionClass = event.direction === 'IN' ? 'bg-green-100 text-green-800' : 
                          event.direction === 'OUT' ? 'bg-blue-100 text-blue-800' : 
                          'bg-gray-100 text-gray-800';
    const directionText = event.direction === 'IN' ? 'Вход' : 
                         event.direction === 'OUT' ? 'Выход' : 'Неизвестно';
    
    const sourceClass = event.source === 'NFC' ? 'bg-purple-500' : 
                       event.source === 'FACE' ? 'bg-blue-500' : 
                       'bg-gray-500';
    
    const personName = event.person ? `${event.person.lastName} ${event.person.firstName}` : 'Неизвестно';
    
    return `
      <div class="event-item">
        <div class="event-icon ${sourceClass}">
          <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            ${event.source === 'FACE' ? 
              '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14.828 14.828a4 4 0 01-5.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>' :
              '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"></path>'
            }
          </svg>
        </div>
        <div class="flex-1 min-w-0">
          <div class="flex items-center justify-between">
            <p class="font-semibold text-slate-800 truncate">${personName}</p>
            <span class="badge ${directionClass} ml-2">${directionText}</span>
          </div>
          <p class="text-sm text-slate-500 mt-1">${formatDateTime(event.createdAt)}</p>
          ${event.device ? `<p class="text-xs text-slate-400 mt-1">Устройство: ${event.device.id}</p>` : ''}
        </div>
      </div>
    `;
  }).join('');
}

// ==================== Personnel ====================
async function loadPersonnel() {
  try {
    const personnel = await Api.personnelList();
    personnelData = personnel;
    renderPersonnelTable(personnel);
  } catch (error) {
    console.error('Personnel load error:', error);
    showToast('Ошибка загрузки сотрудников', 'error');
  }
}

function renderPersonnelTable(personnel) {
  const tbody = document.getElementById('personnel-table-body');
  if (!personnel || personnel.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="5" class="text-center py-8 text-slate-400">Нет данных</td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = personnel.map(person => `
    <tr class="cursor-pointer hover:bg-blue-50" data-person-id="${person.id}">
      <td>
        <div class="font-semibold text-slate-900">${person.lastName} ${person.firstName}</div>
        <div class="text-xs text-slate-500">${person.middleName || ''}</div>
      </td>
      <td class="hidden md:table-cell">${person.position?.name || '-'}</td>
      <td class="hidden sm:table-cell">${person.phone || '-'}</td>
      <td class="hidden lg:table-cell">${person.dateOfBirth ? formatDate(person.dateOfBirth) : '-'}</td>
      <td class="text-right">
        <button class="btn-icon" onclick="selectPerson('${person.id}')" title="Выбрать">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"></path>
          </svg>
        </button>
      </td>
    </tr>
  `).join('');

  // Add click handlers
  tbody.querySelectorAll('tr[data-person-id]').forEach(row => {
    row.addEventListener('click', () => {
      const personId = row.dataset.personId;
      const person = personnel.find(p => p.id === personId);
      if (person) selectPerson(person);
    });
  });
}

function selectPerson(person) {
  if (typeof person === 'string') {
    person = personnelData.find(p => p.id === person);
  }
  
  if (!person) return;
  
  currentPerson = person;
  const fullName = `${person.lastName} ${person.firstName} ${person.middleName || ''}`.trim();
  
  document.getElementById('cards-current-person').textContent = 
    `Выбран: ${fullName} (ID: ${person.id.substring(0, 8)}...)`;
  document.getElementById('faces-current-person').textContent = 
    `Выбран: ${fullName} (ID: ${person.id.substring(0, 8)}...)`;
  
  showToast(`Выбран: ${fullName}`, 'success');
}

// Expose to global scope for inline handlers
window.selectPerson = selectPerson;

// Search personnel
document.getElementById('search-personnel')?.addEventListener('input', (e) => {
  const query = e.target.value.toLowerCase();
  const filtered = personnelData.filter(p => 
    `${p.lastName} ${p.firstName} ${p.middleName}`.toLowerCase().includes(query) ||
    (p.phone && p.phone.includes(query))
  );
  renderPersonnelTable(filtered);
});

// ==================== Cards ====================
function initCards() {
  const form = document.getElementById('card-reg-form');
  form?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const formData = new FormData(form);
    const uid = formData.get('uid');
    const fullName = formData.get('fullName');

    if (!uid || !fullName) {
      showToast('Заполните все поля', 'error');
      return;
    }

    try {
      const result = await Api.cardRegisterByName(uid, fullName);
      showToast(`Карта зарегистрирована: ${result.status}`, 'success');
      form.reset();
    } catch (error) {
      console.error('Card registration error:', error);
      showToast('Ошибка регистрации карты', 'error');
    }
  });

  // NFC Toggle
  document.getElementById('nfc-toggle-btn')?.addEventListener('click', async () => {
    const uid = document.getElementById('nfc-uid').value;
    const device = document.getElementById('nfc-device').value || 'default';

    if (!uid) {
      showToast('Введите UID карты', 'error');
      return;
    }

    try {
      const direction = await Api.nfcToggle(uid, device);
      showToast(`Проход отмечен: ${direction}`, 'success');
      loadDashboard(); // Refresh events
    } catch (error) {
      console.error('NFC toggle error:', error);
      showToast('Ошибка отметки прохода', 'error');
    }
  });
}

// ==================== Events ====================
async function loadEvents() {
  try {
    const events = await Api.eventsRecent();
    eventsData = events;
    renderEventsTable(events);
  } catch (error) {
    console.error('Events load error:', error);
    showToast('Ошибка загрузки событий', 'error');
  }
}

function renderEventsTable(events) {
  const tbody = document.getElementById('events-table-body');
  if (!events || events.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="5" class="text-center py-8 text-slate-400">Нет событий</td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = events.map(event => {
    const directionClass = event.direction === 'IN' ? 'badge-green' : 
                          event.direction === 'OUT' ? 'badge-blue' : 
                          'badge-yellow';
    const directionText = event.direction === 'IN' ? 'Вход' : 
                         event.direction === 'OUT' ? 'Выход' : 
                         event.direction || 'Неизвестно';
    
    const sourceClass = event.source === 'NFC' ? 'badge-purple' : 
                       event.source === 'FACE' ? 'badge-blue' : 
                       'badge-yellow';
    
    const personName = event.person ? 
      `${event.person.lastName} ${event.person.firstName}` : 
      (event.faceName || event.card?.uid || 'Неизвестно');

    return `
      <tr>
        <td>
          <div class="font-medium text-slate-900">${formatDateTime(event.createdAt)}</div>
          <div class="text-xs text-slate-500">${formatDate(event.createdAt)}</div>
        </td>
        <td>
          <div class="font-medium text-slate-900">${personName}</div>
        </td>
        <td class="hidden md:table-cell">
          <span class="badge ${sourceClass}">${event.source || 'N/A'}</span>
        </td>
        <td>
          <span class="badge ${directionClass}">${directionText}</span>
        </td>
        <td class="hidden lg:table-cell">${event.device?.id || '-'}</td>
      </tr>
    `;
  }).join('');
}

document.getElementById('refresh-events')?.addEventListener('click', loadEvents);

// ==================== Devices ====================
async function loadDevices() {
  try {
    const devices = await Api.devicesList();
    devicesData = devices;
    renderDevicesGrid(devices);
  } catch (error) {
    console.error('Devices load error:', error);
    showToast('Ошибка загрузки устройств', 'error');
  }
}

function renderDevicesGrid(devices) {
  const grid = document.getElementById('devices-grid');
  if (!devices || devices.length === 0) {
    grid.innerHTML = '<div class="col-span-full text-center py-12 text-slate-400">Нет устройств</div>';
    return;
  }

  grid.innerHTML = devices.map(device => {
    const kindIcon = device.kind === 'NFC_READER' ? 
      '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"></path>' :
      device.kind === 'CAMERA' ?
      '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 10l4.553-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.447.894L15 14M5 18h8a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"></path>' :
      '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"></path>';

    return `
      <div class="device-card">
        <div class="flex items-start justify-between mb-4">
          <div class="w-12 h-12 bg-blue-100 rounded-xl flex items-center justify-center">
            <svg class="w-6 h-6 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              ${kindIcon}
            </svg>
          </div>
          <span class="badge badge-green">Онлайн</span>
        </div>
        <h4 class="font-semibold text-slate-900 mb-1">${device.id}</h4>
        <p class="text-sm text-slate-500 mb-2">${device.kind || 'Unknown'}</p>
        <p class="text-xs text-slate-400">${device.location || 'Не указано'}</p>
      </div>
    `;
  }).join('');
}

// ==================== Faces ====================
function initFaces() {
  document.getElementById('face-toggle-btn')?.addEventListener('click', async () => {
    const faceName = document.getElementById('face-name').value;
    const device = document.getElementById('face-device').value || 'default';

    if (!faceName) {
      showToast('Введите имя в системе распознавания', 'error');
      return;
    }

    try {
      const result = await Api.faceToggle(faceName, device);
      showToast(`Проход отмечен: ${result.direction}`, 'success');
      loadDashboard(); // Refresh events
    } catch (error) {
      console.error('Face toggle error:', error);
      showToast('Ошибка отметки прохода', 'error');
    }
  });
}

// ==================== Guests ====================
async function loadGuests() {
  try {
    // API endpoint for guests would be needed
    guestsData = [];
    renderGuestsTable(guestsData);
  } catch (error) {
    console.error('Guests load error:', error);
    showToast('Ошибка загрузки гостей', 'error');
  }
}

function renderGuestsTable(guests) {
  const tbody = document.getElementById('guests-table-body');
  if (!guests || guests.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="5" class="text-center py-8 text-slate-400">Нет данных</td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = guests.map(guest => `
    <tr>
      <td>${guest.lastName} ${guest.firstName}</td>
      <td class="hidden md:table-cell">${guest.company || '-'}</td>
      <td class="hidden sm:table-cell">${guest.phone || '-'}</td>
      <td class="hidden lg:table-cell">${guest.document || '-'}</td>
      <td class="text-right">
        <button class="btn-icon" title="Редактировать">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path>
          </svg>
        </button>
      </td>
    </tr>
  `).join('');
}

// ==================== Refresh All ====================
document.getElementById('refresh-all')?.addEventListener('click', async () => {
  showToast('Обновление данных...', 'info');
  await Promise.all([
    loadDashboard(),
    loadPersonnel(),
    loadEvents(),
    loadDevices(),
    loadGuests()
  ]);
  showToast('Данные обновлены', 'success');
});

// ==================== User Menu ====================
function initUserMenu() {
  const username = localStorage.getItem('username') || 'User';
  const usernameEl = document.getElementById('username-display');
  const avatarEl = document.getElementById('user-avatar');
  
  if (usernameEl) {
    usernameEl.textContent = username;
  }
  
  if (avatarEl) {
    avatarEl.textContent = username.charAt(0).toUpperCase();
  }
  
  // Logout button
  document.getElementById('logout-btn')?.addEventListener('click', () => {
    localStorage.removeItem('username');
    localStorage.removeItem('email');
    localStorage.removeItem('roleId');
    localStorage.removeItem('isAdmin');
    window.location.href = '/login.html';
  });
}

// ==================== Role-Based UI ====================
function initRoleBasedUI() {
  console.log('initRoleBasedUI called:', { isAdmin, roleId });
  
  // Для обычных пользователей (role_id = 0) показываем только dashboard
  if (!isAdmin) {
    console.log('User is NOT admin, hiding admin sections');
    const adminOnlySections = ['personnel', 'cards', 'devices', 'faces', 'guests'];
    
    // Скрываем пункты меню
    adminOnlySections.forEach(section => {
      const navItems = document.querySelectorAll(`[data-section="${section}"]`);
      console.log(`Hiding section ${section}, found ${navItems.length} nav items`);
      navItems.forEach(item => {
        item.style.display = 'none';
      });
    });
    
    // Показываем только dashboard
    const allSections = document.querySelectorAll('.section-content');
    allSections.forEach(section => {
      if (section.id === 'dashboard') {
        section.classList.remove('hidden');
      } else {
        section.classList.add('hidden');
      }
    });
  } else {
    console.log('User IS admin, showing all sections');
  }
}

// ==================== Mobile Touch Feedback ====================
function initMobileTouchFeedback() {
  // Добавляем тактильный feedback для всех кликабельных элементов
  const clickableSelectors = 'button, .btn-primary, .btn-secondary, .btn-danger, .btn-success, .btn-icon, a, .card, .device-card, .event-item';
  const clickableElements = document.querySelectorAll(clickableSelectors);
  
  clickableElements.forEach(element => {
    // Пропускаем элементы которые уже обработаны
    if (element.dataset.touchInit) return;
    element.dataset.touchInit = 'true';
    
    element.addEventListener('touchstart', function(e) {
      if (!this.disabled && !this.classList.contains('disabled')) {
        this.style.transition = 'transform 0.1s ease';
        this.style.transform = 'scale(0.97)';
        this.style.opacity = '0.8';
      }
    }, { passive: true });
    
    element.addEventListener('touchend', function() {
      this.style.transform = 'scale(1)';
      this.style.opacity = '1';
    }, { passive: true });
    
    element.addEventListener('touchcancel', function() {
      this.style.transform = 'scale(1)';
      this.style.opacity = '1';
    }, { passive: true });
  });
}

// ==================== Initialize ====================
function init() {
  initUserMenu();
  initRoleBasedUI();
  initNavigation();
  initCards();
  initFaces();
  initMobileTouchFeedback();
  
  // Load initial data
  loadDashboard();
  loadPersonnel();
  loadEvents();
  loadDevices();
  loadGuests();
  
  // Перезапускаем touch feedback после динамических изменений
  const observer = new MutationObserver(() => {
    initMobileTouchFeedback();
  });
  
  observer.observe(document.body, {
    childList: true,
    subtree: true
  });
}

// Start app when DOM is ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', init);
} else {
  init();
}

