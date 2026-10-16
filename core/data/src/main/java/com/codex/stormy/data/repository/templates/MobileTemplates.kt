package com.codex.stormy.data.repository.templates

/**
 * Progressive Web App (PWA) template files
 */
object PwaTemplates {
    val HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="theme-color" content="#4f46e5">
    <meta name="description" content="A Progressive Web App">
    <link rel="manifest" href="manifest.json">
    <link rel="apple-touch-icon" href="icons/icon-192.png">
    <title>My PWA</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <div class="app">
        <header class="app-header">
            <h1>My PWA</h1>
            <p class="connection-status" id="connectionStatus">Online</p>
        </header>

        <main class="app-main">
            <section class="hero">
                <div class="hero-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M12 2L2 7l10 5 10-5-10-5z"/>
                        <path d="M2 17l10 5 10-5"/>
                        <path d="M2 12l10 5 10-5"/>
                    </svg>
                </div>
                <h2>Welcome to Your PWA</h2>
                <p>This app works offline and can be installed on your device.</p>
            </section>

            <section class="features">
                <div class="feature-card">
                    <div class="feature-icon">📱</div>
                    <h3>Installable</h3>
                    <p>Add to home screen for a native app experience</p>
                </div>
                <div class="feature-card">
                    <div class="feature-icon">📴</div>
                    <h3>Offline Ready</h3>
                    <p>Works without internet connection</p>
                </div>
                <div class="feature-card">
                    <div class="feature-icon">⚡</div>
                    <h3>Fast</h3>
                    <p>Loads instantly with cached resources</p>
                </div>
            </section>

            <button id="installBtn" class="install-button" style="display: none;">
                Install App
            </button>
        </main>

        <nav class="bottom-nav">
            <a href="#" class="nav-item active">
                <span class="nav-icon">🏠</span>
                <span class="nav-label">Home</span>
            </a>
            <a href="#" class="nav-item">
                <span class="nav-icon">🔍</span>
                <span class="nav-label">Search</span>
            </a>
            <a href="#" class="nav-item">
                <span class="nav-icon">⚙️</span>
                <span class="nav-label">Settings</span>
            </a>
        </nav>
    </div>

    <script src="app.js"></script>
</body>
</html>
    """.trimIndent()

    val CSS = """
* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

:root {
    --primary: #4f46e5;
    --primary-light: #818cf8;
    --background: #f8fafc;
    --surface: #ffffff;
    --text: #1e293b;
    --text-secondary: #64748b;
    --success: #22c55e;
    --warning: #f59e0b;
}

body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    background: var(--background);
    color: var(--text);
    min-height: 100vh;
    -webkit-font-smoothing: antialiased;
}

.app {
    display: flex;
    flex-direction: column;
    min-height: 100vh;
}

.app-header {
    background: var(--primary);
    color: white;
    padding: 1rem 1.5rem;
    display: flex;
    justify-content: space-between;
    align-items: center;
    position: sticky;
    top: 0;
    z-index: 100;
}

.app-header h1 {
    font-size: 1.25rem;
    font-weight: 600;
}

.connection-status {
    font-size: 0.75rem;
    padding: 0.25rem 0.75rem;
    background: var(--success);
    border-radius: 999px;
}

.connection-status.offline {
    background: var(--warning);
}

.app-main {
    flex: 1;
    padding: 1.5rem;
    padding-bottom: 80px;
}

.hero {
    text-align: center;
    padding: 2rem 0;
}

.hero-icon {
    display: inline-flex;
    padding: 1.5rem;
    background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
    border-radius: 24px;
    color: white;
    margin-bottom: 1.5rem;
}

.hero h2 {
    font-size: 1.75rem;
    margin-bottom: 0.5rem;
}

.hero p {
    color: var(--text-secondary);
    max-width: 300px;
    margin: 0 auto;
}

.features {
    display: grid;
    gap: 1rem;
    margin-top: 2rem;
}

.feature-card {
    background: var(--surface);
    padding: 1.5rem;
    border-radius: 16px;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.feature-icon {
    font-size: 2rem;
    margin-bottom: 0.5rem;
}

.feature-card h3 {
    font-size: 1rem;
    margin-bottom: 0.25rem;
}

.feature-card p {
    font-size: 0.875rem;
    color: var(--text-secondary);
}

.install-button {
    width: 100%;
    padding: 1rem;
    margin-top: 2rem;
    font-size: 1rem;
    font-weight: 600;
    color: white;
    background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
    border: none;
    border-radius: 12px;
    cursor: pointer;
    transition: transform 0.2s, box-shadow 0.2s;
}

.install-button:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(79, 70, 229, 0.3);
}

.bottom-nav {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    background: var(--surface);
    display: flex;
    justify-content: space-around;
    padding: 0.75rem 0;
    box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.1);
    z-index: 100;
}

.nav-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    text-decoration: none;
    color: var(--text-secondary);
    padding: 0.5rem 1rem;
    transition: color 0.2s;
}

.nav-item.active {
    color: var(--primary);
}

.nav-icon {
    font-size: 1.25rem;
}

.nav-label {
    font-size: 0.75rem;
    margin-top: 0.25rem;
}

@media (min-width: 640px) {
    .features {
        grid-template-columns: repeat(3, 1fr);
    }

    .app-main {
        max-width: 800px;
        margin: 0 auto;
    }
}
    """.trimIndent()

    val APP_JS = """
// Register service worker
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('/sw.js')
            .then(registration => {
                console.log('SW registered:', registration);
            })
            .catch(error => {
                console.log('SW registration failed:', error);
            });
    });
}

// Handle online/offline status
const connectionStatus = document.getElementById('connectionStatus');

function updateConnectionStatus() {
    if (navigator.onLine) {
        connectionStatus.textContent = 'Online';
        connectionStatus.classList.remove('offline');
    } else {
        connectionStatus.textContent = 'Offline';
        connectionStatus.classList.add('offline');
    }
}

window.addEventListener('online', updateConnectionStatus);
window.addEventListener('offline', updateConnectionStatus);
updateConnectionStatus();

// Handle PWA install prompt
let deferredPrompt;
const installBtn = document.getElementById('installBtn');

window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredPrompt = e;
    installBtn.style.display = 'block';
});

installBtn.addEventListener('click', async () => {
    if (!deferredPrompt) return;

    deferredPrompt.prompt();
    const { outcome } = await deferredPrompt.userChoice;

    if (outcome === 'accepted') {
        console.log('User accepted the install prompt');
    }
    deferredPrompt = null;
    installBtn.style.display = 'none';
});

window.addEventListener('appinstalled', () => {
    console.log('PWA was installed');
    deferredPrompt = null;
});
    """.trimIndent()

    val SERVICE_WORKER = """
const CACHE_NAME = 'pwa-cache-v1';
const urlsToCache = [
    '/',
    '/index.html',
    '/style.css',
    '/app.js',
    '/manifest.json'
];

// Install event - cache resources
self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(cache => {
                console.log('Opened cache');
                return cache.addAll(urlsToCache);
            })
    );
});

// Fetch event - serve from cache, fallback to network
self.addEventListener('fetch', event => {
    event.respondWith(
        caches.match(event.request)
            .then(response => {
                if (response) {
                    return response;
                }

                return fetch(event.request).then(response => {
                    if (!response || response.status !== 200 || response.type !== 'basic') {
                        return response;
                    }

                    const responseToCache = response.clone();
                    caches.open(CACHE_NAME)
                        .then(cache => {
                            cache.put(event.request, responseToCache);
                        });

                    return response;
                });
            })
    );
});

// Activate event - clean up old caches
self.addEventListener('activate', event => {
    const cacheWhitelist = [CACHE_NAME];
    event.waitUntil(
        caches.keys().then(cacheNames => {
            return Promise.all(
                cacheNames.map(cacheName => {
                    if (cacheWhitelist.indexOf(cacheName) === -1) {
                        return caches.delete(cacheName);
                    }
                })
            );
        })
    );
});
    """.trimIndent()

    val MANIFEST = """
{
    "name": "My Progressive Web App",
    "short_name": "My PWA",
    "description": "A Progressive Web App template",
    "start_url": "/",
    "display": "standalone",
    "background_color": "#ffffff",
    "theme_color": "#4f46e5",
    "orientation": "portrait-primary",
    "icons": [
        {
            "src": "icons/icon-72.png",
            "sizes": "72x72",
            "type": "image/png"
        },
        {
            "src": "icons/icon-96.png",
            "sizes": "96x96",
            "type": "image/png"
        },
        {
            "src": "icons/icon-128.png",
            "sizes": "128x128",
            "type": "image/png"
        },
        {
            "src": "icons/icon-144.png",
            "sizes": "144x144",
            "type": "image/png"
        },
        {
            "src": "icons/icon-152.png",
            "sizes": "152x152",
            "type": "image/png"
        },
        {
            "src": "icons/icon-192.png",
            "sizes": "192x192",
            "type": "image/png",
            "purpose": "any maskable"
        },
        {
            "src": "icons/icon-384.png",
            "sizes": "384x384",
            "type": "image/png"
        },
        {
            "src": "icons/icon-512.png",
            "sizes": "512x512",
            "type": "image/png",
            "purpose": "any maskable"
        }
    ]
}
    """.trimIndent()
}
