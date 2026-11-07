// Theme Switcher
// Toggles between light and dark theme and persists choice in localStorage.
// Expected HTML: <button id="themeToggle" class="btn btn-sm" title="Toggle theme">🌙</button>

(function() {
  console.log('Theme Switcher script loaded');
  const THEME_KEY = 'trustvault_theme';
  const btn = document.getElementById('themeToggle');
  if (!btn) return;

  // Apply saved theme on load
  const saved = localStorage.getItem(THEME_KEY) || 'light';
  setTheme(saved);

  // Click handler
  btn.addEventListener('click', () => {
    const newTheme = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    setTheme(newTheme);
  });

  function setTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem(THEME_KEY, theme);
    // Update button icon: 🌙 for dark mode (click will switch to dark), ☀️ for light mode
    btn.textContent = theme === 'dark' ? '☀️' : '🌙';
  }
})();
