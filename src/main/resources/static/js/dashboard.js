// State & Credentials
let currentUser = JSON.parse(
  localStorage.getItem('trustvault_user') ||
  sessionStorage.getItem('trustvault_user') ||
  'null'
);
let token =
  localStorage.getItem('trustvault_token') ||
  sessionStorage.getItem('trustvault_token') ||
  null;

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
    clearClientSession();
    showToast('Session expired or unauthorized. Redirecting to login...', 'error');
    setTimeout(() => {
      window.location.replace('login.html');
    }, 300);
    return null;
  }
  if (!response.ok) {
    const err = await response.json().catch(() => ({ message: 'Request failed' }));
    throw new Error(err.message || 'Error');
  }
  if (response.status === 204 || response.headers.get('content-length') === '0') {
    return true;
  }
  return await response.json();
}

function clearClientSession() {
  token = null;
  currentUser = null;
  localStorage.removeItem('trustvault_token');
  localStorage.removeItem('trustvault_user');
  sessionStorage.removeItem('trustvault_token');
  sessionStorage.removeItem('trustvault_user');
  sessionStorage.setItem('trustvault_logged_out', 'true');
  try {
    document.cookie = 'trustvault_token=; Path=/; Expires=Thu, 01 Jan 1970 00:00:01 GMT; SameSite=Lax;';
  } catch (e) {}
}

async function handleLogout() {
  try {
    if (token) {
      await fetch('/api/auth/logout', {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
      }).catch(() => {});
    }
  } finally {
    clearClientSession();
    showToast('Signed out securely.', 'info');
    setTimeout(() => {
      window.location.replace('login.html');
    }, 250);
  }
}

function isViewerRole() {
  if (!currentUser) return false;
  const r = String(currentUser.role || '').toUpperCase();
  return r.includes('VIEWER') || currentUser.username === 'viewer';
}

function isAdminRole() {
  if (!currentUser) return false;
  const r = String(currentUser.role || '').toUpperCase();
  return r.includes('ADMIN') || currentUser.username === 'admin';
}

function updateUserInterface() {
  if (!currentUser) return;

  const avatarEl = document.getElementById('header-avatar');
  const nameEl = document.getElementById('header-username');
  const roleEl = document.getElementById('header-role');
  const welcomeEl = document.getElementById('welcome-user-name');

  const initials = currentUser.initials || (currentUser.fullName ? currentUser.fullName.charAt(0) : currentUser.username.charAt(0)).toUpperCase();
  if (avatarEl) avatarEl.innerText = initials;
  if (nameEl) nameEl.innerText = currentUser.fullName || currentUser.username;
  if (roleEl) roleEl.innerText = currentUser.role || 'User';
  if (welcomeEl) welcomeEl.innerText = currentUser.fullName || currentUser.username;

  const badgeEl = document.getElementById('active-session-badge');
  if (badgeEl) {
    badgeEl.innerText = `${currentUser.fullName || currentUser.username} (${currentUser.role || 'ROLE_VIEWER'})`;
  }

  // Role-based UI visibility for upload button
  const uploadBtn = document.getElementById('uploadBtn');
  if (uploadBtn) {
    uploadBtn.style.display = isViewerRole() ? 'none' : 'inline-flex';
  }
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

  if (viewId === 'my-docs' || viewId === 'shared-with-me' || viewId === 'shared-by-me') {
    const panel = document.getElementById('view-my-docs');
    if (panel) panel.style.display = 'block';
    loadAllDocumentsView(viewId);
    return;
  }

  const targetPanel = document.getElementById(`view-${viewId}`);
  if (targetPanel) {
    targetPanel.style.display = 'block';
  }

  if (viewId === 'dashboard') loadDashboardData();
  if (viewId === 'access-control') loadAccessControlView();
  if (viewId === 'audit-logs') loadAuditLogsView();
  if (viewId === 'recycle-bin') loadRecycleBinView();
  if (viewId === 'settings') loadSettingsView();
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
      isAdminRole() ? api('/api/admin/stats').catch(() => null) : Promise.resolve(null),
      isAdminRole() ? api('/api/admin/users').catch(() => null) : Promise.resolve(null),
      isAdminRole() ? api('/api/admin/audit-logs').catch(() => null) : Promise.resolve(null)
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
        postureBox.innerHTML = `<span style="color:var(--text-muted);">Signed in as <strong>${escapeHtml(currentUser?.username || 'user')}</strong> (${escapeHtml(currentUser?.role || 'User')}). Switch to Admin to view system-wide security metrics and full audit ledger.</span>`;
      }
    }

    if (recentActList) {
      if (logs && logs.length > 0) {
        recentActList.innerHTML = logs.slice(0, 6).map(l => `
          <li style="padding:0.65rem 1rem;border-bottom:1px solid var(--border-color);display:flex;justify-content:space-between;align-items:center;font-size:0.85rem;">
            <div>
              <strong style="color:${l.success ? 'var(--text-dark)' : '#ef4444'};">[${escapeHtml(l.action)}]</strong>
              <span>${escapeHtml(l.username)} &mdash; ${escapeHtml(l.details)}</span>
            </div>
            <span style="color:var(--text-muted);font-size:0.78rem;">${new Date(l.timestamp).toLocaleString()}</span>
          </li>
        `).join('');
      } else {
        const userActivities = await api('/api/recent-activities').catch(() => []);
        if (userActivities && userActivities.length > 0) {
          recentActList.innerHTML = userActivities.slice(0, 6).map(a => `
            <li style="padding:0.65rem 1rem;border-bottom:1px solid var(--border-color);display:flex;justify-content:space-between;align-items:center;font-size:0.85rem;">
              <span>${escapeHtml(a.description)}</span>
              <span style="color:var(--text-muted);font-size:0.78rem;">${escapeHtml(a.timestamp)}</span>
            </li>
          `).join('');
        } else {
          recentActList.innerHTML = '<li style="padding:0.75rem 1rem;color:var(--text-muted);">No recent audit activity recorded for this account.</li>';
        }
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

function updateStorageMeter(docs) {
  const storageText = document.getElementById('sidebar-storage-text');
  const fillEl = document.querySelector('.sidebar-storage-box .progress-bar-fill');
  if (!storageText) return;
  const totalBytes = (docs || []).reduce((acc, d) => acc + (d.fileSize || 0), 0);
  const kb = (totalBytes / 1024).toFixed(1);
  const maxBytes = 10 * 1024 * 1024; // 10 MB per-file cap reference
  const pct = Math.min(100, Math.max(2, Math.round((totalBytes / maxBytes) * 100)));
  storageText.innerText = `${kb} KB of 10 MB`;
  if (fillEl) fillEl.style.width = `${pct}%`;
}

// Populate Recent Documents from Backend APIs
async function loadRecentDocuments() {
  const tbody = document.getElementById('recent-docs-tbody');
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; color:#94a3b8; padding:1.5rem;">Loading documents...</td></tr>';

  try {
    const requests = [
      api('/api/documents/my').catch(() => []),
      api('/api/documents/shared').catch(() => [])
    ];
    if (isAdminRole()) {
      requests.push(api('/api/documents/search').catch(() => []));
    }
    const [myDocs, sharedDocs, allDocs] = await Promise.all(requests);

    const combinedMap = new Map();
    (myDocs || []).forEach(d => combinedMap.set(d.id, d));
    (sharedDocs || []).forEach(d => combinedMap.set(d.id, d));
    (allDocs || []).forEach(d => combinedMap.set(d.id, d));
    documentsList = Array.from(combinedMap.values());
    updateStorageMeter(documentsList);

    if (documentsList.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="5" style="text-align:center; color:var(--text-muted); padding:1.5rem;">
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
      const canManage = isOwner || isAdminRole();
      const accessLabel = isOwner ? 'OWNER' : (d.myPermission || 'SHARED');

      return `
        <tr>
          <td>
            <div class="doc-name-cell">
              <div class="file-badge-icon ${type}">${type.toUpperCase()}</div>
              <span>${escapeHtml(d.title)} <small style="color:var(--text-muted);">(#${d.id} • v${d.currentVersion || 1})</small></span>
            </div>
          </td>
          <td>${escapeHtml(isOwner ? 'You (' + d.ownerUsername + ')' : d.ownerUsername)}</td>
          <td>${escapeHtml(modified)}</td>
          <td>
            <span class="access-pill ${isOwner ? 'private' : 'shared'}">
              ${escapeHtml(accessLabel)}
            </span>
          </td>
          <td style="text-align: right; display:flex; gap:0.35rem; justify-content:flex-end; flex-wrap:wrap;">
            <button class="btn btn-sm" onclick="previewDocument(${d.id})" title="Preview in browser">Preview</button>
            <button class="btn btn-sm btn-primary" onclick="downloadDocument(${d.id})">Download</button>
            <button class="btn btn-sm" onclick="openVersionsModal(${d.id}, '${escapeHtml(d.title)}')">Versions</button>
            ${d.canEdit || canManage ? `<button class="btn btn-sm" onclick="openEditDocumentModal(${d.id})">Edit</button>` : ''}
            ${canManage ? `<button class="btn btn-sm" onclick="openShareModal(${d.id})">Share</button>` : `<button class="btn btn-sm" onclick="openRequestAccessModal(${d.id})">Request Access</button>`}
            ${canManage ? `<button class="btn btn-sm" style="background:#ef4444;color:#fff;" onclick="deleteDocument(${d.id})">Delete</button>` : ''}
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
      if (dashTbody) dashTbody.innerHTML = `<tr><td colspan="5" style="text-align:center;color:var(--text-muted);padding:1rem;">${emptyMsg}</td></tr>`;
      if (manageTbody) manageTbody.innerHTML = `<tr><td colspan="6" style="text-align:center;color:var(--text-muted);padding:1rem;">${emptyMsg}</td></tr>`;
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
      myReqTbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:var(--text-muted);padding:1rem;">You have not submitted any access requests yet.</td></tr>';
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
  if (isViewerRole()) {
    showToast('Security Block: Viewers are not permitted to upload documents.', 'error');
    return;
  }
  const modal = document.getElementById('modal-upload-doc');
  if (modal) {
    modal.classList.add('open');
  } else {
    document.getElementById('dashboard-file-input').click();
  }
}

async function submitUploadModal() {
  const titleInput = document.getElementById('upload-modal-title');
  const categoryInput = document.getElementById('upload-modal-category');
  const descInput = document.getElementById('upload-modal-desc');
  const fileInput = document.getElementById('upload-modal-file');

  const file = fileInput?.files?.[0];
  if (!file) {
    showToast('Please select a file to upload.', 'error');
    return;
  }
  const title = (titleInput?.value || '').trim() || file.name;
  const category = (categoryInput?.value || 'General').trim();
  const description = (descInput?.value || '').trim();

  const formData = new FormData();
  formData.append('title', title);
  formData.append('category', category);
  formData.append('description', description);
  formData.append('file', file);

  try {
    showToast(`Encrypting & uploading '${title}' with magic-byte validation...`, 'info');
    await api('/api/documents/upload', {
      method: 'POST',
      body: formData,
      isFormData: true
    });
    showToast(`'${title}' securely uploaded and chained in audit ledger!`, 'success');
    closeModal('modal-upload-doc');
    if (titleInput) titleInput.value = '';
    if (descInput) descInput.value = '';
    if (fileInput) fileInput.value = '';
    loadDashboardData();
    if (currentView === 'my-docs') loadAllDocumentsView('my-docs');
  } catch (err) {
    showToast(err.message || 'File upload failed.', 'error');
  }
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

// All Documents View (My Documents / Shared with Me / Shared by Me)
async function loadAllDocumentsView(type) {
  const targetPanel = document.getElementById('view-my-docs');
  if (targetPanel) targetPanel.style.display = 'block';

  const heading = document.getElementById('docs-view-heading');
  if (heading) {
    heading.innerText = type === 'shared-with-me'
      ? 'Shared with Me'
      : type === 'shared-by-me'
        ? 'Shared by Me'
        : 'My Documents';
  }

  const tbody = document.getElementById('all-docs-tbody');
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding:1.5rem; color:#94a3b8;">Loading documents repository...</td></tr>';

  try {
    let list = [];
    if (type === 'shared-with-me') {
      list = await api('/api/documents/shared');
    } else if (type === 'shared-by-me') {
      const myDocs = await api('/api/documents/my');
      const filtered = [];
      for (const d of (myDocs || [])) {
        const perms = await api(`/api/permissions/documents/${d.id}`).catch(() => []);
        if (perms && perms.some(p => p.active)) {
          filtered.push(d);
        }
      }
      list = filtered;
    } else {
      list = await api('/api/documents/my');
      if (isAdminRole() && (!list || list.length === 0)) {
        list = await api('/api/documents/search');
      }
    }

    if (!list || list.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding:2rem; color:var(--text-muted);">No documents found in this view.</td></tr>';
      return;
    }

    tbody.innerHTML = list.map(d => {
      const isOwner = d.owner || (currentUser && d.ownerUsername === currentUser.username);
      const canManage = isOwner || isAdminRole();
      return `
        <tr>
          <td>
            <div class="doc-name-cell">
              <div class="file-badge-icon pdf">FILE</div>
              <span>${escapeHtml(d.title)} <small style="color:var(--text-muted);">(#${d.id} • v${d.currentVersion || 1})</small></span>
            </div>
          </td>
          <td>${escapeHtml(d.category)}</td>
          <td>${escapeHtml(d.ownerUsername)}</td>
          <td>${(d.fileSize / 1024).toFixed(1)} KB</td>
          <td>${new Date(d.createdAt).toLocaleDateString()}</td>
          <td><span class="access-pill shared">${escapeHtml(d.myPermission)}</span></td>
          <td style="text-align: right; display:flex; gap:0.35rem; justify-content:flex-end; flex-wrap:wrap;">
            <button class="btn btn-sm" onclick="previewDocument(${d.id})">Preview</button>
            <button class="btn btn-sm btn-primary" onclick="downloadDocument(${d.id})">Download</button>
            <button class="btn btn-sm" onclick="openVersionsModal(${d.id}, '${escapeHtml(d.title)}')">Versions</button>
            ${d.canEdit || canManage ? `<button class="btn btn-sm" onclick="openEditDocumentModal(${d.id})">Edit</button>` : ''}
            ${canManage ? `<button class="btn btn-sm" onclick="openShareModal(${d.id})">Share</button>` : `<button class="btn btn-sm" onclick="openRequestAccessModal(${d.id})">Request Access</button>`}
            ${canManage ? `<button class="btn btn-sm" style="background:#ef4444;color:#fff;" onclick="deleteDocument(${d.id})">Delete</button>` : ''}
          </td>
        </tr>
      `;
    }).join('');
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="7" style="text-align:center; color:#ef4444;">Failed to load documents: ${escapeHtml(e.message)}</td></tr>`;
  }
}

// Recycle Bin View (Soft-Deleted Documents)
async function loadRecycleBinView() {
  const tbody = document.getElementById('recycle-bin-tbody');
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; padding:1.5rem; color:#94a3b8;">Loading recycle bin...</td></tr>';

  try {
    const deletedDocs = await api('/api/documents/deleted');
    if (!deletedDocs || deletedDocs.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; padding:2rem; color:var(--text-muted);">Recycle bin is empty. Soft-deleted documents will appear here.</td></tr>';
      return;
    }

    tbody.innerHTML = deletedDocs.map(d => `
      <tr>
        <td><strong>${escapeHtml(d.title)}</strong> (#${d.id})</td>
        <td>${escapeHtml(d.category)}</td>
        <td>${escapeHtml(d.ownerUsername)}</td>
        <td>${(d.fileSize / 1024).toFixed(1)} KB</td>
        <td>${new Date(d.updatedAt || d.createdAt).toLocaleString()}</td>
        <td style="text-align:right;">
          <button class="btn btn-sm btn-primary" onclick="restoreDeletedDocument(${d.id})">Restore Document</button>
        </td>
      </tr>
    `).join('');
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="6" style="text-align:center; color:#ef4444; padding:1.5rem;">Failed to load recycle bin: ${escapeHtml(err.message)}</td></tr>`;
  }
}

async function restoreDeletedDocument(docId) {
  try {
    await api(`/api/documents/${docId}/restore`, { method: 'POST' });
    showToast(`Document #${docId} restored from Recycle Bin!`, 'success');
    loadRecycleBinView();
  } catch (err) {
    showToast(err.message || 'Failed to restore document.', 'error');
  }
}

// Settings View
async function loadSettingsView() {
  const box = document.getElementById('settings-profile-box');
  if (!box) return;
  try {
    const profile = await api('/api/auth/me').catch(() => currentUser);
    const u = profile || currentUser || {};
    box.innerHTML = `
      <div style="padding:1rem;border:1px solid var(--border-color);border-radius:10px;">
        <span style="font-size:0.75rem;color:var(--text-muted);display:block;">Full Name</span>
        <strong style="font-size:1rem;">${escapeHtml(u.fullName || 'N/A')}</strong>
      </div>
      <div style="padding:1rem;border:1px solid var(--border-color);border-radius:10px;">
        <span style="font-size:0.75rem;color:var(--text-muted);display:block;">Username</span>
        <strong style="font-size:1rem;">${escapeHtml(u.username || 'N/A')}</strong>
      </div>
      <div style="padding:1rem;border:1px solid var(--border-color);border-radius:10px;">
        <span style="font-size:0.75rem;color:var(--text-muted);display:block;">Email Address</span>
        <strong style="font-size:1rem;">${escapeHtml(u.email || `${u.username}@trustvault.io`)}</strong>
      </div>
      <div style="padding:1rem;border:1px solid var(--border-color);border-radius:10px;">
        <span style="font-size:0.75rem;color:var(--text-muted);display:block;">Assigned Security Role</span>
        <span class="access-pill shared" style="margin-top:0.25rem;">${escapeHtml(u.role || 'USER')}</span>
      </div>
    `;
  } catch (e) {}

  const adminCard = document.getElementById('admin-user-management-card');
  if (adminCard) {
    if (isAdminRole()) {
      adminCard.style.display = 'block';
      await loadAdminUsersTable();
    } else {
      adminCard.style.display = 'none';
    }
  }
}

async function loadAdminUsersTable() {
  const tbody = document.getElementById('admin-users-tbody');
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:1rem;color:#94a3b8;">Loading users...</td></tr>';
  try {
    const users = await api('/api/admin/users');
    if (!users || users.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;padding:1rem;">No users found.</td></tr>';
      return;
    }
    tbody.innerHTML = users.map(u => {
      const isPrimaryAdmin = u.username === 'admin';
      return `
        <tr>
          <td><strong>#${u.id}</strong></td>
          <td>${escapeHtml(u.username)}</td>
          <td>${escapeHtml(u.fullName)}</td>
          <td>${escapeHtml(u.email)}</td>
          <td><span class="access-pill">${escapeHtml(u.oauthProvider || 'LOCAL')}</span></td>
          <td><span class="access-pill shared">${escapeHtml(u.role)}</span></td>
          <td style="text-align:right;">
            ${isPrimaryAdmin ? '<span style="color:#94a3b8;font-size:0.8rem;">System Admin</span>' : `
              <select id="role-select-${u.id}" class="form-input" style="display:inline-block;width:auto;padding:0.25rem 0.5rem;margin-right:0.35rem;">
                <option value="ROLE_VIEWER" ${u.role === 'ROLE_VIEWER' ? 'selected' : ''}>ROLE_VIEWER</option>
                <option value="ROLE_OWNER" ${u.role === 'ROLE_OWNER' ? 'selected' : ''}>ROLE_OWNER</option>
                <option value="ROLE_ADMIN" ${u.role === 'ROLE_ADMIN' ? 'selected' : ''}>ROLE_ADMIN</option>
              </select>
              <button class="btn btn-sm btn-primary" onclick="updateUserRoleByAdmin(${u.id})">Update</button>
            `}
          </td>
        </tr>
      `;
    }).join('');
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="7" style="text-align:center;color:#ef4444;">${escapeHtml(err.message)}</td></tr>`;
  }
}

async function updateUserRoleByAdmin(userId) {
  const select = document.getElementById(`role-select-${userId}`);
  if (!select) return;
  const role = select.value;
  try {
    await api(`/api/admin/users/${userId}/role`, {
      method: 'PUT',
      body: JSON.stringify({ role })
    });
    showToast(`User #${userId} role updated to ${role}.`, 'success');
    await loadAdminUsersTable();
  } catch (err) {
    showToast(err.message || 'Failed to update user role.', 'error');
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
    const docsEndpoint = isAdminRole() ? '/api/documents/search' : '/api/documents/my';
    const myDocs = await api(docsEndpoint).catch(() => []);
    if (!myDocs || myDocs.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding:1.5rem; color:var(--text-muted);">No owned documents to manage permissions for.</td></tr>';
      return;
    }

    const permArrays = await Promise.all(
      myDocs.map(d => api(`/api/permissions/documents/${d.id}`).catch(() => []))
    );
    const allPerms = permArrays.flat();

    if (allPerms.length === 0) {
      tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; padding:1.5rem; color:var(--text-muted);">No permissions granted on your documents yet.</td></tr>';
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
      tbody.innerHTML = '<tr><td colspan="8" style="text-align:center; padding:2rem; color:var(--text-muted);">Ledger is empty.</td></tr>';
      return;
    }

    tbody.innerHTML = logs.map(l => `
      <tr>
        <td><strong>#${l.id}</strong></td>
        <td>${new Date(l.timestamp).toLocaleString()}</td>
        <td><span class="access-pill ${l.success ? 'shared' : 'private'}">${escapeHtml(l.action)}</span></td>
        <td>${escapeHtml(l.username)}</td>
        <td>${escapeHtml(l.resourceType)}${l.resourceId ? ' #' + escapeHtml(l.resourceId) : ''}</td>
        <td>${escapeHtml(l.ipAddress || '127.0.0.1')}</td>
        <td><strong style="color:${l.success ? '#16a34a' : '#ef4444'};">${l.success ? 'SUCCESS' : 'BLOCKED'}</strong></td>
        <td style="font-family:monospace; font-size:0.72rem; color:var(--text-muted);">${escapeHtml((l.previousHash || '').substring(0, 10))}... &rarr; ${escapeHtml((l.currentHash || '').substring(0, 10))}...</td>
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

// Preview Document Inline
async function previewDocument(id) {
  try {
    showToast(`Loading watermarked preview for Document #${id}...`, 'info');
    const response = await fetch(`/api/documents/${id}/download?watermark=true&inline=true`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    if (response.status === 403) {
      showToast('Security Alert: Access Forbidden by IDOR policy.', 'error');
      return;
    }
    if (!response.ok) {
      showToast('Preview failed.', 'error');
      return;
    }
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    window.open(url, '_blank', 'noopener,noreferrer');
    setTimeout(() => window.URL.revokeObjectURL(url), 30000);
  } catch (e) {
    showToast('Failed to preview document.', 'error');
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
    window.URL.revokeObjectURL(url);
    showToast('Download complete with forensic watermark.', 'success');
  } catch (e) {
    showToast('Download failed.', 'error');
  }
}

// Delete Document (Soft Delete -> Recycle Bin)
async function deleteDocument(id) {
  if (!confirm(`Move Document #${id} to the Recycle Bin? This action is recorded in the cryptographic audit trail.`)) return;
  try {
    await api(`/api/documents/${id}`, { method: 'DELETE' });
    showToast(`Document #${id} moved to Recycle Bin.`, 'success');
    if (currentView === 'dashboard') loadDashboardData();
    else loadAllDocumentsView(currentView);
  } catch (err) {
    showToast(err.message || 'Access denied: Only the document owner can delete this document.', 'error');
  }
}

// Document Version History Modal
async function openVersionsModal(docId, docTitle) {
  document.getElementById('versions-doc-id').value = docId;
  document.getElementById('versions-modal-title').innerText = docTitle || `Document #${docId}`;
  const doc = documentsList.find(d => d.id === docId);
  const uploadBox = document.getElementById('versions-upload-box');
  if (uploadBox) {
    const canEditDoc = !doc || doc.canEdit || doc.owner || isAdminRole();
    uploadBox.style.display = canEditDoc ? 'block' : 'none';
  }
  document.getElementById('modal-versions').classList.add('open');
  await loadDocumentVersions(docId);
}

async function loadDocumentVersions(docId) {
  const tbody = document.getElementById('versions-modal-tbody');
  if (!tbody) return;
  tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:#94a3b8;padding:1rem;">Loading version history...</td></tr>';
  try {
    const versions = await api(`/api/documents/${docId}/versions`);
    if (!versions || versions.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;color:var(--text-muted);padding:1rem;">No version records found.</td></tr>';
      return;
    }
    tbody.innerHTML = versions.map(v => `
      <tr>
        <td><strong>v${v.versionNumber}</strong></td>
        <td>${escapeHtml(v.originalFilename)}</td>
        <td>${escapeHtml(v.uploadedBy?.username || 'owner')}</td>
        <td style="font-family:monospace;font-size:0.75rem;">${escapeHtml((v.fileChecksum || '').substring(0, 14))}...</td>
        <td>${escapeHtml(v.changeSummary || '-')}</td>
        <td>${new Date(v.createdAt).toLocaleString()}</td>
      </tr>
    `).join('');
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;color:#ef4444;padding:1rem;">${escapeHtml(err.message)}</td></tr>`;
  }
}

async function submitNewDocumentVersion() {
  const docId = document.getElementById('versions-doc-id').value;
  const fileInput = document.getElementById('version-new-file');
  const summaryInput = document.getElementById('version-change-summary');

  if (!fileInput?.files?.length) {
    showToast('Please select a file for the new version.', 'error');
    return;
  }

  const formData = new FormData();
  formData.append('file', fileInput.files[0]);
  formData.append('changeSummary', (summaryInput?.value || '').trim() || 'Updated document version');

  try {
    await api(`/api/documents/${docId}/versions`, {
      method: 'POST',
      body: formData,
      isFormData: true
    });
    showToast('New document version uploaded and checksum chained!', 'success');
    fileInput.value = '';
    if (summaryInput) summaryInput.value = '';
    await loadDocumentVersions(docId);
    if (currentView === 'dashboard') loadDashboardData();
    else loadAllDocumentsView(currentView);
  } catch (err) {
    showToast(err.message || 'Failed to upload new version.', 'error');
  }
}

// Share Modal (Connected to POST /api/permissions/documents/{id}/grant)
async function openShareModal(preselectedDocId = null) {
  const select = document.getElementById('share-doc-select');
  if (select) {
    const myDocs = await api(isAdminRole() ? '/api/documents/search' : '/api/documents/my').catch(() => []);
    const list = (myDocs && myDocs.length > 0) ? myDocs : documentsList;
    if (list && list.length > 0) {
      select.innerHTML = list.map(d => `<option value="${d.id}" ${d.id === preselectedDocId ? 'selected' : ''}>${escapeHtml(d.title)} (#${d.id})</option>`).join('');
    } else {
      select.innerHTML = `<option value="1">Document #1</option>`;
    }
  }
  const userSelect = document.getElementById('share-user-select');
  if (userSelect) {
    const users = await api('/api/permissions/users').catch(() => null);
    if (users && users.length > 0) {
      userSelect.innerHTML = users
        .filter(u => !currentUser || u.username !== currentUser.username)
        .map(u => `<option value="${escapeHtml(u.username)}">${escapeHtml(u.fullName)} (${escapeHtml(u.username)} • ${escapeHtml(u.role)})</option>`)
        .join('');
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

  if (!title) {
    showToast('Document title cannot be empty.', 'error');
    return;
  }

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
        tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:var(--text-muted);padding:1rem;">No matching documents found.</td></tr>';
      } else {
        tbody.innerHTML = results.map(d => `
          <tr>
            <td><strong>${escapeHtml(d.title)}</strong> (#${d.id})</td>
            <td>${escapeHtml(d.ownerUsername)}</td>
            <td>${new Date(d.updatedAt || d.createdAt).toLocaleDateString()}</td>
            <td><span class="access-pill shared">${escapeHtml(d.myPermission)}</span></td>
            <td style="text-align:right;">
              <button class="btn btn-sm" onclick="previewDocument(${d.id})">Preview</button>
              <button class="btn btn-sm btn-primary" onclick="downloadDocument(${d.id})">Download</button>
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
  handleLogout();
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
document.addEventListener('DOMContentLoaded', async () => {
  if (!token) {
    clearClientSession();
    window.location.replace('login.html');
    return;
  }
  const verifiedProfile = await api('/api/auth/me').catch(() => null);
  if (!verifiedProfile) {
    clearClientSession();
    window.location.replace('login.html');
    return;
  }
  currentUser = {
    id: verifiedProfile.id,
    username: verifiedProfile.username,
    fullName: verifiedProfile.fullName || verifiedProfile.username,
    email: verifiedProfile.email,
    role: verifiedProfile.role,
    initials: (verifiedProfile.fullName ? verifiedProfile.fullName.charAt(0) : verifiedProfile.username.charAt(0)).toUpperCase()
  };
  if (localStorage.getItem('trustvault_token')) {
    localStorage.setItem('trustvault_user', JSON.stringify(currentUser));
  } else {
    sessionStorage.setItem('trustvault_user', JSON.stringify(currentUser));
  }
  document.body.classList.remove('auth-pending');
  updateUserInterface();
  loadDashboardData();
});
