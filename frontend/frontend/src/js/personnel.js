}
import { Api } from './api.js';
import { $, toast } from './utils.js';

export async function loadPersonnel(onSelectPerson) {
  const tbody = $('#personnel-table');
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="4" class="p-3 text-center text-gray-500">Загрузка...</td></tr>';
  try {
    const list = await Api.personnelList();
    if (!list.length) {
      tbody.innerHTML = '<tr><td colspan="4" class="p-3 text-center text-gray-500">Нет данных</td></tr>';
      return;
    }
    tbody.innerHTML = list.map(p => `
      <tr class="hover:bg-gray-50">
        <td class="p-2 align-top text-xs">${p.id}</td>
        <td class="p-2 align-top">${p.fullName || ''}</td>
        <td class="p-2 align-top text-sm">${p.phone || ''}</td>
        <td class="p-2 align-top text-right">
          <button class="btn text-xs" data-person-id="${p.id}" data-person-name="${p.fullName || ''}">Выбрать</button>
        </td>
      </tr>
    `).join('');
    tbody.querySelectorAll('button[data-person-id]').forEach(btn => {
      btn.addEventListener('click', () => {
        const personId = btn.dataset.personId;
        const name = btn.dataset.personName;
        onSelectPerson && onSelectPerson({ id: personId, fullName: name });
      });
    });
  } catch (e) {
    console.error(e);
    toast('Ошибка загрузки сотрудников');
  }

