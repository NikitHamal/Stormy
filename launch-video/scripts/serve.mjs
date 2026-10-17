// npm run preview — serves the composition with a scrubber (Space = play/pause, arrows = frame step).
import { serve, arg } from './lib.mjs';
import { exec } from 'node:child_process';
const port = Number(arg('port', 5173));
const { url } = await serve(port);
const link = `${url}/src/index.html?preview=1&format=${arg('format', 'landscape')}`;
console.log(`Oro launch preview: ${link}\n(Ctrl+C to stop)`);
if (!process.env.CI && !arg('no-open')) {
  const cmd = process.platform === 'darwin' ? 'open' : process.platform === 'win32' ? 'start ""' : 'xdg-open';
  exec(`${cmd} "${link}"`, () => {});
}
