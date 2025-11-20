// API Base URL - укажите правильный адрес вашего backend
const API_BASE = 'http://192.168.88.247:8081/api';

// Utility functions
function $(selector) {
  return document.querySelector(selector);
}

function showMessage(text, type = 'error') {
  const msgEl = $('#auth-message');
  msgEl.textContent = text;
  msgEl.className = `mt-4 p-4 rounded-lg text-sm ${
    type === 'success' 
      ? 'bg-green-50 text-green-800 border border-green-200' 
      : 'bg-red-50 text-red-800 border border-red-200'
  }`;
  msgEl.classList.remove('hidden');
  
  setTimeout(() => {
    msgEl.classList.add('hidden');
  }, 5000);
}

function showToast(message, type = 'info') {
  const container = $('#toast-container');
  const toast = document.createElement('div');
  
  const colors = {
    success: 'bg-green-500',
    error: 'bg-red-500',
    info: 'bg-blue-500'
  };
  
  toast.className = `${colors[type]} text-white px-6 py-3 rounded-lg shadow-lg transform transition-all duration-300 translate-x-0`;
  toast.textContent = message;
  
  container.appendChild(toast);
  
  setTimeout(() => {
    toast.style.transform = 'translateX(400px)';
    setTimeout(() => toast.remove(), 300);
  }, 3000);
}

// Tab switching
$('#tab-login').addEventListener('click', () => {
  $('#tab-login').className = 'flex-1 py-2 px-4 rounded-md font-medium transition-all bg-white text-blue-600 shadow-sm';
  $('#tab-register').className = 'flex-1 py-2 px-4 rounded-md font-medium transition-all text-slate-600 hover:text-slate-800';
  $('#login-form').classList.remove('hidden');
  $('#register-form').classList.add('hidden');
  $('#auth-message').classList.add('hidden');
});

$('#tab-register').addEventListener('click', () => {
  $('#tab-register').className = 'flex-1 py-2 px-4 rounded-md font-medium transition-all bg-white text-green-600 shadow-sm';
  $('#tab-login').className = 'flex-1 py-2 px-4 rounded-md font-medium transition-all text-slate-600 hover:text-slate-800';
  $('#register-form').classList.remove('hidden');
  $('#login-form').classList.add('hidden');
  $('#auth-message').classList.add('hidden');
});

// Login
$('#login-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = e.target;
  const btn = form.querySelector('button[type=submit]');
  const originalText = btn.textContent;
  
  btn.disabled = true;
  btn.textContent = 'Вход...';
  
  try {
    const response = await fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include', // Важно для cookies
      body: JSON.stringify({
        username: form.username.value,
        password: form.password.value
      })
    });
    
    if (!response.ok) {
      const error = await response.text();
      throw new Error(error || 'Неверные учетные данные');
    }
    
    const data = await response.json();
    
    console.log('=== LOGIN RESPONSE DEBUG ===');
    console.log('Full response:', data);
    console.log('data.roleId:', data.roleId, 'type:', typeof data.roleId);
    console.log('data.isAdmin:', data.isAdmin, 'type:', typeof data.isAdmin);
    console.log('data.roleName:', data.roleName);
    
    // Сохраняем данные пользователя
    localStorage.setItem('username', data.username);
    localStorage.setItem('email', data.email);
    localStorage.setItem('roleId', String(data.roleId));
    localStorage.setItem('isAdmin', String(data.isAdmin));
    
    console.log('=== LOCALSTORAGE AFTER SAVE ===');
    console.log('roleId in storage:', localStorage.getItem('roleId'));
    console.log('isAdmin in storage:', localStorage.getItem('isAdmin'));
    console.log('isAdmin === "true"?', localStorage.getItem('isAdmin') === 'true');
    
    showToast('Вход выполнен успешно!', 'success');
    
    // Перенаправляем на главную страницу
    setTimeout(() => {
      window.location.href = '/';
    }, 500);
    
  } catch (error) {
    console.error('Login error:', error);
    showMessage(error.message || 'Ошибка входа', 'error');
  } finally {
    btn.disabled = false;
    btn.textContent = originalText;
  }
});

// Register
$('#register-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = e.target;
  const btn = form.querySelector('button[type=submit]');
  const originalText = btn.textContent;
  
  // Validate passwords match
  if (form.password.value !== form.confirmPassword.value) {
    showMessage('Пароли не совпадают', 'error');
    return;
  }
  
  btn.disabled = true;
  btn.textContent = 'Регистрация...';
  
  try {
    const response = await fetch(`${API_BASE}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({
        username: form.email.value, // используем email как username
        email: form.email.value,
        password: form.password.value
      })
    });
    
    if (!response.ok) {
      const error = await response.text();
      throw new Error(error || 'Ошибка регистрации');
    }
    
    const data = await response.json();
    
    showMessage('Регистрация успешна! Выполняется вход...', 'success');
    
    // Автоматический вход после регистрации
    setTimeout(async () => {
      const loginResponse = await fetch(`${API_BASE}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          username: form.email.value,
          password: form.password.value
        })
      });
      
      if (loginResponse.ok) {
        const loginData = await loginResponse.json();
        localStorage.setItem('username', loginData.username);
        localStorage.setItem('email', loginData.email);
        localStorage.setItem('roleId', String(loginData.roleId));
        localStorage.setItem('isAdmin', String(loginData.isAdmin));
        
        console.log('Auto-login after registration:', {
          username: loginData.username,
          roleId: loginData.roleId,
          isAdmin: loginData.isAdmin
        });
        
        window.location.href = '/';
      }
    }, 1000);
    
  } catch (error) {
    console.error('Register error:', error);
    showMessage(error.message || 'Ошибка регистрации', 'error');
  } finally {
    btn.disabled = false;
    btn.textContent = originalText;
  }
});

// Check if already logged in
if (localStorage.getItem('username')) {
  window.location.href = '/';
}
