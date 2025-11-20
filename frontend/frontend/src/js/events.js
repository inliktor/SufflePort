import { Api } from './api.js';
import { $, toast } from './utils.js';

export async function loadEvents() {
  const list = $('#events-list');
  if (!list) return;
  list.innerHTML = '<li class="text-gray-500 text-sm">Загрузка...</li>';
  try {
    const events = await Api.eventsRecent();
    if (!events.length) {
      list.innerHTML = '<li class="text-gray-500 text-sm">Нет событий за последние 24ч</li>';
      return;
    }
    list.innerHTML = events.slice(-50).reverse().map(e => `
      <li class="bg-white border rounded px-3 py-2 text-sm flex justify-between gap-3">
        <div>
          <div class="font-mono text-xs text-gray-500">${e.id}</div>
          <div>${e.person && e.person.fullName ? e.person.fullName : (e.faceName || e.card?.uid || 'Неизвестно')}</div>
          <div class="text-xs text-gray-500">${e.device ? (e.device.location || e.device.id) : ''}</div>
        </div>
        <div class="text-right text-xs">
          <div class="badge">${e.direction}</div>
          <div class="mt-1 text-gray-500">${new Date(e.createdAt).toLocaleString()}</div>
        </div>
      </li>
    `).join('');
  } catch (e) {
    console.error(e);
    toast('Ошибка загрузки событий');
  }
}

