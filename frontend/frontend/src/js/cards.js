import { Api } from './api.js';
import { $, toast, setLoading } from './utils.js';

export function bindCardRegistration(getCurrentPerson) {
  const form = $('#card-reg-form');
  if (!form) return;
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const uid = form.uid.value.trim();
    const fullName = form.fullName.value.trim();
    if (!uid) return toast('UID пуст');
    const btn = form.querySelector('button[type=submit]');
    setLoading(btn, true);
    try {
      const res = await Api.cardRegisterByName(uid, fullName);
      toast(`Статус: ${res.status}`, 'success');
      if (getCurrentPerson() && getCurrentPerson().fullName === res.person_name) {
        await loadCards(getCurrentPerson().id);
      }
    } catch (e2) {
      console.error(e2);
      toast('Ошибка регистрации карты');
    } finally { setLoading(btn, false); }
  });
}

export async function loadCards(personId) {
  const listEl = $('#cards-list');
  if (!listEl) return;
  if (!personId) {
    listEl.innerHTML = '<li class="text-gray-500 text-sm">Сначала выберите сотрудника</li>';
    return;
  }
  listEl.innerHTML = '<li class="text-gray-500 text-sm">Загрузка...</li>';
  try {
    const cards = await Api.cardsByPerson(personId);
    if (!cards.length) {
      listEl.innerHTML = '<li class="text-gray-500 text-sm">У сотрудника нет карт</li>';
      return;
    }
    listEl.innerHTML = cards.map(c => `
      <li class="flex items-center justify-between border rounded px-3 py-2 bg-white text-sm">
        <div>
          <div class="font-mono text-xs">${c.uid}</div>
          <div class="text-xs text-gray-500">${c.active ? 'Активна' : 'Неактивна'}</div>
        </div>
      </li>
    `).join('');
  } catch (e) {
    console.error(e);
    toast('Ошибка загрузки карт');
  }
}

