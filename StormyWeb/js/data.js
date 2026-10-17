// Mock state, shaped like the app's domain models (Project, ChatSession, ChatMessage, AiModel...).
export const state = {
  theme: 'dark',
  projects: [
    { id: 'p1', name: 'Bean There', when: 'Today', chats: 3, pinned: true, shape: 1 },
    { id: 'p2', name: 'Mara Ilves portfolio', when: 'Yesterday', chats: 5, pinned: false, shape: 3 },
    { id: 'p3', name: 'Pond', when: 'Mon', chats: 1, pinned: false, shape: 5 },
  ],
  sessions: [
    { id: 's1', title: 'Build me my portfolio site', when: '5m', count: 14, preview: 'Add a gallery with captions', group: 'Today', pinned: false },
    { id: 's2', title: 'Dark mode toggle', when: 'Yesterday', count: 6, preview: 'Remember the choice', group: 'Yesterday', pinned: true },
    { id: 's3', title: 'Fix the mobile menu', when: 'Mon', count: 9, preview: 'It overlaps the logo', group: 'Previous 7 days', pinned: false },
  ],
  currentSession: 's1',
  model: { name: 'K2 Horizon 375B', provider: 'K2 Horizon' },
  agentMode: true,
  autonomy: 'Unleashed',
  dictation: 'System',
  messages: [],
  plan: { title: 'Plan', items: [['Read index.html and style.css', true], ['Add the gallery grid', true], ['Write captions', false], ['Check on phone and desktop', false]] },
  providers: {
    yours: [{ name: 'My vLLM box', url: 'http://10.0.0.4:8000/v1', status: '12 models · 1 key' }],
    builtIn: [
      ['K2 Horizon (community)', 'Ready · no key needed', true], ['Kilo Code', 'Ready · key optional', true], ['Pollinations', 'Ready · no key needed', true],
      ['OVHcloud AI Endpoints', 'Ready · key optional', true], ['DeepSeek', 'Needs a key', false], ['Moonshot (Kimi)', 'Needs a key', false],
      ['Anthropic', 'Needs a key', false], ['OpenAI', 'Needs a key', false], ['xAI', 'Needs a key', false], ['Z.ai (GLM)', 'Needs a key', false],
      ['Groq', '2 keys', true], ['Google Gemini', 'Needs a key', false], ['OpenRouter', 'Needs a key', false], ['Ollama (local)', 'Ready · fetch models', true],
    ],
  },
  models: [
    ['Recommended', [['K2 Horizon 375B', 'K2 Horizon · 128K ctx · text tool calls'], ['Kilo Auto', 'Kilo Code · 200K ctx']]],
    ['On this phone', [['Qwen 2.5 1.5B (on device)', 'On this phone · 4K ctx · text tool calls']]],
    ['Free · no key', [['Qwen 3.5 122B', 'OVHcloud · 128K ctx'], ['openai-fast', 'Pollinations · 128K ctx']]],
    ['Your providers', [['llama-3.3-70b', 'Groq · 128K ctx'], ['deepseek-chat', 'DeepSeek · 64K ctx']]],
  ],
  files: { 'index.html': [
    '<!DOCTYPE html>', '<html lang="en">', '<head>', '  <meta charset="UTF-8">', '  <meta name="viewport" content="width=device-width, initial-scale=1">',
    '  <title>Mara Ilves — Photographer</title>', '  <link rel="stylesheet" href="style.css">', '</head>', '<body>', '  <header class="nav">',
    '    <a class="logo" href="/">MARA ILVES</a>', '    <button class="book">Book</button>', '  </header>', '  <main id="main">', '    <section class="hero">',
    '      <h1>Light, kept.</h1>', '    </section>', '  </main>', '</body>', '</html>'], 'style.css': [':root {', '  --ink: #0E1117;', '  --paper: #F7F8FA;', '}', 'body { margin: 0; font-family: Poppins, sans-serif; }'] },
};
