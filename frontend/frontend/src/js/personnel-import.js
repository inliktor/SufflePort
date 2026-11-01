// Импорт сотрудников из Excel. Живёт в разделе «Сотрудники», потому что
// относится к справочнику персонала, а не к процессу согласования заявок.
import { Api } from './api.js';
import { showToast, escapeHtml } from './utils.js';

const state = { bound: false };

const $ = (id) => document.getElementById(id);

export function initPersonnelImport(user) {
  bind();
  const allowed = Boolean(user?.isAdmin) || Number(user?.roleId ?? 0) === 1;
  $('personnel-import-btn')?.classList.toggle('hidden', !allowed);
}

function bind() {
  if (state.bound) return;
  const modal = $('personnel-import-modal');
  if (!modal) return;
  state.bound = true;

  $('personnel-import-btn')?.addEventListener('click', open);

  document.querySelectorAll('[data-import-close]').forEach(button => {
    button.addEventListener('click', close);
  });

  modal.addEventListener('click', (event) => {
    if (event.target === modal) close();
  });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && !modal.classList.contains('hidden')) close();
  });

  const template = $('personnel-import-template');
  if (template) template.href = Api.personnelImportTemplateUrl();

  $('personnel-import-preview')?.addEventListener('click', () => run('preview'));
  $('personnel-import-apply')?.addEventListener('click', () => run('apply'));
}

function open() {
  $('personnel-import-modal')?.classList.remove('hidden');
  document.body.classList.add('no-scroll');
  $('personnel-import-file')?.focus();
}

function close() {
  $('personnel-import-modal')?.classList.add('hidden');
  document.body.classList.remove('no-scroll');
}

async function run(mode) {
  const file = $('personnel-import-file')?.files?.[0];
  if (!file) {
    showToast('Выберите файл .xlsx', 'error');
    return;
  }

  const button = mode === 'preview' ? $('personnel-import-preview') : $('personnel-import-apply');
  if (button) button.disabled = true;
  setResult(skeletons());

  try {
    const report = mode === 'preview'
      ? await Api.personnelImportPreview(file)
      : await Api.personnelImportApply(file);
    renderReport(report, mode);

    if (mode === 'apply') {
      showToast(`Импортировано записей: ${report.ok}`, 'success');
      if (typeof window.loadPersonnel === 'function') window.loadPersonnel();
    }
  } catch (error) {
    setResult(emptyState('Импорт не выполнен', error.message));
  } finally {
    if (button) button.disabled = false;
  }
}

function renderReport(report, mode) {
  const rows = (report.rows || []).map(row => {
    const cls = row.status === 'ERROR'
      ? 'vr-report-row-error'
      : (row.status === 'SKIP' ? 'vr-report-row-skip' : '');
    const statusText = row.status === 'ERROR' ? 'Ошибка' : (row.status === 'SKIP' ? 'Пропущена' : 'Готова');
    return `
      <tr class="${cls}">
        <td>${row.rowNumber}</td>
        <td>${escapeHtml(row.fullName || '—')}</td>
        <td>${escapeHtml(statusText)}</td>
        <td>${escapeHtml((row.messages || []).join('; '))}</td>
      </tr>`;
  }).join('');

  setResult(`
    <div class="vr-card vr-fade">
      <div class="vr-card-main w-full">
        <div class="flex flex-wrap items-center gap-3 mb-3">
          <span class="vr-pill vr-pill-ok">Готовы: ${report.ok}</span>
          <span class="vr-pill vr-pill-wait">Пропущены: ${report.skipped}</span>
          <span class="vr-pill vr-pill-bad">Ошибки: ${report.errors}</span>
          <span class="text-xs text-slate-400">Всего строк: ${report.total}${mode === 'apply' ? ' · применено' : ' · проверка без записи'}</span>
        </div>
        <div class="w-full overflow-x-auto">
          <table class="vr-report-table">
            <thead><tr><th>Строка</th><th>Сотрудник</th><th>Статус</th><th>Замечания</th></tr></thead>
            <tbody>${rows || '<tr><td colspan="4">Нет данных</td></tr>'}</tbody>
          </table>
        </div>
      </div>
    </div>`);
}

function setResult(html) {
  const container = $('personnel-import-result');
  if (container) container.innerHTML = html;
}

function skeletons(count = 2) {
  return Array.from({ length: count }, () => '<div class="vr-skeleton"></div>').join('');
}

function emptyState(title, hint) {
  return `
    <div class="vr-empty">
      <p class="font-medium text-slate-500">${escapeHtml(title)}</p>
      ${hint ? `<p class="text-xs text-slate-400">${escapeHtml(hint)}</p>` : ''}
    </div>`;
}
