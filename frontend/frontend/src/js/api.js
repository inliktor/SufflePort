const BASE = 'http://192.168.88.247:8081/api';

async function request(path, opts = {}) {
  const headers = opts.body instanceof FormData ? {} : { 'Content-Type': 'application/json' };
  
  const r = await fetch(BASE + path, {
    headers,
    credentials: 'include', // Включаем cookies для сессий
    ...opts
  });
  
  // Если 401 или 403 - проверяем был ли пользователь залогинен
  if (r.status === 401 || r.status === 403) {
    const wasLoggedIn = localStorage.getItem('username');
    console.warn('API request returned', r.status, 'for path:', path);
    if (wasLoggedIn) {
      console.log('User was logged in, clearing session and redirecting');
      localStorage.clear();
      window.location.href = '/login.html';
    }
    throw new Error('Unauthorized');
  }
  
  if (!r.ok) throw new Error('HTTP ' + r.status);
  const ct = r.headers.get('content-type') || '';
  if (ct.includes('application/json')) return r.json();
  return r.text();
}

export const Api = {
  // HA
  hasAccess: (uid) => request(`/ha/has-access?uid=${encodeURIComponent(uid)}`),
  cardRegisterByName: (uid, name) => request(`/ha/card/register-by-name?uid=${encodeURIComponent(uid)}&name=${encodeURIComponent(name)}`, { method: 'POST' }),
  cardScan: (uid) => request(`/ha/card/scan?uid=${encodeURIComponent(uid)}`, { method: 'POST' }),
  nfcToggle: (uid, device) => request(`/ha/nfc/toggle?uid=${encodeURIComponent(uid)}&device=${encodeURIComponent(device)}`, { method: 'POST' }),
  faceToggle: (face, device) => request(`/ha/face/toggle?face=${encodeURIComponent(face)}&device=${encodeURIComponent(device)}`, { method: 'POST' }),

  // Personnel & cards
  personnelList: () => request('/personnel'),
  cardsByPerson: (personId) => request(`/cards/by-person/${personId}`),

  // Devices
  devicesList: () => request('/devices'),

  // Events (берём последние по created-after за небольшой период)
  eventsRecent: () => {
    const date = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
    return request(`/events/created-after?date=${encodeURIComponent(date)}`);
  },

  // CompreFace wrapper
  faceSubject: (personId) => request(`/face/${personId}/subject`),
  faceList: (personId) => request(`/face/${personId}/faces`),
  faceDelete: (personId, faceId) => request(`/face/${personId}/faces/${encodeURIComponent(faceId)}`, { method: 'DELETE' }),
  faceDeleteAll: (personId) => request(`/face/${personId}/faces`, { method: 'DELETE' }),
  faceUpload: async (personId, file) => {
    const fd = new FormData();
    fd.append('file', file);
    const r = await fetch(BASE + `/face/${personId}/upload`, { method: 'POST', body: fd });
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return r.json();
  }
};
