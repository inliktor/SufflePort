import { Api } from './api.js';

export function renderPersonnel(list) {
  const tbody = document.querySelector('#personnel-table-body');
  if (!tbody) return;
  tbody.innerHTML = list.map(p => `
    <tr class="border-b">
      <td class="py-1">${p.id}</td>
      <td class="py-1">${p.fullName || ''}</td>
      <td class="py-1">${p.phone || ''}</td>
    </tr>
  `).join('');
}

export async function loadPersonnel() {
  try {
    const data = await Api.personnelList();
    renderPersonnel(data);
  } catch (e) { console.error(e); }
}

export function initCardRegistration() {
  const form = document.querySelector('#card-reg-form');
  if (!form) return;
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const uid = form.querySelector('[name=uid]').value.trim();
    const name = form.querySelector('[name=fullName]').value.trim();
    if (!uid) return alert('UID пуст');
    try {
      const res = await Api.cardRegisterByName(uid, name);
      alert(`Статус: ${res.status}\nИмя: ${res.person_name || ''}`);
    } catch (err) { alert('Ошибка'); }
  });
}

export function initNfcToggle() {
  const btn = document.querySelector('#nfc-toggle-btn');
  if (!btn) return;
  btn.addEventListener('click', async () => {
    const uid = document.querySelector('#nfc-uid').value.trim();
    const device = document.querySelector('#nfc-device').value.trim();
    if (!uid) return alert('UID пуст');
    const dir = await Api.nfcToggle(uid, device);
    alert(`Направление: ${dir}`);
  });
}

export function initFaceToggle() {
  const btn = document.querySelector('#face-toggle-btn');
  if (!btn) return;
  btn.addEventListener('click', async () => {
    const face = document.querySelector('#face-name').value.trim();
    const device = document.querySelector('#face-device').value.trim();
    if (!face) return alert('Имя лица пусто');
    const res = await Api.faceToggle(face, device);
    alert(`Direction: ${res.direction}`);
  });
}

export function bootstrap() {
  loadPersonnel();
  initCardRegistration();
  initNfcToggle();
  initFaceToggle();
}

