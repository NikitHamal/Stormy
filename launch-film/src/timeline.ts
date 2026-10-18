// The single source of truth for time. Picture (src/acts), sound (src/sync.ts -> out/cues.json)
// and QA (scripts/grid-check.ts) all import this file, so a change here moves everything together.
// Think in bars and beats, never in seconds. Keep this file free of React and CSS imports:
// Node scripts load it directly.
import { holdFor, PACE, readingWindows, revealFor, wordCount, type PaceName } from './lib/pace';

export { PACE } from './lib/pace'; // grid-check checks COPY against the film's own pace numbers

export const FPS = 60;
export const BPM = 120;
/**
 * Frame size. The master is 16:9; the social re-layouts (9:16, 1:1) are the same film laid out
 * again from the same timeline (never a crop of the master): bundle with REMOTION_FORMAT=9x16 or
 * 1x1 and every act lays out from these numbers (see FORMAT / PORTRAIT below).
 */
const ENV_FORMAT: string = (globalThis as { process?: { env?: Record<string, string | undefined> } }).process?.env?.REMOTION_FORMAT ?? '16x9';
export const FORMAT: '16x9' | '9x16' | '1x1' = ENV_FORMAT === '9x16' || ENV_FORMAT === '1x1' ? ENV_FORMAT : '16x9';
export const W = FORMAT === '16x9' ? 1920 : 1080;
export const H = FORMAT === '9x16' ? 1920 : 1080;
/** True for frames taller than wide or square: acts stack instead of splitting side by side. */
export const NARROW = W <= H;
/**
 * The film's pace (src/lib/pace.ts). calm: text arrives as short fragments that take turns with
 * the picture, held about 1-1.5 s (the default for text-led films, at 90-100 BPM). crisp: the
 * denser launch-film tempo, for briefs that ask for energy (at 120 BPM).
 */
export const PACE_NAME: PaceName = 'crisp'; // the brief asks for energy (BRIEF.md)

/** Frames per beat. Must be a whole number (grid-check fails otherwise): 60 fps -> 90, 100, 120, 144, 150 BPM. */
export const BEAT = (60 / BPM) * FPS;
export const BAR = BEAT * 4;
/**
 * Absolute frame of a grid position: bar is 1-based, beat 0-3, sub = 16ths within the beat.
 * b(3) is the downbeat of bar 3; b(2, 1, 2) is the "and" of beat 2 in bar 2.
 */
export const b = (bar: number, beat = 0, sub = 0) => Math.round(((bar - 1) * 4 + beat) * BEAT + sub * (BEAT / 4));

/**
 * A choreography length written in 60 fps frames (the unit every number in the cinetic
 * references uses), converted to this film's fps. Cues come from b(); durations inside a move
 * use f60(), so the film keeps its timing if --fps changes.
 */
export const f60 = (frames: number) => Math.round((frames * FPS) / 60);

/** After the last bar the picture holds while the audio decays to digital silence. */
export const TAIL = FPS;

// Acts: contiguous, bar-aligned, [from, from + dur). Each act is its own composition in Root.tsx.
// One beat = 30 f, one bar = 120 f (2 s). The film: TREATMENT.md section 5.
export const ACT = {
  pile: { from: b(1), dur: b(3) - b(1) }, // the toolchain piles up; freeze; the turn on bar 3
  turn: { from: b(3), dur: b(6) - b(3) }, // implosion -> the loop is born -> composer -> the prompt
  build: { from: b(6), dur: b(8) - b(6) }, // send; Orobi's tool rows cascade, spinner -> check
  live: { from: b(8), dur: b(9) - b(8) }, // iris from the loop into the live site building
  change: { from: b(9), dur: b(11) - b(9) }, // Orobi's UI block: one slider drives the gallery
  fly: { from: b(11), dur: b(12) - b(11) }, // the site flown like a runway; dark -> light mid-flight
  free: { from: b(12), dur: b(14) - b(12) }, // the model chip swaps free models, no key
  ship: { from: b(14), dur: b(16) - b(14) }, // publish: spinner -> check -> URL; crash through
  end: { from: b(16), dur: b(18) + TAIL - b(16) }, // the site clears; the loop becomes Orobi; lockup
};

export const TOTAL = b(18) + TAIL;

// Named sync points (absolute frames), all on the 16th grid unless marked offgrid.
export const CUE = {
  // pile
  hookA: b(1), // "A website" rises from frame 0 over cards that are already moving
  surge: b(1, 2, 3), // the pile surges: the key prompt card lands
  hookB: b(1, 3, 1), // "takes all this." once the first fragment has been read
  hookOut: b(2, 3), // the line leaves before the freeze
  freeze: b(2, 3, 2), // the pile stops dead: 15 f of true silence
  // turn
  implode: b(3), // the pile implodes into a point on the downbeat
  born: b(3, 0, 2), // the point draws itself into the loop
  orIn: b(3, 2), // "Or"
  spin: b(4, 0, 2), // the loop turns once: Orobi is listening
  sentence: b(4, 1), // "one sentence."
  rise: b(4, 3, 2), // the app rises under the loop
  dock: b(5, 0, 2), // the loop lands in the send button
  typeFrom: b(5, 1), // the prompt starts typing (product UI)
  typeHalf: b(5, 2), // "photo portfolio": half typed
  typeWords: b(5, 3), // "for Mira": the prompt keeps typing
  typeTo: b(5, 3, 3), // ...and is complete
  // build
  send: b(6), // the tap: the send loop starts to spin
  row1: b(6, 0, 2), // the first tool row arrives
  orobi: b(6, 1), // "Orobi"
  row3: b(6, 3, 2), // index.html's row checks
  writes: b(7), // "writes it," once the first rows have landed
  iris: b(7, 3, 1), // the seed on the last row's loop balloons; the disc fills on the bar-8 downbeat
  // live
  open: b(8), // the iris fills the frame on the downbeat: the live site
  liveIn: b(8, 1), // "live."
  // change
  block: b(9), // Orobi's UI block lands in the chat
  changeIn: b(9, 1), // "Change anything."
  drag: b(10), // the thumb is dragged: columns 4 -> 2
  dragEnd: b(10, 1, 2), // the slider locks on 2 columns
  tapLight: b(10, 3), // "Light" is tapped
  // fly
  flyUp: b(11, 0, 2), // the runway reaches cruise
  flip: b(11, 2), // the theme flips mid-flight
  flyOut: b(11, 3, 2), // the flight's fastest frames into the cut
  // free
  freeIn: b(12, 0, 2), // "Free models."
  swap1: b(12, 3, 2), // swaps sit between the reads, never inside one
  swap2: b(13),
  keyIn: b(13, 0, 2), // "No key."
  swap3: b(13, 3, 2),
  // ship
  sheet: b(14), // the publish sheet rises
  publish: b(14, 1), // tap Publish: the loop spins
  build2: b(14, 2), // the publish log's second step resolves
  done: b(14, 3), // check: published
  url: b(14, 3, 2), // the URL card lands
  shipIn: b(15), // "Ship it."
  crash: b(15, 3), // the URL card extrudes; the camera crashes through
  // end
  settle: b(16, 0, 2), // the crash's interior shot comes to rest
  clear: b(16, 1), // the site drops away and uncovers the loop
  uncover: b(16, 2), // the drop clears the loop: it turns once, alone on the stage
  grow: b(16, 3), // the loop grows into Orobi
  lockup: b(17), // the wordmark locks beside Orobi: the payoff
  lineIn: b(17, 2), // "Websites from your phone."
  end: b(18), // last bar line; TAIL follows
};

export type Copy = {
  id: string;
  text: string; // exactly what is on screen (\n separates lines)
  in: number; // first frame any of it is visible
  resolved: number; // first frame it is fully readable
  out: number; // first frame it starts to leave (TOTAL if it stays)
  kind?: 'copy' | 'ui'; // 'ui': a string the product shows (a typed command, code, a URL, an app label); not budgeted
  chain?: string; // the thought this fragment belongs to (fx/Chain renders a chain; grid-check checks its gaps)
  mode?: 'build' | 'replace'; // build: fragments append to one held line; replace: each takes the last one's place
};

const pace = PACE[PACE_NAME];

/**
 * One line of copy revealed word by word with this film's pace (the numbers <Words> uses), so
 * `resolved` is the frame its last word is fully in. `out` defaults to the pace's hold.
 */
export const line = (id: string, text: string, inF: number, out?: number): Copy => {
  const n = wordCount(text);
  const resolved = inF + f60(revealFor(n, pace));
  return { id, text, in: inF, resolved, out: out ?? resolved + f60(holdFor(n, pace)) };
};

/**
 * One thought as a chain of fragments, one COPY entry each (`<id>.1`, `<id>.2`, ...), rendered by
 * fx/Chain. A thought longer than 4 words arrives this way: 1-4 words per fragment, never a whole
 * sentence at once, with a picture beat between fragments (the product does what the fragment set
 * up). Give each fragment its first frame; `resolved` follows from the pace.
 *   build    fragments append to one held line, which re-centres; the line clears as a whole, at
 *            `out` (default: the last fragment's resolve plus the hold of the full line)
 *   replace  each fragment leaves (as a whole, over pace.exitDur: 24 f when calm) and has gone
 *            6 f before the next one rises in its place, so two fragments never share the spot;
 *            the last one leaves at `out` (default: its resolve plus its hold)
 * grid-check checks the result: every fragment is read before the next arrives, the picture beat
 * between them, the words on screen at once and the copy rate.
 */
export const chain = (id: string, mode: 'build' | 'replace', fragments: [text: string, inF: number][], o: { out?: number } = {}): Copy[] => {
  const base = fragments.map(([text, inF], i) => ({ id: `${id}.${i + 1}`, text, in: inF, resolved: inF + f60(revealFor(wordCount(text), pace)), chain: id, mode }));
  const last = base[base.length - 1];
  if (!last) throw new Error(`timeline: chain "${id}" has no fragments`);
  const words = (list: { text: string }[]) => list.reduce((n, c) => n + wordCount(c.text), 0);
  if (mode === 'build') {
    const out = o.out ?? last.resolved + f60(holdFor(words(base), pace));
    return base.map((c) => ({ ...c, out }));
  }
  // replace: the outgoing fragment's exit (exitDur) is over 6 f before the next one starts to rise
  const leave = f60(pace.exitDur) + f60(6);
  return base.map((c, i) => ({ ...c, out: base[i + 1] ? base[i + 1].in - leave : (o.out ?? c.resolved + f60(holdFor(wordCount(c.text), pace))) }));
};

// Every on-screen word: the word budget (kind: 'ui' entries are counted apart), the hold check,
// and the text-flow checks (fragments, words on screen at once, copy rate, text share) read this.
export const COPY: Copy[] = [
  ...chain('hook', 'build', [['A website', CUE.hookA], ['takes all this.', CUE.hookB]], { out: CUE.hookOut }),
  ...chain('turn', 'replace', [['Or', CUE.orIn], ['one sentence.', CUE.sentence]]),
  ...chain('build', 'build', [['Orobi', CUE.orobi], ['builds it,', CUE.writes]], { out: CUE.iris - f60(6) }),
  line('live', 'live.', CUE.liveIn),
  line('change', 'Change anything.', CUE.changeIn),
  ...chain('free', 'replace', [['Free models.', CUE.freeIn], ['No key.', CUE.keyIn]]),
  line('ship', 'Ship it.', CUE.shipIn, CUE.crash),
  { id: 'wordmark', text: 'Oro', in: CUE.lockup - f60(26), resolved: CUE.lockup, out: TOTAL },
  line('endline', 'Your website, from one sentence.', CUE.lineIn, TOTAL),
  // Product UI strings (kind 'ui': counted apart from the copy budget)
  { id: 'prompt', text: 'A street photo portfolio for Mira', in: CUE.typeFrom, resolved: CUE.typeTo, out: ACT.build.from + ACT.build.dur, kind: 'ui' },
];

/**
 * When each COPY entry is being read: [from, to) frame ranges by id (src/lib/pace.ts
 * readingWindows(): from `resolved` for at most readMax, an earlier fragment of a build chain only
 * for its own hold, then the whole line again). The one rule every check uses: export-cues.ts
 * writes it into out/cues.json, and grid-check, layout-audit, measure-speed and forensics read it.
 * Nothing new should start inside a window: the read happens in the quiet.
 */
export const READ = readingWindows(COPY, pace, FPS, TOTAL);

/** One COPY entry by id; acts read their text and timing from here so the budget stays true. */
export const copy = (id: string): Copy => {
  const c = COPY.find((x) => x.id === id);
  if (!c) throw new Error(`timeline: no COPY entry "${id}"`);
  return c;
};

/** The fragments of one chain, in order of arrival. */
export const chainOf = (id: string): Copy[] => {
  const list = COPY.filter((x) => x.chain === id).sort((p, q) => p.in - q.in);
  if (!list.length) throw new Error(`timeline: no COPY chain "${id}"`);
  return list;
};
