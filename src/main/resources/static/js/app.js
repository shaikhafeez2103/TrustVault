// State Management
const state = {
  token: localStorage.getItem('trustvault_token') || null,
  user: JSON.parse(localStorage.getItem('trustvault_user') || 'null'),
  currentTab: 'vault',
  docViewMode: 'my',
  documents: [],
  selectedDocId: null
};

// API Base Helpers
function getAuthHeaders() {
  const headers = { 'Content-Type': 'application/json' };
  if (state.token) {
    headers['Authorization'] = `Bearer ${state.token}`;
  }
  return headers;
}

async function apiRequest(endpoint, options = {}) {
  const defaultHeaders = {};
  if (state.token) {
    defaultHeaders['Authorization'] = `Bearer ${state.token}`;
  }
  if (!options.isFormData && !options.headers?.['Content-Type']) {
    defaultHeaders['Content-Type'] = 'application/json';
  }

  const mergedOptions = {
    ...options,
    headers: { ...defaultHeaders, ...options.headers }
  };

  try {
    const response = await fetch(endpoint, mergedOptions);
    if (response.status === 401) {
      showToast('Session expired or unauthorized. Please sign in.', 'error');
      logout();
      return null;
    }
    if (response.status === 403) {
      const err = await response.json().catch(() => ({ message: 'Access Denied (403 Forbidden)' }));
      showToast(`Security Block: ${err.message || 'Unauthorized action.'}`, 'error');
      throw new Error(err.message || 'Forbidden');
    }
    if (!response.ok) {
      const err = await response.json().catch(() => ({ message: `HTTP Error ${response.status}` }));
      showToast(`Error: ${err.message || 'Action failed'}`, 'error');
      throw new Error(err.message || 'Error');
    }
    if (response.status === 204 || response.headers.get('content-length') === '0') {
      return true;
    }
    return await response.json();
  } catch (error) {
    console.error(`API Error on ${endpoint}:`, error);
    throw error;
  }
}

// UI Toasts
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

// Quick Demo Login for Evaluators
async function quickLogin(username, password) {
  try {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });
    if (!res.ok) {
      showToast('Login failed. Please check credentials.', 'error');
      return;
    }
    const data = await res.json();
    state.token = data.token;
    state.user = {
      id: data.id,
      username: data.username,
      fullName: data.fullName,
      email: data.email,
      role: data.role
    };
    localStorage.setItem('trustvault_token', data.token);
    localStorage.setItem('trustvault_user', JSON.stringify(state.user));

    updateDemoChips(username);
    renderUserNav();
    showToast(`Logged in as ${data.fullName} (${data.role})`, 'success');

    // Reload active view
    loadVaultDocuments();
    if (state.currentTab === 'requests') loadAccessRequests();
    if (state.currentTab === 'audit') loadAuditLogs();
    if (state.currentTab === 'soc') loadSocData();
  } catch (err) {
    showToast('Failed to connect to authentication server.', 'error');
  }
}

function updateDemoChips(activeUsername) {
  document.querySelectorAll('.role-switcher .btn-chip').forEach(btn => {
    if (btn.innerText.toLowerCase().includes(activeUsername.toLowerCase())) {
      btn.classList.add('active');
    } else {
      btn.classList.remove('active');
    }
  });
}

function renderUserNav() {
  const userPill = document.getElementById('user-profile-pill');
  const authBtn = document.getElementById('auth-btn');

  if (state.user) {
    userPill.style.display = 'flex';
    document.getElementById('user-avatar').innerText = state.user.fullName.charAt(0);
    document.getElementById('user-display-name').innerText = state.user.fullName;
    document.getElementById('user-display-role').innerText = state.user.role;
    authBtn.innerText = 'Logout';
    authBtn.onclick = logout;

    // Show or hide admin tabs
    const isAdmin = state.user.role === 'ROLE_ADMIN';
    const auditTabBtn = document.getElementById('tab-audit-btn');
    const socTabBtn = document.getElementById('tab-soc-btn');
    if (auditTabBtn) auditTabBtn.style.display = isAdmin ? 'flex' : 'none';
    if (socTabBtn) socTabBtn.style.display = isAdmin ? 'flex' : 'none';
  } else {
    userPill.style.display = 'none';
    authBtn.innerText = 'Login';
    authBtn.onclick = () => openModal('modal-login');
  }
}

function logout() {
  state.token = null;
  state.user = null;
  localStorage.removeItem('trustvault_token');
  localStorage.removeItem('trustvault_user');
  renderUserNav();
  document.getElementById('documents-grid').innerHTML = '<div style="color:var(--text-muted); padding:2rem;">Please sign in using one of the demo role buttons above.</div>';
  showToast('Logged out securely.', 'info');
}

// Tab Switching
function switchTab(tabId) {
  state.currentTab = tabId;
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.classList.toggle('active', btn.dataset.tab === tabId);
  });
  document.querySelectorAll('.tab-content').forEach(section => {
    section.style.display = section.id === `section-${tabId}` ? 'block' : 'none';
  });

  if (tabId === 'vault') loadVaultDocuments();
  if (tabId === 'requests') loadAccessRequests();
  if (tabId === 'audit') loadAuditLogs();
  if (tabId === 'soc') loadSocData();
}

// Vault Documents
async function loadVaultDocuments() {
  if (!state.token) return;
  const grid = document.getElementById('documents-grid');
  grid.innerHTML = '<div style="color:var(--text-muted); padding:2rem;">Loading documents securely...</div>';

  try {
    let endpoint = '/api/documents/my';
    if (state.docViewMode === 'shared') endpoint = '/api/documents/shared';
    if (state.docViewMode === 'all') endpoint = '/api/documents/search';

    const docs = await apiRequest(endpoint);
    state.documents = docs || [];
    renderDocuments(state.documents);
  } catch (e) {
    grid.innerHTML = '<div style="color:var(--danger); padding:2rem;">Failed to load documents or access restricted.</div>';
  }
}

function changeDocViewMode(mode) {
  state.docViewMode = mode;
  loadVaultDocuments();
}

async function handleSearch() {
  const query = document.getElementById('doc-search-input').value;
  const category = document.getElementById('doc-category-filter').value;

  const params = new URLSearchParams();
  if (query) params.append('query', query);
  if (category) params.append('category', category);

  try {
    const docs = await apiRequest(`/api/documents/search?${params.toString()}`);
    renderDocuments(docs || []);
  } catch (e) {}
}

function renderDocuments(docs) {
  const grid = document.getElementById('documents-grid');
  if (!docs || docs.length === 0) {
    grid.innerHTML = '<div style="color:var(--text-muted); padding:2rem; grid-column:1/-1; text-align:center;">No documents found in this view.</div>';
    return;
  }

  grid.innerHTML = docs.map(doc => {
    const sizeKb = (doc.fileSize / 1024).toFixed(1);
    const dateStr = new Date(doc.createdAt).toLocaleDateString();

    const isOwner = doc.isOwner;
    const canEdit = doc.canEdit;
    const permission = doc.myPermission;

    return `
      <div class="glass-panel doc-card">
        <div>
          <div class="doc-header">
            <div class="doc-icon">
              <svg width="22" height="22" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/></svg>
            </div>
            <div class="doc-meta">
              <h3 title="${escapeHtml(doc.title)}">${escapeHtml(doc.title)}</h3>
              <span class="doc-category-badge">${escapeHtml(doc.category)}</span>
              <span class="badge ${permission === 'OWNER' ? 'badge-success' : permission === 'EDIT' ? 'badge-warning' : 'badge-info'}" style="margin-left:4px;">
                ${permission}
              </span>
            </div>
          </div>
          <p class="doc-desc">${escapeHtml(doc.description || 'No confidential description provided.')}</p>
        </div>

        <div>
          <div style="font-family:var(--font-mono); font-size:0.7rem; color:var(--text-dim); margin-bottom:0.5rem;">
            SHA-256: ${doc.fileChecksum ? doc.fileChecksum.substring(0, 16) + '...' : 'N/A'} • v${doc.currentVersion}
          </div>
          <div class="doc-footer">
            <span>By ${escapeHtml(doc.ownerUsername)} • ${sizeKb} KB • ${dateStr}</span>
            <div class="doc-actions">
              ${permission !== 'NONE' ? `
                <button class="btn btn-secondary btn-sm" title="Secure Watermarked Download" onclick="downloadDoc(${doc.id}, '${escapeHtml(doc.title)}')">
                  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"/></svg>
                </button>
              ` : `
                <button class="btn btn-secondary btn-sm" title="Request Access" onclick="openRequestModal(${doc.id}, '${escapeHtml(doc.title)}')">
                  Request Access
                </button>
              `}

              ${isOwner ? `
                <button class="btn btn-secondary btn-sm" title="Manage Permissions" onclick="openPermissionsModal(${doc.id}, '${escapeHtml(doc.title)}')">
                  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"/></svg>
                </button>
              ` : ''}

              ${canEdit ? `
                <button class="btn btn-secondary btn-sm" title="Version History" onclick="openVersionsModal(${doc.id}, '${escapeHtml(doc.title)}')">
                  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
                </button>
              ` : ''}

              ${isOwner ? `
                <button class="btn btn-danger btn-sm" title="Delete Document" onclick="deleteDoc(${doc.id})">
                  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/></svg>
                </button>
              ` : ''}
            </div>
          </div>
        </div>
      </div>
    `;
  }).join('');
}

// Download Document with Watermarking
async function downloadDoc(id, title) {
  try {
    showToast(`Downloading & dynamically watermarking '${title}'...`, 'info');
    const response = await fetch(`/api/documents/${id}/download?watermark=true`, {
      headers: { 'Authorization': `Bearer ${state.token}` }
    });

    if (response.status === 403) {
      showToast('Security Alert: Access Forbidden by backend IDOR policy.', 'error');
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
    showToast('Failed to download document.', 'error');
  }
}

// Delete Document
async function deleteDoc(id) {
  if (!confirm('Are you sure you want to delete this confidential document? This action will be recorded in the audit trail.')) return;
  try {
    await apiRequest(`/api/documents/${id}`, { method: 'DELETE' });
    showToast('Document deleted and audit event chained.', 'success');
    loadVaultDocuments();
  } catch (e) {}
}

// Upload Modal & Handler
function openUploadModal() {
  openModal('modal-upload');
}

async function submitUpload(e) {
  e.preventDefault();
  const title = document.getElementById('upload-title').value;
  const category = document.getElementById('upload-category').value;
  const desc = document.getElementById('upload-desc').value;
  const fileInput = document.getElementById('upload-file');

  if (!fileInput.files || fileInput.files.length === 0) {
    showToast('Please select a file.', 'error');
    return;
  }

  const formData = new FormData();
  formData.append('title', title);
  formData.append('category', category);
  formData.append('description', desc);
  formData.append('file', fileInput.files[0]);

  try {
    const res = await apiRequest('/api/documents/upload', {
      method: 'POST',
      body: formData,
      isFormData: true
    });
    if (res) {
      showToast(`Document '${title}' uploaded successfully!`, 'success');
      closeModal('modal-upload');
      document.getElementById('upload-form').reset();
      loadVaultDocuments();
    }
  } catch (err) {}
}

// Permissions Modal & Handler
async function openPermissionsModal(docId, docTitle) {
  state.selectedDocId = docId;
  document.getElementById('perm-doc-title').innerText = docTitle;
  openModal('modal-permissions');
  loadDocPermissions(docId);
  loadUsersForGrant();
}

async function loadUsersForGrant() {
  try {
    const select = document.getElementById('grant-username-select');
    select.innerHTML = `
      <option value="viewer">viewer (Bob Smith)</option>
      <option value="owner">owner (Alice Jenkins)</option>
      <option value="admin">admin (Administrator)</option>
    `;
  } catch (e) {}
}

async function loadDocPermissions(docId) {
  const tbody = document.getElementById('permissions-tbody');
  tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--text-muted);">Loading permissions...</td></tr>';
  try {
    const list = await apiRequest(`/api/permissions/documents/${docId}`);
    if (!list || list.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--text-muted);">No individual permissions granted yet.</td></tr>';
      return;
    }
    tbody.innerHTML = list.map(p => `
      <tr>
        <td><strong>${escapeHtml(p.username)}</strong><br><span style="font-size:0.75rem; color:var(--text-dim);">${escapeHtml(p.userFullName || '')}</span></td>
        <td>Viewer</td>
        <td><span class="badge ${p.permissionType === 'EDIT' ? 'badge-warning' : 'badge-info'}">${p.permissionType}</span></td>
        <td>${p.expiresAt ? new Date(p.expiresAt).toLocaleString() : 'Permanent (No Expiry)'}</td>
        <td>
          <span class="status-pill ${p.active ? 'verified' : 'tampered'}" style="padding:0.15rem 0.5rem; font-size:0.7rem;">
            ${p.active ? 'ACTIVE' : 'REVOKED'}
          </span>
        </td>
        <td>
          ${p.active ? `
            <button class="btn btn-danger btn-sm" onclick="revokePermission(${p.documentId}, ${p.id})">
              Revoke Immediately
            </button>
          ` : '<span style="color:var(--text-dim); font-size:0.75rem;">Access Terminated</span>'}
        </td>
      </tr>
    `).join('');
  } catch (e) {
    tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--danger);">Failed to load permissions.</td></tr>';
  }
}

async function submitGrantPermission() {
  const username = document.getElementById('grant-username-select').value;
  const permissionType = document.getElementById('grant-type-select').value;
  const expiry = document.getElementById('grant-expiry-input').value;

  const payload = {
    username,
    permissionType,
    expiresAt: expiry ? new Date(expiry).toISOString() : null
  };

  try {
    await apiRequest(`/api/permissions/documents/${state.selectedDocId}/grant`, {
      method: 'POST',
      body: JSON.stringify(payload)
    });
    showToast(`Permission granted to ${username}. Logged in audit trail.`, 'success');
    loadDocPermissions(state.selectedDocId);
    loadVaultDocuments();
  } catch (e) {}
}

async function revokePermission(docId, permissionId) {
  if (!confirm('Revoking access immediately blocks this user from viewing or downloading this document. Proceed?')) return;
  try {
    await apiRequest(`/api/permissions/documents/${docId}/revoke/${permissionId}`, {
      method: 'DELETE'
    });
    showToast('Permission REVOKED immediately. Zero-grace period enforced.', 'success');
    loadDocPermissions(docId);
    loadVaultDocuments();
  } catch (e) {}
}

// Request Access Modal & Handler
function openRequestModal(docId, docTitle) {
  document.getElementById('req-doc-id').value = docId;
  document.getElementById('req-doc-title').innerText = docTitle;
  openModal('modal-request-access');
}

async function submitRequestAccess(e) {
  e.preventDefault();
  const documentId = document.getElementById('req-doc-id').value;
  const requestedPermission = document.getElementById('req-perm-type').value;
  const reason = document.getElementById('req-reason').value;

  try {
    await apiRequest('/api/requests', {
      method: 'POST',
      body: JSON.stringify({ documentId, requestedPermission, reason })
    });
    showToast('Access request dispatched to document owner.', 'success');
    closeModal('modal-request-access');
    document.getElementById('request-access-form').reset();
  } catch (e) {}
}

// Access Requests Tab
async function loadAccessRequests() {
  const pendingTbody = document.getElementById('pending-requests-tbody');
  const myTbody = document.getElementById('my-requests-tbody');

  pendingTbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--text-muted);">Loading requests...</td></tr>';
  myTbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--text-muted);">Loading requests...</td></tr>';

  try {
    const [pending, myRequests] = await Promise.all([
      apiRequest('/api/requests/pending'),
      apiRequest('/api/requests/my')
    ]);

    if (!pending || pending.length === 0) {
      pendingTbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--text-muted);">No pending access requests for your documents.</td></tr>';
    } else {
      pendingTbody.innerHTML = pending.map(r => `
        <tr>
          <td><strong>${escapeHtml(r.documentTitle)}</strong></td>
          <td>${escapeHtml(r.requesterUsername)} (${escapeHtml(r.requesterEmail || '')})</td>
          <td><span class="badge ${r.requestedPermission === 'EDIT' ? 'badge-warning' : 'badge-info'}">${r.requestedPermission}</span></td>
          <td>${escapeHtml(r.reason || 'None')}</td>
          <td>${new Date(r.createdAt).toLocaleString()}</td>
          <td>
            <div style="display:flex; gap:0.4rem;">
              <button class="btn btn-primary btn-sm" onclick="reviewRequest(${r.id}, true)">Approve</button>
              <button class="btn btn-danger btn-sm" onclick="reviewRequest(${r.id}, false)">Reject</button>
            </div>
          </td>
        </tr>
      `).join('');
    }

    if (!myRequests || myRequests.length === 0) {
      myTbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--text-muted);">You have not submitted any access requests.</td></tr>';
    } else {
      myTbody.innerHTML = myRequests.map(r => `
        <tr>
          <td><strong>${escapeHtml(r.documentTitle)}</strong></td>
          <td><span class="badge ${r.requestedPermission === 'EDIT' ? 'badge-warning' : 'badge-info'}">${r.requestedPermission}</span></td>
          <td>${escapeHtml(r.reason || '')}</td>
          <td>
            <span class="badge ${r.status === 'APPROVED' ? 'badge-success' : r.status === 'REJECTED' ? 'badge-danger' : 'badge-warning'}">
              ${r.status}
            </span>
          </td>
          <td>${r.reviewedByUsername ? escapeHtml(r.reviewedByUsername) : 'Pending Review'}</td>
          <td>${new Date(r.createdAt).toLocaleDateString()}</td>
        </tr>
      `).join('');
    }
  } catch (e) {}
}

async function reviewRequest(id, approve) {
  try {
    await apiRequest(`/api/requests/${id}/review?approve=${approve}`, { method: 'POST' });
    showToast(`Request ${approve ? 'APPROVED' : 'REJECTED'}. Audit log generated.`, 'success');
    loadAccessRequests();
  } catch (e) {}
}

// Version History Modal & Handler
async function openVersionsModal(docId, docTitle) {
  document.getElementById('version-doc-id').value = docId;
  document.getElementById('version-doc-title').innerText = docTitle;
  openModal('modal-versions');
  loadVersions(docId);
}

async function loadVersions(docId) {
  const tbody = document.getElementById('versions-tbody');
  tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--text-muted);">Loading version history...</td></tr>';
  try {
    const versions = await apiRequest(`/api/documents/${docId}/versions`);
    if (!versions || versions.length === 0) {
      tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--text-muted);">No historical versions recorded.</td></tr>';
      return;
    }
    tbody.innerHTML = versions.map(v => `
      <tr>
        <td><span class="badge badge-info">v${v.versionNumber}</span></td>
        <td>${escapeHtml(v.uploadedBy?.username || 'Owner')}</td>
        <td>${escapeHtml(v.originalFilename)}</td>
        <td class="mono-hash">${v.fileChecksum ? v.fileChecksum.substring(0, 16) + '...' : ''}</td>
        <td>${escapeHtml(v.changeSummary || 'No summary')}</td>
        <td>${new Date(v.createdAt).toLocaleString()}</td>
      </tr>
    `).join('');
  } catch (e) {
    tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--danger);">Failed to load versions.</td></tr>';
  }
}

async function submitNewVersion(e) {
  e.preventDefault();
  const docId = document.getElementById('version-doc-id').value;
  const fileInput = document.getElementById('version-file-input');
  const summary = document.getElementById('version-summary-input').value;

  if (!fileInput.files || fileInput.files.length === 0) {
    showToast('Please select a replacement file.', 'error');
    return;
  }

  const formData = new FormData();
  formData.append('file', fileInput.files[0]);
  formData.append('changeSummary', summary);

  try {
    await apiRequest(`/api/documents/${docId}/versions`, {
      method: 'POST',
      body: formData,
      isFormData: true
    });
    showToast('New version uploaded and verified!', 'success');
    document.getElementById('new-version-form').reset();
    loadVersions(docId);
    loadVaultDocuments();
  } catch (e) {}
}

// Audit Trail Ledger
async function loadAuditLogs() {
  const tbody = document.getElementById('audit-logs-tbody');
  tbody.innerHTML = '<tr><td colspan="8" style="text-align:center; color:var(--text-muted);">Loading cryptographically linked ledger...</td></tr>';
  try {
    const logs = await apiRequest('/api/admin/audit-logs');
    if (!logs || logs.length === 0) {
      tbody.innerHTML = '<tr><td colspan="8" style="text-align:center; color:var(--text-muted);">Ledger is empty.</td></tr>';
      return;
    }

    tbody.innerHTML = logs.map(l => {
      const prev = l.previousHash.substring(0, 10) + '...';
      const curr = l.currentHash.substring(0, 10) + '...';
      const actionBadge = l.action.includes('FAILED') || l.action.includes('UNAUTHORIZED') ? 'badge-danger' :
                          l.action.includes('GRANTED') || l.action.includes('SUCCESS') ? 'badge-success' : 'badge-info';

      return `
        <tr>
          <td><span class="mono-hash">#${l.id}</span></td>
          <td>${new Date(l.timestamp).toLocaleString()}</td>
          <td><span class="badge ${actionBadge}">${l.action}</span></td>
          <td><strong>${escapeHtml(l.username)}</strong> (${escapeHtml(l.userRole || '')})</td>
          <td>${escapeHtml(l.resourceType)}: ${escapeHtml(l.resourceId || '-')}</td>
          <td class="mono-hash">${escapeHtml(l.ipAddress || '')}</td>
          <td>
            <span class="badge ${l.success ? 'badge-success' : 'badge-danger'}">
              ${l.success ? 'SUCCESS' : 'DENIED'}
            </span>
          </td>
          <td class="mono-hash">${prev} &rarr; ${curr}</td>
        </tr>
      `;
    }).join('');
  } catch (e) {
    tbody.innerHTML = '<tr><td colspan="8" style="text-align:center; color:var(--danger);">Audit trail restricted to Administrators.</td></tr>';
  }
}

// Security Operations Center (SOC) & Tamper Proofing
async function loadSocData() {
  try {
    const [stats, tamperResult] = await Promise.all([
      apiRequest('/api/admin/stats'),
      apiRequest('/api/tamper/verify')
    ]);

    if (stats) {
      document.getElementById('stat-docs').innerText = stats.totalDocuments;
      document.getElementById('stat-audit-count').innerText = stats.totalAuditLogs;
      document.getElementById('stat-unauthorized').innerText = stats.unauthorizedAccessAttempts24h + stats.failedLogins24h;
      document.getElementById('stat-perms').innerText = stats.activePermissions;
    }

    updateTamperVisual(tamperResult);
  } catch (e) {}
}

async function runTamperVerification() {
  try {
    showToast('Calculating and verifying cryptographic hash chain...', 'info');
    const result = await apiRequest('/api/tamper/verify');
    updateTamperVisual(result);
    if (result.valid) {
      showToast('Integrity Confirmed! 100% of audit blocks verified.', 'success');
    } else {
      showToast(`TAMPER DETECTED at Block #${result.brokenRecordId}!`, 'error');
    }
  } catch (e) {
    showToast('Tamper verification failed to execute.', 'error');
  }
}

function updateTamperVisual(result) {
  const badge = document.getElementById('verification-badge');
  const box = document.getElementById('verification-detail-box');
  const pill = document.getElementById('tamper-status-pill');
  const pillText = document.getElementById('tamper-status-text');

  if (!result) return;

  if (result.valid) {
    badge.className = 'status-pill verified';
    badge.innerHTML = '<span>CHAIN VALID (100%)</span>';
    box.style.color = '#a7f3d0';
    box.innerText = `[SECURE]: ${result.details} (${result.totalRecordsChecked} blocks verified)`;

    pill.className = 'status-pill verified';
    pillText.innerText = 'LEDGER: 100% VERIFIED';
  } else {
    badge.className = 'status-pill tampered';
    badge.innerHTML = '<span>TAMPER DETECTED</span>';
    box.style.color = '#fca5a5';
    box.innerText = `[CRITICAL ALERT]: ${result.details} (Compromised Block: #${result.brokenRecordId})`;

    pill.className = 'status-pill tampered';
    pillText.innerText = `SECURITY ALERT: TAMPER ON #${result.brokenRecordId}`;
  }

  // Render Visual Blocks
  const visual = document.getElementById('hash-chain-visual');
  const count = Math.min(result.totalRecordsChecked, 6);
  let html = `<div class="block-chip valid">Genesis Block #0<br><span style="color:var(--text-dim); font-size:0.65rem;">00000000...</span></div>`;

  for (let i = 1; i <= count; i++) {
    const isTampered = !result.valid && result.brokenRecordId == i;
    html += `
      <div class="chain-arrow">&rarr;</div>
      <div class="block-chip ${isTampered ? 'tampered' : 'valid'}">
        Block #${i}<br>
        <span style="font-size:0.65rem; color:${isTampered ? '#fca5a5' : 'var(--text-dim)'};">
          ${isTampered ? 'BROKEN HASH!' : 'SHA-256 Valid'}
        </span>
      </div>
    `;
  }
  if (result.totalRecordsChecked > 6) {
    html += `<div class="chain-arrow">&rarr;</div><div class="block-chip valid">+${result.totalRecordsChecked - 6} more blocks</div>`;
  }
  visual.innerHTML = html;
}

// Modal Helpers
function openModal(id) {
  document.getElementById(id).classList.add('open');
}

function closeModal(id) {
  document.getElementById(id).classList.remove('open');
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

// Initialize on load
document.addEventListener('DOMContentLoaded', () => {
  if (state.user) {
    renderUserNav();
    updateDemoChips(state.user.username);
    loadVaultDocuments();
  } else {
    // Auto-login as Alice (Owner) for immediate demo experience
    quickLogin('owner', 'Owner@123');
  }
});
