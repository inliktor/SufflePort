// DOM helpers
export function $(sel) { 
  return document.querySelector(sel); 
}

export function $all(sel) { 
  return Array.from(document.querySelectorAll(sel)); 
}

// Toast notifications
export function showToast(message, type = 'info', duration = 3000) {
  const container = document.getElementById('toast-container');
  if (!container) return;

  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  
  const icons = {
    success: `<svg class="w-5 h-5 text-green-600 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
              </svg>`,
    error: `<svg class="w-5 h-5 text-red-600 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
            </svg>`,
    warning: `<svg class="w-5 h-5 text-yellow-600 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path>
              </svg>`,
    info: `<svg class="w-5 h-5 text-blue-600 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
             <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
           </svg>`
  };

  toast.innerHTML = `
    ${icons[type] || icons.info}
    <div class="flex-1">
      <p class="text-sm font-medium text-slate-900">${message}</p>
    </div>
    <button class="toast-close ml-2 text-slate-400 hover:text-slate-600 transition-colors">
      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
      </svg>
    </button>
  `;

  container.appendChild(toast);

  // Close button
  toast.querySelector('.toast-close').addEventListener('click', () => {
    removeToast(toast);
  });

  // Auto remove
  setTimeout(() => {
    removeToast(toast);
  }, duration);
}

function removeToast(toast) {
  toast.style.opacity = '0';
  toast.style.transform = 'translateX(100%)';
  setTimeout(() => {
    toast.remove();
  }, 300);
}

// Legacy support
export function toast(msg, type = 'error', timeout = 2500) {
  showToast(msg, type, timeout);
}

// Loading state
export function setLoading(el, loading) {
  if (!el) return;
  if (loading) {
    el.dataset.originalText = el.textContent;
    el.innerHTML = `
      <svg class="animate-spin h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
      </svg>
      <span>Загрузка...</span>
    `;
    el.disabled = true;
  } else {
    if (el.dataset.originalText) el.textContent = el.dataset.originalText;
    el.disabled = false;
  }
}

// Show loading overlay
export function showLoading(show = true) {
  const overlay = document.getElementById('loading-overlay');
  if (overlay) {
    if (show) {
      overlay.classList.remove('hidden');
    } else {
      overlay.classList.add('hidden');
    }
  }
}

// Date/Time formatters
export function formatDateTime(dateString) {
  if (!dateString) return '-';
  const date = new Date(dateString);
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');
  return `${hours}:${minutes}:${seconds}`;
}

export function formatDate(dateString) {
  if (!dateString) return '-';
  const date = new Date(dateString);
  const day = String(date.getDate()).padStart(2, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const year = date.getFullYear();
  return `${day}.${month}.${year}`;
}

export function formatFullDateTime(dateString) {
  if (!dateString) return '-';
  return `${formatDate(dateString)} ${formatDateTime(dateString)}`;
}

// Debounce helper
export function debounce(func, wait) {
  let timeout;
  return function executedFunction(...args) {
    const later = () => {
      clearTimeout(timeout);
      func(...args);
    };
    clearTimeout(timeout);
    timeout = setTimeout(later, wait);
  };
}

// Copy to clipboard
export function copyToClipboard(text) {
  if (navigator.clipboard) {
    navigator.clipboard.writeText(text).then(() => {
      showToast('Скопировано в буфер обмена', 'success');
    }).catch(() => {
      showToast('Ошибка копирования', 'error');
    });
  } else {
    // Fallback
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    try {
      document.execCommand('copy');
      showToast('Скопировано в буфер обмена', 'success');
    } catch (err) {
      showToast('Ошибка копирования', 'error');
    }
    document.body.removeChild(textarea);
  }
}

// Confirm dialog
export function confirm(message, onConfirm, onCancel) {
  const result = window.confirm(message);
  if (result && onConfirm) {
    onConfirm();
  } else if (!result && onCancel) {
    onCancel();
  }
  return result;
}

