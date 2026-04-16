import { Api } from './api.js';
import { $, toast, escapeHtml } from './utils.js';

export async function loadDevices() {
  const list = $('#devices-list');
  if (!list) return;
  list.innerHTML = '<li class="text-gray-500 text-sm">Загрузка...</li>';
  try {
    const devs = await Api.devicesList();
    if (!devs.length) {
      list.innerHTML = '<li class="text-gray-500 text-sm">Устройств нет</li>';
      return;
    }
    list.innerHTML = devs.map(d => `
      <li class="bg-white border rounded px-3 py-2 text-sm flex flex-col gap-1">
        <div class="font-semibold">${escapeHtml(d.id)}</div>
        <div class="text-xs text-gray-500">${escapeHtml(d.kind || '')}</div>
        <div class="text-xs text-gray-500">${escapeHtml(d.location || '')}</div>
      </li>
    `).join('');
  } catch (e) {
    console.error(e);
    toast('Ошибка загрузки устройств');
  }
}

