// Where the app screen sits in each act, as camera shots over app coordinates (ui/kit APP).
// Shared so every seam between acts matches exactly.
import type { Shot } from '../lib/camera';
import { H, W } from '../timeline';

/** A shot's scale, capped so the 720 px app never overflows a narrow frame by more than a little. */
export const fitK = (k: number) => Math.min(k, (W * 0.96) / 720);
import { COMPOSER } from '../ui/kit';

/** The app at rest on the stage: the composer at the lower middle of the frame. */
export const S_REST: Shot = { ax: 360, ay: 1342, sx: W / 2, sy: 760, k: fitK(1.45) };
/** Pushed in on the composer while the prompt types. */
export const S_TYPE: Shot = { ax: 360, ay: 1342, sx: W / 2, sy: H * 0.5, k: fitK(2.1) };
/** Framing Orobi's rows in the chat. */
export const S_ROWS: Shot = { ax: 360, ay: 560, sx: W / 2, sy: H * 0.31, k: fitK(1.5) };
/** The send button's centre in app coordinates. */
export const SEND = { x: COMPOSER.send.x, y: COMPOSER.send.y, d: COMPOSER.send.d };
/** The loop's width when it sits in a button or a row tile (px in app coordinates). */
export const LOOP_IN_BUTTON = 58;
