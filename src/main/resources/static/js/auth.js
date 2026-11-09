function toggleAuthContainers(target, event) {
  if (event && typeof event.preventDefault === 'function') {
    event.preventDefault();
  }
  const signin = document.getElementById('signin-container');
  const signup = document.getElementById('signup-container');

  if (target === 'signup') {
    signin.style.display = 'none';
    signup.style.display = 'flex';
  } else {
    signup.style.display = 'none';
    signin.style.display = 'flex';
  }
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function togglePasswordVisibility(fieldId, event) {
  if (event && typeof event.preventDefault === 'function') {
    event.preventDefault();
  }
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

function startGoogleSignIn() {
  window.location.href = '/oauth2/authorization/google';
}

function persistAuthSession(token, userProfile, rememberMe) {
  sessionStorage.removeItem('trustvault_logged_out');
  const maxAge = rememberMe ? 86400 * 7 : 86400;
  try {
    document.cookie = `trustvault_token=${encodeURIComponent(token)}; Path=/; SameSite=Lax; max-age=${maxAge}`;
  } catch (e) {}

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
    const displayFullName = data.fullName || data.username;
    const userProfile = {
      id: data.id,
      username: data.username,
      fullName: displayFullName,
      email: data.email,
      role: data.role,
      initials: (data.fullName ? data.fullName.charAt(0) : data.username.charAt(0)).toUpperCase()
    };
    persistAuthSession(data.token, userProfile, rememberMe);

    showToast(`Welcome back, ${displayFullName}! Redirecting to dashboard...`, 'success');
    setTimeout(() => {
      window.location.href = 'dashboard.html';
    }, 400);
  } catch (err) {
    showToast('Network error connecting to TrustVault server.', 'error');
  }
}

function autoFillUsername(email) {
  const usernameInput = document.getElementById('signup-username');
  if (usernameInput && !usernameInput.dataset.userEdited) {
    let clean = (email || '').split('@')[0].toLowerCase().replace(/[^a-z0-9_]/g, '');
    if (clean.length >= 3) {
      usernameInput.value = clean.substring(0, 30);
    }
  }
}

async function handleSignUp(e) {
  e.preventDefault();
  const fullName = document.getElementById('signup-fullname').value.trim();
  const email = document.getElementById('signup-email').value.trim();
  let username = (document.getElementById('signup-username')?.value || '').trim();
  const role = document.getElementById('signup-role')?.value || 'ROLE_OWNER';
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

  // Derive valid 3-50 char username if not supplied
  if (!username) {
    username = email.split('@')[0].toLowerCase().replace(/[^a-z0-9_]/g, '');
    if (username.length < 3) {
      username = (username + fullName.toLowerCase().replace(/[^a-z0-9]/g, '') + 'user').substring(0, 12);
    }
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
        role
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
      window.location.href = 'dashboard.html';
    }, 400);
  } catch (err) {
    showToast('Failed to connect to TrustVault server.', 'error');
  }
}

function openForgotPasswordModal(event) {
  if (event && typeof event.preventDefault === 'function') {
    event.preventDefault();
  }
  const modal = document.getElementById('forgot-password-modal');
  if (modal) {
    const signinEmail = document.getElementById('signin-email')?.value || '';
    const forgotInput = document.getElementById('forgot-email');
    if (forgotInput && signinEmail) forgotInput.value = signinEmail;
    modal.style.display = 'flex';
  }
}

function closeForgotPasswordModal(event) {
  if (event && typeof event.preventDefault === 'function') {
    event.preventDefault();
  }
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
  }, 4500);
}

async function handleOAuthCallbackOnLoad() {
  const params = new URLSearchParams(window.location.search);
  const oauthCode = params.get('oauth_code');
  const oauthError = params.get('oauth_error');

  if (!oauthCode && !oauthError) return;

  // Clean URL query parameters immediately so codes/errors are not replayed on refresh
  window.history.replaceState({}, document.title, window.location.pathname);

  if (oauthError) {
    const errorMessages = {
      not_configured: 'Google Sign-In requires GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET environment variables on the server.',
      cancelled: 'Google Sign-In was cancelled.',
      invalid_state: 'Security state validation failed or expired. Please try signing in again.',
      unverified_email: 'Your Google account email address must be verified to sign in to TrustVault.',
      account_conflict: 'This email belongs to a protected account that cannot be automatically linked via Google OAuth. Please sign in with your password.',
      account_disabled: 'This account has been disabled by an administrator.',
      token_exchange_failed: 'Unable to verify Google identity token with Google OAuth servers. Please try again.'
    };
    showToast(errorMessages[oauthError] || 'Google authentication failed. Please sign in with your credentials.', 'error');
    return;
  }

  if (oauthCode) {
    try {
      showToast('Completing Google OpenID Connect verification...', 'info');
      const res = await fetch('/api/auth/oauth2/exchange', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code: oauthCode })
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({ message: 'OAuth session exchange failed' }));
        showToast(err.message || 'Google session exchange failed.', 'error');
        return;
      }
      const data = await res.json();
      persistAuthSession(data.token, {
        id: data.id,
        username: data.username,
        fullName: data.fullName,
        email: data.email,
        role: data.role
      }, true);
      showToast(`Signed in with Google as ${data.fullName}! Redirecting...`, 'success');
      setTimeout(() => {
        window.location.href = 'dashboard.html';
      }, 450);
    } catch (e) {
      showToast('Network error completing Google authentication.', 'error');
    }
  }
}

document.addEventListener('DOMContentLoaded', () => {
  handleOAuthCallbackOnLoad();

  if (window.location.hash === '#signup' || window.location.search.includes('mode=signup')) {
    toggleAuthContainers('signup');
  }

  const signupUsername = document.getElementById('signup-username');
  if (signupUsername) {
    signupUsername.addEventListener('input', () => {
      signupUsername.dataset.userEdited = 'true';
    });
  }
});
