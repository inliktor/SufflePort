import { Api } from './api.js';
import { $, toast, setLoading } from './utils.js';

export async function loadFaces(personId) {
  const grid = $('#faces-grid');
  if (!grid) return;
  if (!personId) {
    grid.innerHTML = '<div class="text-gray-500 text-sm">Сначала выберите сотрудника</div>';
    return;
  }
  grid.innerHTML = '<div class="text-gray-500 text-sm">Загрузка...</div>';
  try {
    await Api.faceSubject(personId); // ensure subject
    const data = await Api.faceList(personId);
    const faces = data.faces || [];
    if (!faces.length) {
      grid.innerHTML = '<div class="text-gray-500 text-sm">Фото не загружены</div>';
      return;
    }
    grid.innerHTML = faces.map(f => `
      <div class="relative group border rounded overflow-hidden bg-white">
        <div class="p-1 text-[10px] text-gray-500 break-all">${f.face_id}</div>
        <button data-face-id="${f.face_id}" class="absolute top-1 right-1 text-[10px] bg-red-600 text-white px-1 rounded opacity-0 group-hover:opacity-100">X</button>
      </div>
    `).join('');
    grid.querySelectorAll('button[data-face-id]').forEach(btn => {
      btn.addEventListener('click', async () => {
        const fid = btn.dataset.faceId;
        try {
          await Api.faceDelete(personId, fid);
          toast('Фото удалено', 'success');
          await loadFaces(personId);
        } catch (e) {
          console.error(e);
          toast('Ошибка удаления фото');
        }
      });
    });
  } catch (e) {
    console.error(e);
    toast('Ошибка загрузки лиц');
  }
}

export function bindFaceUpload(getCurrentPerson) {
  const form = $('#face-upload-form');
  if (!form) return;
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const person = getCurrentPerson();
    if (!person) return toast('Сначала выберите сотрудника');
    const fileInput = form.querySelector('input[type=file]');
    const file = fileInput.files[0];
    if (!file) return toast('Файл не выбран');
    const btn = form.querySelector('button[type=submit]');
    setLoading(btn, true);
    try {
      await Api.faceUpload(person.id, file);
      toast('Фото загружено', 'success');
      fileInput.value = '';
      await loadFaces(person.id);
    } catch (e2) {
      console.error(e2);
      toast('Ошибка загрузки фото');
    } finally { setLoading(btn, false); }
  });
}

