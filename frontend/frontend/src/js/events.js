import { Api } from './api.js';
import { $, toast, escapeHtml } from './utils.js';

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
    list.innerHTML = events.slice(-50).reverse().map(e => {
      const name = e.person && e.person.fullName ? e.person.fullName : (e.faceName || e.card?.uid || 'Неизвестно');
      const deviceLabel = e.device ? (e.device.location || e.device.id) : '';
      return `
      <li class="bg-white border rounded px-3 py-2 text-sm flex justify-between gap-3">
        <div>
          <div class="font-mono text-xs text-gray-500">${escapeHtml(e.id)}</div>
          <div>${escapeHtml(name)}</div>
          <div class="text-xs text-gray-500">${escapeHtml(deviceLabel)}</div>
        </div>
        <div class="text-right text-xs">
          <div class="badge">${escapeHtml(e.direction)}</div>
          <div class="mt-1 text-gray-500">${escapeHtml(new Date(e.createdAt).toLocaleString())}</div>
        </div>
      </li>
    `}).join('');
  } catch (e) {
    console.error(e);
    toast('Ошибка загрузки событий');
  }
}

