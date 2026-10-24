// The product's data, one module, asserted: every frame of the film reads it, so a paused frame
// always makes sense (references/product-ui.md). Strings here are product UI, not film copy.
import { copy } from './timeline';

export const PROJECT = 'Mira portfolio';
export const PROMPT = copy('prompt').text;

/** The problem pile: what a site needs the old way. Each a small window of the toolchain. */
export const PILE: { title: string; body: string; tone: 'bad' | 'mute' | 'plain'; w: number }[] = [
  { title: 'Terminal', body: 'npm ERR! code ERESOLVE', tone: 'bad', w: 520 },
  { title: 'Install Node.js', body: 'Download 22.11 LTS · 96 MB', tone: 'plain', w: 460 },
  { title: 'Android Studio', body: 'Downloading · 6.2 GB', tone: 'mute', w: 440 },
  { title: 'API key required', body: 'Paste your secret key to continue', tone: 'plain', w: 500 },
  { title: 'Sign in', body: 'Create an account to deploy', tone: 'plain', w: 420 },
  { title: 'Build failed', body: 'Module not found: ./Gallery', tone: 'bad', w: 480 },
  { title: 'Choose a plan', body: 'Pro · $20 / month', tone: 'plain', w: 400 },
  { title: 'Terminal', body: 'Error: port 3000 in use', tone: 'bad', w: 460 },
  { title: 'Git', body: 'Permission denied (publickey)', tone: 'bad', w: 480 },
  { title: 'VS Code', body: 'Install 14 recommended extensions?', tone: 'mute', w: 520 },
  { title: 'Gradle', body: 'Sync failed · retry', tone: 'bad', w: 400 },
  { title: 'DNS', body: 'Record not found for your domain', tone: 'mute', w: 480 },
  { title: 'Docker', body: 'Daemon is not running', tone: 'bad', w: 420 },
  { title: 'Hosting', body: 'Add a payment method', tone: 'plain', w: 440 },
  { title: 'Terminal', body: 'zsh: command not found: npx', tone: 'bad', w: 500 },
  { title: 'Keychain', body: 'Enter your password', tone: 'mute', w: 400 },
];

/** Orobi's tool rows for the build, in order (as the app draws them: verb, file, +lines). */
export const ROWS: { verb: string; file: string; add?: number; icon: 'read' | 'write' | 'eye' }[] = [
  { verb: 'Read', file: 'project', icon: 'read' },
  { verb: 'Wrote', file: 'index.html', add: 142, icon: 'write' },
  { verb: 'Wrote', file: 'style.css', add: 96, icon: 'write' },
  { verb: 'Wrote', file: 'gallery.js', add: 38, icon: 'write' },
  { verb: 'Checked', file: 'the preview', icon: 'eye' },
];
export const FILES_WRITTEN = ROWS.filter((r) => r.verb === 'Wrote').length;
export const LINES_ADDED = ROWS.reduce((n, r) => n + (r.add ?? 0), 0);

/** Mira's site. */
export const SITE = {
  name: 'Mira Okafor',
  nav: ['Work', 'About', 'Contact'],
  title: ['Streets', 'after dark.'],
  sub: 'Street photography from Lagos and Lisbon.',
  photos: 8, // tiles in the gallery
};

/** The follow-up the user sends before Orobi answers with the UI block. */
export const ASK = 'Bigger photos, lighter look';

/** The UI block Orobi answers with: one slider drives the gallery. */
export const GALLERY = {
  title: 'Gallery',
  colsFrom: 4,
  colsTo: 2,
  /** photo width in px at a 1440 px page with 24 px gaps: derived from the columns, never typed */
  widthFor: (cols: number) => Math.round((1440 - 2 * 96 - 24 * (cols - 1)) / cols),
};

/** Free models, no key: real keyless models in the app's picker (AiModels.kt seeds). */
export const MODELS = ['Qwen3.8 Max', 'DeepSeek V4 Flash', 'GLM 5.3 Flash', 'Kilo Auto'];

export const PUBLISH = {
  target: 'Netlify',
  url: 'mira-okafor.netlify.app',
  steps: ['Bundling 3 files', 'Uploading', 'Live'],
};

// Asserts: the story's numbers agree with each other on every frame.
const assert = (ok: boolean, msg: string) => {
  if (!ok) throw new Error(`data.ts: ${msg}`);
};
assert(FILES_WRITTEN === 3, 'three files are written');
assert(LINES_ADDED === 276, 'the rows add 276 lines');
assert(GALLERY.widthFor(4) === 294 && GALLERY.widthFor(2) === 612, 'gallery widths follow the columns');
assert(PUBLISH.steps[0].includes(String(FILES_WRITTEN)), 'the bundle holds the files Orobi wrote');
assert(new Set(MODELS).size === MODELS.length, 'model names are distinct');
