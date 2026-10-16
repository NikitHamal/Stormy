package com.codex.stormy.data.repository.templates

/**
 * React template files (Vite-based)
 */
object ReactTemplates {
    val HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>React App</title>
</head>
<body>
    <div id="root"></div>
    <script type="module" src="/src/main.jsx"></script>
</body>
</html>
    """.trimIndent()

    val MAIN_JSX = """
import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import './App.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
    """.trimIndent()

    val APP_JSX = """
import { useState } from 'react'

function App() {
  const [count, setCount] = useState(0)

  return (
    <div className="app">
      <header className="app-header">
        <h1>Welcome to React</h1>
        <p>Edit <code>src/App.jsx</code> and save to reload.</p>

        <div className="card">
          <button onClick={() => setCount((count) => count + 1)}>
            Count is {count}
          </button>
          <p>Click the button to test state management</p>
        </div>

        <div className="links">
          <a
            href="https://react.dev"
            target="_blank"
            rel="noopener noreferrer"
          >
            Learn React
          </a>
          <a
            href="https://vitejs.dev"
            target="_blank"
            rel="noopener noreferrer"
          >
            Vite Docs
          </a>
        </div>
      </header>
    </div>
  )
}

export default App
    """.trimIndent()

    val APP_CSS = """
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

:root {
  --primary: #61dafb;
  --bg-dark: #282c34;
  --text-light: #ffffff;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  -webkit-font-smoothing: antialiased;
}

.app {
  text-align: center;
  min-height: 100vh;
}

.app-header {
  background-color: var(--bg-dark);
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-light);
  padding: 2rem;
}

h1 {
  font-size: 3rem;
  margin-bottom: 1rem;
  background: linear-gradient(135deg, #61dafb, #764abc);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

p {
  margin-bottom: 1.5rem;
  color: #abb2bf;
}

code {
  background: rgba(255, 255, 255, 0.1);
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  font-family: 'Fira Code', monospace;
}

.card {
  padding: 2rem;
  margin: 1rem 0;
}

button {
  font-size: 1rem;
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #61dafb, #764abc);
  color: white;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 20px rgba(97, 218, 251, 0.3);
}

.links {
  display: flex;
  gap: 2rem;
  margin-top: 2rem;
}

.links a {
  color: var(--primary);
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s;
}

.links a:hover {
  color: #764abc;
}
    """.trimIndent()

    val PACKAGE_JSON = """
{
  "name": "react-app",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^18.2.0",
    "react-dom": "^18.2.0"
  },
  "devDependencies": {
    "@vitejs/plugin-react": "^4.2.1",
    "vite": "^5.0.0"
  }
}
    """.trimIndent()

    val VITE_CONFIG = """
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    open: true
  }
})
    """.trimIndent()
}

/**
 * Vue.js 3 template files (Vite-based)
 */
object VueTemplates {
    val HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Vue App</title>
</head>
<body>
    <div id="app"></div>
    <script type="module" src="/src/main.js"></script>
</body>
</html>
    """.trimIndent()

    val MAIN_JS = """
import { createApp } from 'vue'
import App from './App.vue'
import './style.css'

createApp(App).mount('#app')
    """.trimIndent()

    val APP_VUE = """
<script setup>
import { ref } from 'vue'

const count = ref(0)
const increment = () => count.value++
</script>

<template>
  <div class="app">
    <header class="app-header">
      <h1>Welcome to Vue 3</h1>
      <p>Edit <code>src/App.vue</code> and save to reload.</p>

      <div class="card">
        <button @click="increment">
          Count is {{ count }}
        </button>
        <p>Click the button to test reactivity</p>
      </div>

      <div class="links">
        <a href="https://vuejs.org" target="_blank">Vue Docs</a>
        <a href="https://vitejs.dev" target="_blank">Vite Docs</a>
      </div>
    </header>
  </div>
</template>

<style scoped>
.app {
  text-align: center;
  min-height: 100vh;
}

.app-header {
  background: linear-gradient(135deg, #42b883 0%, #35495e 100%);
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: white;
  padding: 2rem;
}

h1 {
  font-size: 3rem;
  margin-bottom: 1rem;
}

p {
  margin-bottom: 1.5rem;
  opacity: 0.9;
}

code {
  background: rgba(255, 255, 255, 0.2);
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
}

.card {
  padding: 2rem;
}

button {
  font-size: 1rem;
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 8px;
  background: white;
  color: #42b883;
  cursor: pointer;
  font-weight: 600;
  transition: transform 0.2s, box-shadow 0.2s;
}

button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
}

.links {
  display: flex;
  gap: 2rem;
  margin-top: 2rem;
}

.links a {
  color: white;
  text-decoration: none;
  font-weight: 500;
  opacity: 0.9;
}

.links a:hover {
  opacity: 1;
}
</style>
    """.trimIndent()

    val STYLE_CSS = """
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  -webkit-font-smoothing: antialiased;
}
    """.trimIndent()

    val PACKAGE_JSON = """
{
  "name": "vue-app",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "vue": "^3.4.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.0.0",
    "vite": "^5.0.0"
  }
}
    """.trimIndent()

    val VITE_CONFIG = """
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 3000,
    open: true
  }
})
    """.trimIndent()
}

/**
 * Svelte template files (Vite-based)
 */
object SvelteTemplates {
    val HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Svelte App</title>
</head>
<body>
    <div id="app"></div>
    <script type="module" src="/src/main.js"></script>
</body>
</html>
    """.trimIndent()

    val MAIN_JS = """
import App from './App.svelte'
import './app.css'

const app = new App({
  target: document.getElementById('app'),
})

export default app
    """.trimIndent()

    val APP_SVELTE = """
<script>
  let count = 0

  function increment() {
    count += 1
  }
</script>

<main class="app">
  <header class="app-header">
    <h1>Welcome to Svelte</h1>
    <p>Edit <code>src/App.svelte</code> and save to reload.</p>

    <div class="card">
      <button on:click={increment}>
        Count is {count}
      </button>
      <p>Click the button to test reactivity</p>
    </div>

    <div class="links">
      <a href="https://svelte.dev" target="_blank">Svelte Docs</a>
      <a href="https://vitejs.dev" target="_blank">Vite Docs</a>
    </div>
  </header>
</main>

<style>
  .app {
    text-align: center;
    min-height: 100vh;
  }

  .app-header {
    background: linear-gradient(135deg, #ff3e00 0%, #ff6b35 100%);
    min-height: 100vh;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: white;
    padding: 2rem;
  }

  h1 {
    font-size: 3rem;
    margin-bottom: 1rem;
  }

  p {
    margin-bottom: 1.5rem;
    opacity: 0.9;
  }

  code {
    background: rgba(255, 255, 255, 0.2);
    padding: 0.2rem 0.5rem;
    border-radius: 4px;
  }

  .card {
    padding: 2rem;
  }

  button {
    font-size: 1rem;
    padding: 0.75rem 1.5rem;
    border: none;
    border-radius: 8px;
    background: white;
    color: #ff3e00;
    cursor: pointer;
    font-weight: 600;
    transition: transform 0.2s, box-shadow 0.2s;
  }

  button:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
  }

  .links {
    display: flex;
    gap: 2rem;
    margin-top: 2rem;
  }

  .links a {
    color: white;
    text-decoration: none;
    font-weight: 500;
    opacity: 0.9;
  }

  .links a:hover {
    opacity: 1;
  }
</style>
    """.trimIndent()

    val APP_CSS = """
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  -webkit-font-smoothing: antialiased;
}
    """.trimIndent()

    val PACKAGE_JSON = """
{
  "name": "svelte-app",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  },
  "devDependencies": {
    "@sveltejs/vite-plugin-svelte": "^3.0.0",
    "svelte": "^4.2.0",
    "vite": "^5.0.0"
  }
}
    """.trimIndent()

    val VITE_CONFIG = """
import { defineConfig } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'

export default defineConfig({
  plugins: [svelte()],
  server: {
    port: 3000,
    open: true
  }
})
    """.trimIndent()
}

/**
 * Next.js template files
 */
object NextJsTemplates {
    val INDEX_JS = """
import Head from 'next/head'
import styles from '../styles/Home.module.css'
import { useState } from 'react'

export default function Home() {
  const [count, setCount] = useState(0)

  return (
    <div className={styles.container}>
      <Head>
        <title>Next.js App</title>
        <meta name="description" content="Created with Next.js" />
        <link rel="icon" href="/favicon.ico" />
      </Head>

      <main className={styles.main}>
        <h1 className={styles.title}>
          Welcome to <span className={styles.highlight}>Next.js!</span>
        </h1>

        <p className={styles.description}>
          Edit <code className={styles.code}>pages/index.js</code> and save to reload.
        </p>

        <div className={styles.card}>
          <button onClick={() => setCount(c => c + 1)} className={styles.button}>
            Count is {count}
          </button>
        </div>

        <div className={styles.grid}>
          <a href="https://nextjs.org/docs" className={styles.cardLink}>
            <h2>Documentation &rarr;</h2>
            <p>Find in-depth information about Next.js features and API.</p>
          </a>

          <a href="https://nextjs.org/learn" className={styles.cardLink}>
            <h2>Learn &rarr;</h2>
            <p>Learn about Next.js in an interactive course with quizzes!</p>
          </a>
        </div>
      </main>

      <footer className={styles.footer}>
        <p>Built with Next.js</p>
      </footer>
    </div>
  )
}
    """.trimIndent()

    val APP_JS = """
import '../styles/globals.css'

function MyApp({ Component, pageProps }) {
  return <Component {...pageProps} />
}

export default MyApp
    """.trimIndent()

    val GLOBALS_CSS = """
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  -webkit-font-smoothing: antialiased;
}

a {
  color: inherit;
  text-decoration: none;
}
    """.trimIndent()

    val HOME_MODULE_CSS = """
.container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, #000000, #1a1a2e);
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: 4rem;
}

.title {
  margin: 0;
  line-height: 1.15;
  font-size: 4rem;
  text-align: center;
  color: white;
}

.highlight {
  background: linear-gradient(135deg, #0070f3, #00d4ff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.description {
  margin: 2rem 0;
  line-height: 1.5;
  font-size: 1.25rem;
  text-align: center;
  color: #888;
}

.code {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 5px;
  padding: 0.25rem 0.5rem;
  font-family: monospace;
}

.card {
  margin: 1rem;
  padding: 1.5rem;
}

.button {
  padding: 1rem 2rem;
  font-size: 1rem;
  background: linear-gradient(135deg, #0070f3, #00d4ff);
  color: white;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 20px rgba(0, 112, 243, 0.4);
}

.grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  max-width: 800px;
  margin-top: 2rem;
}

.cardLink {
  margin: 1rem;
  padding: 1.5rem;
  border: 1px solid #333;
  border-radius: 10px;
  transition: border-color 0.2s;
  max-width: 350px;
}

.cardLink:hover {
  border-color: #0070f3;
}

.cardLink h2 {
  margin: 0 0 1rem 0;
  font-size: 1.5rem;
  color: white;
}

.cardLink p {
  margin: 0;
  font-size: 1rem;
  color: #888;
}

.footer {
  padding: 2rem;
  text-align: center;
  border-top: 1px solid #333;
  color: #888;
}
    """.trimIndent()

    val PACKAGE_JSON = """
{
  "name": "nextjs-app",
  "version": "0.1.0",
  "private": true,
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "start": "next start",
    "export": "next build && next export"
  },
  "dependencies": {
    "next": "^14.0.0",
    "react": "^18.2.0",
    "react-dom": "^18.2.0"
  }
}
    """.trimIndent()

    val NEXT_CONFIG = """
/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // Enable static HTML export
  output: 'export',
}

module.exports = nextConfig
    """.trimIndent()
}
