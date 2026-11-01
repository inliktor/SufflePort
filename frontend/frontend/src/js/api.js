// Prefer relative API path so the frontend works regardless of backend host/IP.
// Allow overriding via window.API_BASE if needed (e.g., during dev on remote devices).
const BASE = window.API_BASE || '/api';
const RETRYABLE_STATUS = new Set([502, 503, 504]);

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function buildQuery(params = {}) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value));
    }
  });
  const qs = query.toString();
  return qs ? `?${qs}` : '';
}

async function parseErrorMessage(response) {
  const fallback = `HTTP ${response.status}`;
  const contentType = response.headers.get('content-type') || '';

  try {
    if (contentType.includes('application/json')) {
      const body = await response.json();
      let message = body?.message || fallback;
      if (body?.errors && typeof body.errors === 'object') {
        const firstError = Object.values(body.errors).find(Boolean);
        if (firstError) message = `${message}: ${firstError}`;
      }
      return message;
    }

    const text = await response.text();
    return text || fallback;
  } catch (_) {
    return fallback;
  }
}

async function request(path, opts = {}) {
  const { retries = 2, ...fetchOpts } = opts;
  const headers = fetchOpts.body instanceof FormData ? {} : { 'Content-Type': 'application/json' };
  let r;

  for (let attempt = 0; ; attempt += 1) {
    try {
      r = await fetch(BASE + path, {
        headers,
        credentials: 'include', // ensure session cookies are sent
        cache: fetchOpts.cache ?? 'no-store',
        ...fetchOpts
      });
    } catch (err) {
      if (attempt < retries) {
        await delay(400 * (attempt + 1));
        continue;
      }
      throw new Error('Сервер временно недоступен, повторите через пару секунд');
    }

    if (RETRYABLE_STATUS.has(r.status) && attempt < retries) {
      await delay(400 * (attempt + 1));
      continue;
    }
    break;
  }
  
  // Если 401 или 403 - проверяем был ли пользователь залогинен
  if (r.status === 401 || r.status === 403) {
    const wasLoggedIn = localStorage.getItem('username');
    if (wasLoggedIn) {
      localStorage.clear();
      window.location.href = '/login.html';
    }
    throw new Error('Unauthorized');
  }
  
  if (!r.ok) {
    throw new Error(await parseErrorMessage(r));
  }
  const ct = r.headers.get('content-type') || '';
  if (ct.includes('application/json')) return r.json();
  return r.text();
}

export const Api = {
  request: (path, opts = {}) => request(path, opts),

  // Auth
  login: (data) => request('/auth/login', { method: 'POST', body: JSON.stringify(data) }),
  register: (data) => request('/auth/register', { method: 'POST', body: JSON.stringify(data) }),
  async logout() {
    return request('/auth/logout', { method: 'POST' });
  },
  getCurrentUser: () => request('/auth/me'),

  // Personnel & cards
  personnelList: (params = {}) => request(`/personnel${buildQuery(params)}`),
  personnelPage: (params = {}) => request(`/personnel${buildQuery(params)}`),
  personnelGet: (id) => request(`/personnel/${id}`),
  personnelCreate: (data) => request('/personnel', { method: 'POST', body: JSON.stringify(data) }),
  personnelUpdate: (id, data) => request(`/personnel/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  personnelDelete: (id) => request(`/personnel/${id}`, { method: 'DELETE' }),
  personnelCards: (id) => request(`/personnel/${encodeURIComponent(id)}/cards`),
  personnelCardAssign: (id, uid) => request(`/personnel/${encodeURIComponent(id)}/cards`, { method: 'POST', body: JSON.stringify({ uid }) }),
  personnelCardReassign: (id, uid) => request(`/personnel/${encodeURIComponent(id)}/cards/${encodeURIComponent(uid)}/reassign`, { method: 'PUT' }),
  personnelCardDelete: (id, uid) => request(`/personnel/${encodeURIComponent(id)}/cards/${encodeURIComponent(uid)}`, { method: 'DELETE' }),
  cardsByPerson: (personId) => request(`/cards/by-person/${personId}`),
  cardRegister: (personId, uid) => request('/cards', { method: 'POST', body: JSON.stringify({ personId, uid }) }),
  cardReassign: (uid, personId) => request(`/cards/${encodeURIComponent(uid)}/reassign/${encodeURIComponent(personId)}`, { method: 'PUT' }),
  cardRegisterByName: (uid, name) => request(`/ha/card/register-by-name?uid=${encodeURIComponent(uid)}&name=${encodeURIComponent(name)}`, { method: 'POST' }),
  cardScanLatest: () => request('/ha/card-scan/latest'),
  cardRegistrationStart: (ttlSec = 45) => request(`/cards/registration/start?ttlSec=${encodeURIComponent(ttlSec)}`, { method: 'POST' }),
  cardRegistrationStop: () => request('/cards/registration/stop', { method: 'POST' }),
  cardRegistrationStatus: () => request('/cards/registration/status'),
  cardCountActive: () => request('/cards/count-active'),

  // Events
  eventsList: (params = {}) => request(`/events${buildQuery(params)}`),
  eventsRecent: () => {
    const date = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
    return request(`/events/created-after?date=${encodeURIComponent(date)}`);
  },
  eventsRecentPage: (params = {}) => {
    const date = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
    return request(`/events/created-after${buildQuery({ date, ...params })}`);
  },
  eventsFrom: (days) => {
    const date = new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString();
    return request(`/events/created-after?date=${encodeURIComponent(date)}`);
  },
  eventsManualCreate: (data) => request('/events/manual', { method: 'POST', body: JSON.stringify(data) }),

  // NFC and Face toggles
  nfcToggle: (uid, device) => request(`/ha/nfc/toggle${buildQuery({ uid, device })}`, { method: 'POST' }),
  faceToggle: (face, device) => request(`/ha/face/toggle${buildQuery({ face, device })}`, { method: 'POST' }),

  // Devices
  devicesList: (params = {}) => request(`/devices${buildQuery(params)}`),
  devicesPage: (params = {}) => request(`/devices${buildQuery(params)}`),
  deviceCreate: (data) => request('/devices', { method: 'POST', body: JSON.stringify(data) }),
  deviceUpdate: (id, data) => request(`/devices/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deviceDelete: (id) => request(`/devices/${id}`, { method: 'DELETE' }),

  // Guests
  guestsList: (params = {}) => request(`/guests${buildQuery(params)}`),
  guestsPage: (params = {}) => request(`/guests${buildQuery(params)}`),
  guestCreate: (data) => request('/guests', { method: 'POST', body: JSON.stringify(data) }),
  guestUpdate: (id, data) => request(`/guests/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  guestDelete: (id) => request(`/guests/${id}`, { method: 'DELETE' }),

  // Positions
  positionsList: (params = {}) => request(`/positions${buildQuery(params)}`),
  positionsPage: (params = {}) => request(`/positions${buildQuery(params)}`),
  positionGet: (id) => request(`/positions/${id}`),
  positionCreate: (data) => request('/positions', { method: 'POST', body: JSON.stringify(data) }),
  positionUpdate: (id, data) => request(`/positions/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  positionDelete: (id) => request(`/positions/${id}`, { method: 'DELETE' }),

  // Cameras
  camerasList: (params = {}) => request(`/cameras${buildQuery(params)}`),
  camerasPage: (params = {}) => request(`/cameras${buildQuery(params)}`),
  cameraGet: (id) => request(`/cameras/${id}`),
  cameraCreate: (data) => request('/cameras', { method: 'POST', body: JSON.stringify(data) }),
  cameraUpdate: (id, data) => request(`/cameras/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  cameraReassignDevice: (id, deviceId) => request(`/cameras/${id}/reassign-device${buildQuery({ deviceId })}`, { method: 'PUT' }),
  cameraDelete: (id) => request(`/cameras/${id}`, { method: 'DELETE' }),
  camerasByDevice: (deviceId) => request(`/cameras/by-device/${deviceId}`),

  // Guest Visits
  guestVisitsList: (params = {}) => request(`/guest-visits${buildQuery(params)}`),
  guestVisitsPage: (params = {}) => request(`/guest-visits${buildQuery(params)}`),
  guestVisitCreate: (data) => request('/guest-visits', { method: 'POST', body: JSON.stringify(data) }),
  guestVisitUpdateStatus: (id, status) => request(`/guest-visits/${id}/status?status=${encodeURIComponent(status)}`, { method: 'PUT' }),
  guestVisitGet: (id) => request(`/guest-visits/${id}`),
  guestVisitsByGuest: (guestId) => request(`/guest-visits/by-guest/${guestId}`),
  guestVisitsToday: (status) => request(`/guest-visits/today${buildQuery({ status })}`),
  guestVisitStart: (id) => request(`/guest-visits/${id}/start`, { method: 'PUT' }),
  guestVisitFinish: (id) => request(`/guest-visits/${id}/finish`, { method: 'PUT' }),
  guestVisitCancel: (id) => request(`/guest-visits/${id}/cancel`, { method: 'PUT' }),
  guestVisitQrUrl: (id) => `${BASE}/guest-visits/${encodeURIComponent(id)}/qr`,
  guestVisitCheckinByCode: (code) => request('/guest-visits/checkin-by-code', { method: 'POST', body: JSON.stringify({ code }) }),

  // Visits requests (заявки на посещение)
  visitRequestsMine: () => request('/visit-requests/mine'),
  visitRequestsQueue: () => request('/visit-requests/queue'),
  visitRequestsAll: (status) => request(`/visit-requests/all${buildQuery({ status })}`),
  visitRequestsExpected: (date) => request(`/visit-requests/expected${buildQuery({ date })}`),
  visitRequestGet: (id) => request(`/visit-requests/${id}`),
  visitRequestHistory: (id) => request(`/visit-requests/${id}/history`),
  visitRequestCreate: (data) => request('/visit-requests', { method: 'POST', body: JSON.stringify(data) }),
  visitRequestUpdatePlan: (id, data) => request(`/visit-requests/${id}/plan`, {
    method: 'PUT', body: JSON.stringify(data)
  }),
  visitRequestSubmit: (id, comment) => request(`/visit-requests/${id}/submit`, {
    method: 'POST', body: JSON.stringify({ comment: comment || null })
  }),  visitRequestApprove: (id, comment) => request(`/visit-requests/${id}/approve`, {
    method: 'POST', body: JSON.stringify({ comment: comment || null })
  }),
  visitRequestReject: (id, comment) => request(`/visit-requests/${id}/reject`, {
    method: 'POST', body: JSON.stringify({ comment })
  }),
  visitRequestCancel: (id, comment) => request(`/visit-requests/${id}/cancel`, {
    method: 'POST', body: JSON.stringify({ comment: comment || null })
  }),

  // Notifications
  notifications: () => request('/notifications'),
  notificationsUnread: () => request('/notifications/unread-count'),
  notificationRead: (id) => request(`/notifications/${id}/read`, { method: 'POST' }),
  notificationsReadAll: () => request('/notifications/read-all', { method: 'POST' }),

  // Audit log (только администратор)
  auditLog: (params = {}) => request(`/audit${buildQuery(params)}`),

  // Импорт сотрудников из Excel (только администратор)
  personnelImportTemplateUrl: () => `${BASE}/personnel-import/template`,
  personnelImportPreview: async (file) => {
    const fd = new FormData();
    fd.append('file', file);
    const r = await fetch(BASE + '/personnel-import/preview', {
      method: 'POST', body: fd, credentials: 'include', cache: 'no-store'
    });
    if (!r.ok) throw new Error(await parseErrorMessage(r));
    return r.json();
  },
  personnelImportApply: async (file) => {
    const fd = new FormData();
    fd.append('file', file);
    const r = await fetch(BASE + '/personnel-import/apply', {
      method: 'POST', body: fd, credentials: 'include', cache: 'no-store'
    });
    if (!r.ok) throw new Error(await parseErrorMessage(r));
    return r.json();
  },

  // Card operations
  cardActivate: (uid) => request(`/cards/${uid}/activate`, { method: 'POST' }),
  cardDeactivate: (uid) => request(`/cards/${uid}/deactivate`, { method: 'POST' }),
  cardDelete: (uid) => request(`/cards/${uid}`, { method: 'DELETE' }),

  // CompreFace wrapper
  faceSubject: (personId) => request(`/face/${personId}/subject`),
  faceList: (personId) => request(`/face/${personId}/faces`),
  faceDelete: (personId, faceId) => request(`/face/${personId}/faces/${encodeURIComponent(faceId)}`, { method: 'DELETE' }),
  faceDeleteAll: (personId) => request(`/face/${personId}/faces`, { method: 'DELETE' }),
  faceUpload: async (personId, file) => {
    const fd = new FormData();
    fd.append('file', file);
    const r = await fetch(BASE + `/face/${personId}/upload`, {
      method: 'POST',
      body: fd,
      credentials: 'include',
      cache: 'no-store'
    });
    if (!r.ok) throw new Error(await parseErrorMessage(r));
    return r.json();
  }
};
