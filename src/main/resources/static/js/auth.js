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
  if (input.type === 'password') {
    input.type = 'text';
  } else {
    input.type = 'password';
  }
}

function fillDemo(emailOrUsername, password) {
  toggleAuthContainers('signin');
  document.getElementById('signin-email').value = emailOrUsername;
  document.getElementById('signin-password').value = password;
  showToast(`Credentials populated for ${emailOrUsername}. Click 'Sign In' or hit Enter.`, 'success');
}

async function handleSignIn(e) {
  e.preventDefault();
  const username = document.getElementById('signin-email').value.trim();
  const password = document.getElementById('signin-password').value;

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
    localStorage.setItem('trustvault_token', data.token);
    localStorage.setItem('trustvault_user', JSON.stringify({
      id: data.id,
      username: data.username,
      fullName: displayFullName,
      email: data.email,
      role: data.role,
      initials: data.username === 'admin' ? 'DA' : undefined
    }));

    showToast(`Welcome back, ${displayFullName}! Redirecting to dashboard...`, 'success');
    setTimeout(() => {
      window.location.href = 'index.html';
    }, 600);
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

  if (password !== confirmPassword) {
    showToast('Passwords do not match.', 'error');
    return;
  }

  // Derive username from email or name
  const username = email.split('@')[0].toLowerCase().replace(/[^a-z0-9]/g, '');

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
        role: 'ROLE_VIEWER'
      })
    });

    if (!response.ok) {
      const err = await response.json().catch(() => ({ message: 'Registration failed' }));
      showToast(err.message || 'Account registration failed.', 'error');
      return;
    }

    const data = await response.json();
    localStorage.setItem('trustvault_token', data.token);
    localStorage.setItem('trustvault_user', JSON.stringify({
      id: data.id,
      username: data.username,
      fullName: data.fullName,
      email: data.email,
      role: data.role
    }));

    showToast('Account created successfully! Launching dashboard...', 'success');
    setTimeout(() => {
      window.location.href = 'index.html';
    }, 600);
  } catch (err) {
    showToast('Failed to connect to TrustVault server.', 'error');
  }
}

function showToast(message, type = 'info') {
  const container = document.getElementById('toast-container');
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerText = message;
  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}
