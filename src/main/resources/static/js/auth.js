function toggleAuthContainers(target) {
  const signin = document.getElementById('signin-container');
  const signup = document.getElementById('signup-container');

  if (target === 'signup') {
    signin.style.display = 'none';
    signup.style.display = 'flex';
  } else {
    signup.style.display = 'none';
    signin.style.display = 'flex';
  }
}

function togglePasswordVisibility(fieldId) {
  const input = document.getElementById(fieldId);
  if (!input) return;
  if (input.type === 'password') {
    input.type = 'text';
  } else {
    input.type = 'password';
  }
}

function validatePasswordRequirements(password) {
  const pwd = password || '';
  const hasLen = pwd.length >= 8;
  const hasUpper = /[A-Z]/.test(pwd);
  const hasNum = /\d/.test(pwd);
  const hasSpecial = /[^A-Za-z0-9]/.test(pwd);

  const setReq = (id, valid) => {
    const el = document.getElementById(id);
    if (el) el.classList.toggle('valid', valid);
  };
  setReq('req-len', hasLen);
  setReq('req-upper', hasUpper);
  setReq('req-num', hasNum);
  setReq('req-special', hasSpecial);
  return hasLen && hasUpper && hasNum;
}

function fillDemo(emailOrUsername, password) {
  toggleAuthContainers('signin');
  document.getElementById('signin-email').value = emailOrUsername;
  document.getElementById('signin-password').value = password;
  showToast(`Credentials populated for ${emailOrUsername}. Click 'Sign In' or hit Enter.`, 'success');
}

function persistAuthSession(token, userProfile, rememberMe) {
  sessionStorage.removeItem('trustvault_logged_out');
  if (rememberMe) {
    localStorage.setItem('trustvault_token', token);
    localStorage.setItem('trustvault_user', JSON.stringify(userProfile));
    sessionStorage.removeItem('trustvault_token');
    sessionStorage.removeItem('trustvault_user');
  } else {
    sessionStorage.setItem('trustvault_token', token);
    sessionStorage.setItem('trustvault_user', JSON.stringify(userProfile));
    localStorage.removeItem('trustvault_token');
    localStorage.removeItem('trustvault_user');
  }
}

async function handleSignIn(e) {
  e.preventDefault();
  const username = document.getElementById('signin-email').value.trim();
  const password = document.getElementById('signin-password').value;
  const rememberMe = document.getElementById('signin-remember') ? document.getElementById('signin-remember').checked : true;

  if (!username || !password) {
    showToast('Please enter both your email/username and password.', 'error');
    return;
  }

  try {
    showToast('Authenticating with TrustVault Security Engine...', 'info');
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });

    if (!response.ok) {
      const err = await response.json().catch(() => ({ message: 'Invalid credentials' }));
      showToast(err.message || 'Authentication failed. Check email and password.', 'error');
      return;
    }

    const data = await response.json();
    const displayFullName = data.username === 'admin' ? 'Demo Admin' : data.fullName;
    const userProfile = {
      id: data.id,
      username: data.username,
      fullName: displayFullName,
      email: data.email,
      role: data.role,
      initials: data.username === 'admin' ? 'DA' : undefined
    };
    persistAuthSession(data.token, userProfile, rememberMe);

    showToast(`Welcome back, ${displayFullName}! Redirecting to dashboard...`, 'success');
    setTimeout(() => {
      window.location.href = 'index.html';
    }, 500);
  } catch (err) {
    showToast('Network error connecting to TrustVault server.', 'error');
  }
}

async function handleSignUp(e) {
  e.preventDefault();
  const fullName = document.getElementById('signup-fullname').value.trim();
  const email = document.getElementById('signup-email').value.trim();
  const password = document.getElementById('signup-password').value;
  const confirmPassword = document.getElementById('signup-confirm-password').value;

  if (!fullName || !email || !password) {
    showToast('All fields are required.', 'error');
    return;
  }

  const emailRegex = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
  if (!emailRegex.test(email)) {
    showToast('Please enter a valid email address.', 'error');
    return;
  }

  if (password !== confirmPassword) {
    showToast('Passwords do not match.', 'error');
    return;
  }

  if (!validatePasswordRequirements(password)) {
    showToast('Password must be at least 8 characters and include an uppercase letter and a number.', 'error');
    return;
  }

  // Derive valid 3-50 char username from email prefix
  let username = email.split('@')[0].toLowerCase().replace(/[^a-z0-9_]/g, '');
  if (username.length < 3) {
    username = (username + fullName.toLowerCase().replace(/[^a-z0-9]/g, '') + 'user').substring(0, 12);
  }

  try {
    showToast('Creating enterprise account...', 'info');
    const response = await fetch('/api/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username,
        email,
        password,
        fullName,
        role: 'ROLE_OWNER'
      })
    });

    if (!response.ok) {
      const err = await response.json().catch(() => ({ message: 'Registration failed' }));
      const msg = err.message || (err.errors ? Object.values(err.errors)[0] : 'Account registration failed.');
      showToast(msg, 'error');
      return;
    }

    const data = await response.json();
    persistAuthSession(data.token, {
      id: data.id,
      username: data.username,
      fullName: data.fullName,
      email: data.email,
      role: data.role
    }, true);

    showToast('Account created successfully! Launching dashboard...', 'success');
    setTimeout(() => {
      window.location.href = 'index.html';
    }, 500);
  } catch (err) {
    showToast('Failed to connect to TrustVault server.', 'error');
  }
}

function openForgotPasswordModal() {
  const modal = document.getElementById('forgot-password-modal');
  if (modal) {
    const signinEmail = document.getElementById('signin-email')?.value || '';
    const forgotInput = document.getElementById('forgot-email');
    if (forgotInput && signinEmail) forgotInput.value = signinEmail;
    modal.style.display = 'flex';
  }
}

function closeForgotPasswordModal() {
  const modal = document.getElementById('forgot-password-modal');
  if (modal) modal.style.display = 'none';
}

async function handleForgotPassword(e) {
  e.preventDefault();
  const email = document.getElementById('forgot-email').value.trim();
  if (!email) {
    showToast('Please enter your email address or username.', 'error');
    return;
  }

  try {
    const res = await fetch('/api/auth/forgot-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email })
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      showToast(data.message || 'Password recovery request rejected.', 'error');
      return;
    }
    showToast(data.message || 'Password recovery instructions dispatched.', 'success');
    closeForgotPasswordModal();
  } catch (err) {
    showToast('Network error submitting password recovery request.', 'error');
  }
}

function showToast(message, type = 'info') {
  const container = document.getElementById('toast-container');
  if (!container) return;
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerText = message;
  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}
