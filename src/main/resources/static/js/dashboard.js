// State & Credentials
let currentUser = JSON.parse(localStorage.getItem('trustvault_user') || 'null');
let token = localStorage.getItem('trustvault_token') || null;
// Auto‑login demo admin if no token present (ensures upload works out‑of‑the‑box)
if (!token) {
  // fire the demo login for admin – it will set token & currentUser in localStorage
  switchDemoUser('admin');
}

// Fallback demo users
const DEMO_USERS = {
  admin: {
    username: 'admin',
    password: 'Admin@123',
    fullName: 'Abdul Haffiz',
    role: 'Admin',
    initials: 'AH'
  },
  owner: {
    username: 'owner',
    password: 'Owner@123',
    fullName: 'Alice Jenkins',
    role: 'Document Owner',
    initials: 'AJ'
  },
  viewer: {
    username: 'viewer',
    password: 'Viewer@123',
    fullName: 'Bob Smith',
    role: 'Viewer',
    initials: 'BS'
  }
};

let currentView = 'dashboard';
let documentsList = [];

// Helper API caller
async function api(url, options = {}) {
  const headers = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  if (!options.isFormData && !options.headers?.['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }

  const response = await fetch(url, {
    ...options,
    headers: { ...headers, ...options.headers }
  });

  if (response.status === 401) {
    showToast('Session expired. Please log in.', 'error');
    window.location.href = 'login.html';
    return null;
  }
  if (!response.ok) {
    const err = await response.json().catch(() => ({ message: 'Request failed' }));
    throw new Error(err.message || 'Error');
  }
  return await response.json();
}

// Switch Demo User
async function switchDemoUser(userKey) {
  const target = DEMO_USERS[userKey] || DEMO_USERS.admin;
  try {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: target.username, password: target.password })
    });
    if (res.ok) {
      const data = await res.json();
      token = data.token;
      currentUser = {
        id: data.id,
        username: data.username,
        fullName: target.fullName,
        email: data.email,
        role: target.role,
        initials: target.initials
      };
      localStorage.setItem('trustvault_token', token);
      localStorage.setItem('trustvault_user', JSON.stringify(currentUser));
    }
  } catch (e) {
    currentUser = target;
  }

  // Update UI chips
  document.querySelectorAll('.strip-chip').forEach(c => c.classList.remove('active'));
  const chip = document.getElementById(`chip-${userKey}`);
  if (chip) chip.classList.add('active');

  updateUserInterface();
  showToast(`Switched active user to ${target.fullName} (${target.role})`, 'success');
  loadDashboardData();
}

function updateUserInterface() {
  if (!currentUser) currentUser = DEMO_USERS.admin;

  document.getElementById('header-avatar').innerText = currentUser.initials || currentUser.fullName?.charAt(0) || 'U';
  document.getElementById('header-username').innerText = currentUser.fullName || currentUser.username;
  document.getElementById('header-role').innerText = currentUser.role || 'User';
  document.getElementById('welcome-user-name').innerText = currentUser.fullName || currentUser.username;
}

// Navigation between views
function navigateTo(viewId) {
  currentView = viewId;

  document.querySelectorAll('.sidebar-nav .nav-item').forEach(item => {
    item.classList.toggle('active', item.dataset.view === viewId);
  });

  document.querySelectorAll('.view-panel').forEach(panel => {
    panel.style.display = 'none';
  });

  const titles = {
    'dashboard': 'Dashboard',
    'my-docs': 'My Documents',
    'shared-with-me': 'Shared with Me',
    'shared-by-me': 'Shared by Me',
    'access-control': 'Access Control',
    'audit-logs': 'Audit Logs',
    'recycle-bin': 'Recycle Bin',
    'settings': 'Settings'
  };

  document.getElementById('page-title').innerText = titles[viewId] || 'Dashboard';

  const targetPanel = document.getElementById(`view-${viewId}`);
  if (targetPanel) {
    targetPanel.style.display = 'block';
  } else {
    // Default fallback to my-docs view
    document.getElementById('view-my-docs').style.display = 'block';
  }

  if (viewId === 'dashboard') loadDashboardData();
  if (viewId === 'my-docs' || viewId === 'shared-with-me' || viewId === 'shared-by-me') loadAllDocumentsView(viewId);
  if (viewId === 'access-control') loadAccessControlView();
  if (viewId === 'audit-logs') loadAuditLogsView();
}

// Load Dashboard Data & KPIs from Real Backend APIs
async function loadDashboardData() {
  const kpiUsers = document.getElementById('kpi-total-users');
  const kpiDocs = document.getElementById('kpi-total-docs');
  const kpiPerms = document.getElementById('kpi-shared-docs');
  const kpiAudit = document.getElementById('kpi-storage-usage');
  const postureBox = document.getElementById('security-posture-summary');
  const recentActList = document.getElementById('recent-activities-list');

  if (kpiUsers) kpiUsers.innerText = 'Loading...';
  if (kpiDocs) kpiDocs.innerText = 'Loading...';
  if (kpiPerms) kpiPerms.innerText = 'Loading...';
  if (kpiAudit) kpiAudit.innerText = 'Loading...';
  if (postureBox) postureBox.innerHTML = 'Loading security telemetry from backend...';
  if (recentActList) recentActList.innerHTML = '<li style="padding:0.75rem 1rem;color:#94a3b8;">Loading recent audit trail events...</li>';

  try {
    const [stats, users, logs] = await Promise.all([
      api('/api/admin/stats').catch(() => null),
      api('/api/admin/users').catch(() => null),
      api('/api/admin/audit-logs').catch(() => null)
    ]);

    if (stats) {
      if (kpiUsers) kpiUsers.innerText = users ? users.length : '3+';
      if (kpiDocs) kpiDocs.innerText = stats.totalDocuments;
      if (kpiPerms) kpiPerms.innerText = stats.activePermissions;
      if (kpiAudit) {
        const denied24h = (stats.unauthorizedAccessAttempts24h || 0) + (stats.failedLogins24h || 0);
        kpiAudit.innerText = `${stats.totalAuditLogs} (${denied24h} denied)`;
      }
      if (postureBox) {
        postureBox.innerHTML = `
          <div style="display:flex;gap:1.5rem;flex-wrap:wrap;font-size:0.9rem;">
            <div><strong>Total Audit Blocks:</strong> ${stats.totalAuditLogs}</div>
            <div><strong>Failed Logins (24h):</strong> ${stats.failedLogins24h}</div>
            <div><strong>Denied Access Attempts (24h):</strong> <span style="color:${stats.unauthorizedAccessAttempts24h > 0 ? '#ef4444' : '#16a34a'};font-weight:700;">${stats.unauthorizedAccessAttempts24h}</span></div>
            <div><strong>Tamper Status:</strong> <span style="color:${stats.tamperDetected ? '#ef4444' : '#16a34a'};font-weight:700;">${stats.tamperDetected ? 'TAMPER DETECTED' : 'VERIFIED INTACT'}</span></div>
            <div><strong>Suspicious Activity Flag:</strong> ${stats.suspiciousActivityFlag ? '<span style="color:#ef4444;font-weight:700;">ELEVATED</span>' : '<span style="color:#16a34a;font-weight:700;">NORMAL</span>'}</div>
          </div>
        `;
      }
    } else {
      // Non-admin user: compute accessible counts from user's own endpoints
      const [myDocs, sharedDocs, myReqs] = await Promise.all([
        api('/api/documents/my').catch(() => []),
        api('/api/documents/shared').catch(() => []),
        api('/api/requests/my').catch(() => [])
      ]);
      if (kpiUsers) kpiUsers.innerText = 'Admin Only';
      if (kpiDocs) kpiDocs.innerText = (myDocs ? myDocs.length : 0) + (sharedDocs ? sharedDocs.length : 0);
      if (kpiPerms) kpiPerms.innerText = sharedDocs ? sharedDocs.length : 0;
      if (kpiAudit) kpiAudit.innerText = `${myReqs ? myReqs.length : 0} Requests`;
      if (postureBox) {
        postureBox.innerHTML = `<span style="color:#64748b;">Signed in as <strong>${escapeHtml(currentUser?.username || 'user')}</strong> (${escapeHtml(currentUser?.role || 'User')}). Switch to Admin to view system-wide security metrics and full audit ledger.</span>`;
      }
    }

    if (recentActList) {
      if (logs && logs.length > 0) {
        recentActList.innerHTML = logs.slice(0, 6).map(l => `
          <li style="padding:0.65rem 1rem;border-bottom:1px solid #e2e8f0;display:flex;justify-content:space-between;align-items:center;font-size:0.85rem;">
            <div>
              <strong style="color:${l.success ? '#0f172a' : '#ef4444'};">[${escapeHtml(l.action)}]</strong>
              <span>${escapeHtml(l.username)} &mdash; ${escapeHtml(l.details)}</span>
            </div>
            <span style="color:#64748b;font-size:0.78rem;">${new Date(l.timestamp).toLocaleString()}</span>
          </li>
        `).join('');
      } else {
        recentActList.innerHTML = '<li style="padding:0.75rem 1rem;color:#64748b;">Admin role required to view global audit stream.</li>';
      }
    }
  } catch (err) {
    if (kpiUsers) kpiUsers.innerText = 'Error';
    if (kpiDocs) kpiDocs.innerText = 'Error';
    if (kpiPerms) kpiPerms.innerText = 'Error';
    if (kpiAudit) kpiAudit.innerText = 'Error';
    if (postureBox) postureBox.innerHTML = `<span style="color:#ef4444;">Failed to load security stats: ${escapeHtml(err.message)}</span>`;
  }

  loadRecentDocuments();
  loadPendingAccessRequests();
}

// Populate Recent Documents from Backend APIs
async function loadRecentDocuments() {
  const tbody = document.getElementById('recent-docs-tbody');
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; color:#94a3b8; padding:1.5rem;">Loading documents...</td></tr>';

  try {
    const [myDocs, sharedDocs] = await Promise.all([
      api('/api/documents/my').catch(() => []),
      api('/api/documents/shared').catch(() => [])
    ]);

    const combinedMap = new Map();
    (myDocs || []).forEach(d => combinedMap.set(d.id, d));
    (sharedDocs || []).forEach(d => combinedMap.set(d.id, d));
    documentsList = Array.from(combinedMap.values());

    if (documentsList.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="5" style="text-align:center; color:#64748b; padding:1.5rem;">
            No accessible documents found for this user.
            <button class="btn btn-sm btn-primary" style="margin-left:0.75rem;" onclick="openRequestAccessModal(1)">Request Access to Document #1</button>
          </td>
        </tr>
      `;
      return;
    }

    tbody.innerHTML = documentsList.slice(0, 8).map(d => {
      const filename = d.originalFilename || d.title || 'Document';
      const type = filename.endsWith('.xlsx') ? 'xls' : filename.endsWith('.docx') ? 'doc' : 'pdf';
      const modified = new Date(d.updatedAt || d.createdAt).toLocaleDateString('en-US', {
        month: 'short', day: 'numeric', year: 'numeric', hour: '2-digit', minute: '2-digit'
      });
      const isOwner = d.owner || (currentUser && d.ownerUsername === currentUser.username);
      const accessLabel = isOwner ? 'OWNER' : (d.myPermission || 'SHARED');

      return `
        <tr>
          <td>
            <div class="doc-name-cell">
              <div class="file-badge-icon ${type}">${type.toUpperCase()}</div>
              <span>${escapeHtml(d.title)} <small style="color:#64748b;">(#${d.id})</small></span>
            </div>
          </td>
          <td>${escapeHtml(isOwner ? 'You (' + d.ownerUsername + ')' : d.ownerUsername)}</td>
          <td>${escapeHtml(modified)}</td>
          <td>
            <span class="access-pill ${isOwner ? 'private' : 'shared'}">
              ${escapeHtml(accessLabel)}
            </span>
          </td>
          <td style="text-align: right; display:flex; gap:0.4rem; justify-content:flex-end;">
            <button class="btn btn-sm btn-primary" onclick="downloadDocument(${d.id})">Download</button>
            <button class="btn btn-sm" onclick="openEditDocumentModal(${d.id})">Edit</button>
            ${!isOwner ? `<button class="btn btn-sm" onclick="openRequestAccessModal(${d.id})">Request Access</button>` : `<button class="btn btn-sm" onclick="openShareModal(${d.id})">Share</button>`}
          </td>
        </tr>
      `;
    }).join('');
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="5" style="text-align:center; color:#ef4444; padding:1.5rem;">Failed to load documents: ${escapeHtml(e.message)}</td></tr>`;
  }
}

// Pending Access Requests (Dashboard & Access Control Inbox)
async function loadPendingAccessRequests() {
  const dashTbody = document.getElementById('pending-requests-tbody');
  const manageTbody = document.getElementById('access-requests-manage-tbody');

  if (dashTbody) {
    dashTbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:#94a3b8;padding:1rem;">Loading pending access requests...</td></tr>';
  }
  if (manageTbody) {
    manageTbody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:#94a3b8;padding:1rem;">Loading pending access requests...</td></tr>';
  }

  try {
    const pending = await api('/api/requests/pending');
    if (!pending || pending.length === 0) {
      const emptyMsg = 'No pending access requests for documents you own.';
      if (dashTbody) dashTbody.innerHTML = `<tr><td colspan="5" style="text-align:center;color:#64748b;padding:1rem;">${emptyMsg}</td></tr>`;
      if (manageTbody) manageTbody.innerHTML = `<tr><td colspan="6" style="text-align:center;color:#64748b;padding:1rem;">${emptyMsg}</td></tr>`;
      return;
    }

    if (dashTbody) {
      dashTbody.innerHTML = pending.map(r => `
        <tr>
          <td><strong>${escapeHtml(r.requesterUsername)}</strong></td>
          <td>${escapeHtml(r.documentTitle)} (#${r.documentId})</td>
          <td><span class="access-pill shared">${escapeHtml(r.requestedPermission)}</span> &mdash; ${escapeHtml(r.reason || 'No reason provided')}</td>
          <td>${new Date(r.createdAt).toLocaleString()}</td>
          <td style="text-align:right;display:flex;gap:0.4rem;justify-content:flex-end;">
            <button class="btn btn-sm btn-primary" style="background:#16a34a;" onclick="reviewAccessRequest(${r.id}, true)">Approve</button>
            <button class="btn btn-sm" style="background:#ef4444;color:#fff;" onclick="reviewAccessRequest(${r.id}, false)">Reject</button>
          </td>
        </tr>
      `).join('');
    }

    if (manageTbody) {
      manageTbody.innerHTML = pending.map(r => `
        <tr>
          <td><strong>${escapeHtml(r.requesterUsername)}</strong> (${escapeHtml(r.requesterEmail || '')})</td>
          <td>${escapeHtml(r.documentTitle)} (#${r.documentId})</td>
          <td><span class="access-pill shared">${escapeHtml(r.requestedPermission)}</span></td>
          <td>${escapeHtml(r.reason || 'No reason provided')}</td>
          <td><strong>${escapeHtml(r.status)}</strong></td>
          <td style="text-align:right;display:flex;gap:0.4rem;justify-content:flex-end;">
            <button class="btn btn-sm btn-primary" style="background:#16a34a;" onclick="reviewAccessRequest(${r.id}, true)">Approve</button>
            <button class="btn btn-sm" style="background:#ef4444;color:#fff;" onclick="reviewAccessRequest(${r.id}, false)">Reject</button>
          </td>
        </tr>
      `).join('');
    }
  } catch (err) {
    const errMsg = `Failed to load pending requests: ${escapeHtml(err.message)}`;
    if (dashTbody) dashTbody.innerHTML = `<tr><td colspan="5" style="text-align:center;color:#ef4444;padding:1rem;">${errMsg}</td></tr>`;
    if (manageTbody) manageTbody.innerHTML = `<tr><td colspan="6" style="text-align:center;color:#ef4444;padding:1rem;">${errMsg}</td></tr>`;
  }
}

async function loadMySubmittedRequests() {
  const myReqTbody = document.getElementById('my-submitted-requests-tbody');
  if (!myReqTbody) return;
  myReqTbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:#94a3b8;padding:1rem;">Loading your submitted requests...</td></tr>';

  try {
    const list = await api('/api/requests/my');
    if (!list || list.length === 0) {
      myReqTbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:#64748b;padding:1rem;">You have not submitted any access requests yet.</td></tr>';
      return;
    }
    myReqTbody.innerHTML = list.map(r => `
      <tr>
        <td>${escapeHtml(r.documentTitle)} (#${r.documentId})</td>
        <td><span class="access-pill shared">${escapeHtml(r.requestedPermission)}</span></td>
        <td>${escapeHtml(r.reason || '-')}</td>
        <td><strong style="color:${r.status === 'APPROVED' ? '#16a34a' : r.status === 'REJECTED' ? '#ef4444' : '#d97706'};">${escapeHtml(r.status)}</strong></td>
        <td>${new Date(r.createdAt).toLocaleString()}</td>
      </tr>
    `).join('');
  } catch (err) {
    myReqTbody.innerHTML = `<tr><td colspan="5" style="text-align:center;color:#ef4444;padding:1rem;">Error loading requests: ${escapeHtml(err.message)}</td></tr>`;
  }
}

function openRequestAccessModal(docId = 1) {
  const docInput = document.getElementById('req-doc-id');
  if (docInput && docId) docInput.value = docId;
  const modal = document.getElementById('modal-request-access');
  if (modal) modal.classList.add('open');
}

async function submitAccessRequest() {
  const documentId = Number(document.getElementById('req-doc-id').value);
  const requestedPermission = document.getElementById('req-perm-type').value;
  const reason = document.getElementById('req-reason').value;

  if (!documentId || documentId <= 0) {
    showToast('Please enter a valid Document ID.', 'error');
    return;
  }

  try {
    showToast('Submitting access request to document owner...', 'info');
    await api('/api/requests', {
      method: 'POST',
      body: JSON.stringify({ documentId, requestedPermission, reason })
    });
    showToast('Access request submitted and recorded in audit trail!', 'success');
    closeModal('modal-request-access');
    loadPendingAccessRequests();
    loadMySubmittedRequests();
  } catch (err) {
    showToast(err.message || 'Failed to submit access request.', 'error');
  }
}

async function reviewAccessRequest(requestId, approve) {
  try {
    showToast(`${approve ? 'Approving' : 'Rejecting'} access request #${requestId}...`, 'info');
    await api(`/api/requests/${requestId}/review?approve=${approve}`, {
      method: 'POST'
    });
    showToast(`Access request #${requestId} ${approve ? 'approved (permission granted)' : 'rejected'}!`, 'success');
    loadPendingAccessRequests();
    loadMySubmittedRequests();
    if (currentView === 'access-control') loadAccessControlView();
    if (currentView === 'dashboard') loadDashboardData();
  } catch (err) {
    showToast(err.message || 'Failed to review access request.', 'error');
  }
}

// Upload Trigger & Handling
function triggerFileInput() {
  document.getElementById('dashboard-file-input').click();
}

async function handleFileSelect(e) {
  const file = e.target.files[0];
  if (!file) return;

  const formData = new FormData();
  formData.append('title', file.name);
  formData.append('category', 'General');
  formData.append('file', file);

  try {
    showToast(`Encrypting & uploading '${file.name}' with magic-byte validation (max 10MB)...`, 'info');
    await api('/api/documents/upload', {
      method: 'POST',
      body: formData,
      isFormData: true
    });
    showToast(`'${file.name}' securely uploaded! Chained in audit ledger.`, 'success');
    document.getElementById('dashboard-file-input').value = '';
    loadDashboardData();
  } catch (err) {
    showToast(err.message || 'File upload failed.', 'error');
  }
}

// All Documents View
async function loadAllDocumentsView(type) {
  const targetPanel = document.getElementById('view-my-docs');
  if (targetPanel) targetPanel.style.display = 'block';

  const tbody = document.getElementById('all-docs-tbody');
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding:1.5rem; color:#94a3b8;">Loading documents repository...</td></tr>';

  let endpoint = '/api/documents/my';
  if (type === 'shared-with-me') endpoint = '/api/documents/shared';

  try {
    const list = await api(endpoint);
    if (!list || list.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding:2rem; color:#64748b;">No documents found in this view.</td></tr>';
      return;
    }

    tbody.innerHTML = list.map(d => `
      <tr>
        <td>
          <div class="doc-name-cell">
            <div class="file-badge-icon pdf">FILE</div>
            <span>${escapeHtml(d.title)} <small style="color:#64748b;">(#${d.id})</small></span>
          </div>
        </td>
        <td>${escapeHtml(d.category)}</td>
        <td>${escapeHtml(d.ownerUsername)}</td>
        <td>${(d.fileSize / 1024).toFixed(1)} KB</td>
        <td>${new Date(d.createdAt).toLocaleDateString()}</td>
        <td><span class="access-pill shared">${escapeHtml(d.myPermission)}</span></td>
        <td style="text-align: right; display:flex; gap:0.4rem; justify-content:flex-end;">
          <button class="btn btn-sm btn-primary" onclick="downloadDocument(${d.id})">Download</button>
          <button class="btn btn-sm" onclick="openEditDocumentModal(${d.id})">Edit</button>
          <button class="btn btn-sm" onclick="openRequestAccessModal(${d.id})">Request Access</button>
        </td>
      </tr>
    `).join('');
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="7" style="text-align:center; color:#ef4444;">Failed to load documents: ${escapeHtml(e.message)}</td></tr>`;
  }
}

// Access Control View (Pending Requests + My Requests + Active Permissions)
async function loadAccessControlView() {
  loadPendingAccessRequests();
  loadMySubmittedRequests();

  const tbody = document.getElementById('access-control-tbody');
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding:1.5rem; color:#94a3b8;">Loading active permissions from backend...</td></tr>';

  try {
    const myDocs = await api('/api/documents/my').catch(() => []);
    if (!myDocs || myDocs.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding:1.5rem; color:#64748b;">No owned documents to manage permissions for.</td></tr>';
      return;
    }

    const permArrays = await Promise.all(
      myDocs.map(d => api(`/api/permissions/documents/${d.id}`).catch(() => []))
    );
    const allPerms = permArrays.flat();

    if (allPerms.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding:1.5rem; color:#64748b;">No permissions granted on your documents yet.</td></tr>';
      return;
    }

    tbody.innerHTML = allPerms.map(p => `
      <tr>
        <td><strong>${escapeHtml(p.documentTitle)}</strong> (#${p.documentId})</td>
        <td>${escapeHtml(p.userFullName || p.username)} (${escapeHtml(p.username)})</td>
        <td><span class="access-pill shared">${escapeHtml(p.permissionType)}</span></td>
        <td>${p.grantedAt ? new Date(p.grantedAt).toLocaleDateString() : '-'}</td>
        <td>${p.expiresAt ? new Date(p.expiresAt).toLocaleDateString() : 'Permanent'}</td>
        <td><span style="color:${p.active ? '#16a34a' : '#ef4444'}; font-weight:700;">${p.active ? 'ACTIVE' : 'REVOKED'}</span></td>
        <td style="text-align:right;">
          ${p.active ? `<button class="btn btn-sm" style="background:#ef4444;color:#fff;" onclick="revokeDocumentPermission(${p.documentId}, ${p.id})">Revoke</button>` : '<span style="color:#94a3b8;">Revoked</span>'}
        </td>
      </tr>
    `).join('');
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="7" style="text-align:center; color:#ef4444;">Failed to load permissions: ${escapeHtml(err.message)}</td></tr>`;
  }
}

async function revokeDocumentPermission(docId, permId) {
  try {
    showToast('Revoking permission immediately...', 'info');
    await api(`/api/permissions/documents/${docId}/revoke/${permId}`, {
      method: 'DELETE'
    });
    showToast('Permission revoked immediately! Access terminated.', 'success');
    loadAccessControlView();
  } catch (err) {
    showToast(err.message || 'Failed to revoke permission.', 'error');
  }
}

// Audit Logs View & Tamper Verification
async function loadAuditLogsView() {
  const tbody = document.getElementById('audit-table-tbody');
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="8" style="text-align:center; padding:1.5rem; color:#94a3b8;">Loading cryptographically chained ledger...</td></tr>';

  try {
    const logs = await api('/api/admin/audit-logs');
    if (!logs || logs.length === 0) {
      tbody.innerHTML = '<tr><td colspan="8" style="text-align:center; padding:2rem; color:#64748b;">Ledger is empty.</td></tr>';
      return;
    }

    tbody.innerHTML = logs.map(l => `
      <tr>
        <td><strong>#${l.id}</strong></td>
        <td>${new Date(l.timestamp).toLocaleString()}</td>
        <td><span class="access-pill ${l.success ? 'shared' : 'private'}">${escapeHtml(l.action)}</span></td>
        <td>${escapeHtml(l.username)}</td>
        <td>${escapeHtml(l.resourceType)}</td>
        <td>${escapeHtml(l.ipAddress || '127.0.0.1')}</td>
        <td><strong style="color:${l.success ? '#16a34a' : '#ef4444'};">${l.success ? 'SUCCESS' : 'BLOCKED'}</strong></td>
        <td style="font-family:monospace; font-size:0.72rem; color:#64748b;">${escapeHtml((l.previousHash || '').substring(0, 10))}... &rarr; ${escapeHtml((l.currentHash || '').substring(0, 10))}...</td>
      </tr>
    `).join('');
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="8" style="text-align:center; color:#ef4444; padding:1.5rem;">Admin privileges required to view full audit ledger (${escapeHtml(e.message)}).</td></tr>`;
  }
}

async function runTamperProofCheck() {
  try {
    showToast('Calculating mathematical SHA-256 chain verification...', 'info');
    const result = await api('/api/tamper/verify');
    const banner = document.getElementById('tamper-banner-alert');
    const text = document.getElementById('tamper-banner-text');

    if (result.valid) {
      if (banner) banner.className = 'tamper-alert-box verified';
      if (text) text.innerText = `100% LEDGER INTEGRITY VERIFIED (${result.totalRecordsChecked} BLOCKS VERIFIED)`;
      showToast('All audit ledger blocks mathematically verified! Zero tampering detected.', 'success');
    } else {
      if (banner) {
        banner.className = 'tamper-alert-box';
        banner.style.background = '#fef2f2';
        banner.style.color = '#991b1b';
      }
      if (text) text.innerText = `TAMPER DETECTED AT BLOCK #${result.brokenRecordId}!`;
      showToast(`Tamper Alert: Block #${result.brokenRecordId} modified!`, 'error');
    }
  } catch (e) {
    showToast(e.message || 'Admin role required to run tamper verification.', 'error');
  }
}

// Download Document with Watermark
async function downloadDocument(id) {
  try {
    showToast('Applying dynamic watermark (user, timestamp, IP) and downloading...', 'info');
    const response = await fetch(`/api/documents/${id}/download?watermark=true`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });

    if (response.status === 403) {
      showToast('Security Alert: Access Forbidden by IDOR policy.', 'error');
      return;
    }
    if (!response.ok) {
      showToast('Download failed.', 'error');
      return;
    }
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `TrustVault_Watermarked_${id}.pdf`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    showToast('Download complete with forensic watermark.', 'success');
  } catch (e) {
    showToast('Download failed.', 'error');
  }
}

// Share Modal (Connected to POST /api/permissions/documents/{id}/grant)
async function openShareModal(preselectedDocId = null) {
  const select = document.getElementById('share-doc-select');
  if (select) {
    const myDocs = await api('/api/documents/my').catch(() => []);
    const list = (myDocs && myDocs.length > 0) ? myDocs : documentsList;
    if (list && list.length > 0) {
      select.innerHTML = list.map(d => `<option value="${d.id}" ${d.id === preselectedDocId ? 'selected' : ''}>${escapeHtml(d.title)} (#${d.id})</option>`).join('');
    } else {
      select.innerHTML = `<option value="1">Document #1</option>`;
    }
  }
  document.getElementById('modal-share').classList.add('open');
}

async function executeShareDocument() {
  const docId = document.getElementById('share-doc-select').value;
  const username = document.getElementById('share-user-select').value;
  const permissionType = document.getElementById('share-perm-select') ? document.getElementById('share-perm-select').value : 'READ';

  try {
    await api(`/api/permissions/documents/${docId}/grant`, {
      method: 'POST',
      body: JSON.stringify({ username, permissionType, expiresAt: null })
    });
    showToast(`Access granted to ${username} (${permissionType}). Logged in audit trail.`, 'success');
    closeModal('modal-share');
    if (currentView === 'access-control') loadAccessControlView();
    if (currentView === 'dashboard') loadDashboardData();
  } catch (err) {
    showToast(err.message || 'Failed to grant permission.', 'error');
  }
}

function openUserManagementModal() {
  showToast('User Management: 3 enterprise roles active (Admin, Owner, Viewer).', 'info');
}

function openEditDocumentModal(docId) {
  const doc = documentsList.find(d => d.id === docId) || { id: docId, title: '', category: 'General', description: '' };
  document.getElementById('edit-doc-id').value = doc.id;
  document.getElementById('edit-doc-title').value = doc.title || '';
  document.getElementById('edit-doc-category').value = doc.category || 'General';
  document.getElementById('edit-doc-description').value = doc.description || '';
  document.getElementById('modal-edit-doc').classList.add('open');
}

async function submitEditDocument() {
  const docId = document.getElementById('edit-doc-id').value;
  const title = document.getElementById('edit-doc-title').value.trim();
  const category = document.getElementById('edit-doc-category').value.trim();
  const description = document.getElementById('edit-doc-description').value.trim();

  try {
    await api(`/api/documents/${docId}`, {
      method: 'PUT',
      body: JSON.stringify({ title, category, description })
    });
    showToast(`Document #${docId} updated successfully!`, 'success');
    closeModal('modal-edit-doc');
    loadDashboardData();
    if (currentView !== 'dashboard') loadAllDocumentsView(currentView);
  } catch (err) {
    showToast(err.message || 'Access denied: You do not have EDIT permission.', 'error');
  }
}

function closeModal(id) {
  const el = document.getElementById(id);
  if (el) el.classList.remove('open');
}

async function handleGlobalSearch(query) {
  if (!query || query.trim().length < 2) {
    if (currentView === 'dashboard') loadRecentDocuments();
    return;
  }
  try {
    const results = await api(`/api/documents/search?query=${encodeURIComponent(query.trim())}`);
    const tbody = document.getElementById('recent-docs-tbody');
    if (tbody && results) {
      if (results.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:#64748b;padding:1rem;">No matching documents found.</td></tr>';
      } else {
        tbody.innerHTML = results.map(d => `
          <tr>
            <td><strong>${escapeHtml(d.title)}</strong> (#${d.id})</td>
            <td>${escapeHtml(d.ownerUsername)}</td>
            <td>${new Date(d.updatedAt || d.createdAt).toLocaleDateString()}</td>
            <td><span class="access-pill shared">${escapeHtml(d.myPermission)}</span></td>
            <td style="text-align:right;">
              <button class="btn btn-sm btn-primary" onclick="downloadDocument(${d.id})">Download</button>
              <button class="btn btn-sm" onclick="openRequestAccessModal(${d.id})">Request Access</button>
            </td>
          </tr>
        `).join('');
      }
    }
  } catch (e) {}
}

function handleDocumentAction(id) {
  downloadDocument(id);
}

function handleUserMenuToggle() {
  window.location.href = 'login.html';
}

function showToast(message, type = 'info') {
  const container = document.getElementById('toast-container');
  if (!container) return;
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.style.cssText = 'background:#1e293b;color:#fff;padding:0.75rem 1rem;border-radius:6px;box-shadow:0 4px 12px rgba(0,0,0,0.15);font-size:0.85rem;';
  toast.innerText = message;
  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

// Initialize on Load
document.addEventListener('DOMContentLoaded', () => {
  updateUserInterface();
  loadDashboardData();
});
