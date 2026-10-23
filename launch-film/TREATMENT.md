# TREATMENT: Oro — "All this, or one sentence."

**Spec:** `1920x1080@60, 33s, 120BPM, pace: crisp, audio: earshot kit (crisp) + music` · **Format:** launch film · **Engine:** Remotion
**Product:** describe a website on your phone; Orobi builds it live, on free models, no key · **Viewer:** feed and README, muted first
**Assumptions:** see BRIEF.md.

## 1. Inventory
| List | Entries |
|---|---|
| Name | Oro: gold; and the app's mascot Orobi is an infinity loop with a head: the loader that never stops. The loop suggests the device. |
| Non-obvious behaviour | Unlike the obvious version (a chatbot that prints code you must paste somewhere), it writes the project files itself and shows the running site while it writes, on the phone, with free models and no key. |
| Value sentence | Before: a site needs a laptop, a toolchain, accounts and keys. After: one sentence on a phone. |
| Verbs (10) | type, send, read, write, check, build, preview, tweak, publish, loop |
| The product's objects | composer with the infinity send button; tool rows (Read, Wrote index.html +142); the live preview; generative-UI blocks (slider, choice); the model chip; the publish sheet with its URL |
| The number | files written 0 → 4, lines +142/+96/+38; photo columns 4 → 2 |
| Pain scene | a bus at night, an idea for a site, only a phone; every tutorial says install Node, an IDE, sign up, add an API key |
| Category clichés (banned here) | code rain, glowing "AI" orb, robot hands on keyboards, a laptop on a desk, a sparkle that means AI |
| World sound | soft key taps, a send tap, tiny confirming ticks as files land, a whoosh of a page, one clean hit |

## 2. Three concepts
| Lens | Logline (≤ 15 words) | Device | Ownership | Deletion | Specificity | Muted | Poster | Score /15 |
|---|---|---|---|---|---|---|---|---|
| (a) Verb made literal: "loop" | Orobi's loop keeps going until the site exists. | the loader stroke | pass: the loop is Oro's | value holds without the model beat | a code chatbot has no loop that builds | yes | loop + mark | 11 |
| (b) Pain made visible | All this, or one sentence. | toolchain pile → one loop | pass: the pile is generic, its collapse into Orobi's loop is not | value reads in hook + payoff | a simpler product still needs the pile | yes | lockup | 13 |
| (c) Formal device | One unbroken push from a sentence into a published site. | the camera | weak: any builder could do this | — | — | partly | site | 9 |

**Chosen:** (b) · **Grafted:** from (a) the loop as the one relay object through every shot; from (c) one continuous push from the send button into the live site (iris + runway).

## 3. Concept
- **Idea (≤ 8 words):** A website needs only one sentence now.
- **Device:** the infinity loop (Orobi's body, the app's loader). Born from the collapsing pile; it is the send button, the working spinner of each tool row, the iris seed into the live site, the model chip's icon, the publish spinner, and it resolves into Orobi and the lockup.
- **Grammar:** everything the old way needs piles up and is taken away; everything Oro does arrives from the loop.
- **Logline:** A pile of toolchain collapses into Orobi's loop, which builds and ships a site.
- **Personality:** friendly, nimble, exact → Poppins 500/600, sentence case, dark cool stage (ink-navy), corners 16–28 px, motion crisp.
- **Mark:** brand-supplied (Orobi, the app's mascot and launcher icon); not redesigned.
- **Accent:** magenta `#F800F8` (brand-supplied) means "Orobi is doing it". Success green `#3DDC97` only on checks (the app's own). **Stage:** dark. **Luminance plan:** cool dark pile → dark app → dark site → light site (theme flip) → dark app → dark lockup.
- **Signature move:** the loop draws itself (stroke dash travel) — open (born from the pile), middle (iris seed), close (becomes Orobi).
- **Easing characters:** `E.out` arrivals, `E.inOut` whips and lockup, `E.cam` camera; `E.in` for the implosion.
- **Sonic signature:** a two-note tuned "loop" motif (a rising fifth) on the loop's birth and on the lockup.

## 3b. Drawn techniques
`<!-- pick.py --seed 2295680993 --format launch --energy high --seconds 30 -->` (+ opening reroll `--seed 2585979266`)

| Pick (id) | Category | Beat # | Adaptation |
|---|---|---|---|
| oh-pile-up-freeze-turn | opening_hook | 1 | toolchain cards (npm errors, installers, key prompts, paywalls) pile in accelerating, 12 f freeze in silence, implode into the loop on the bar-3 downbeat |
| ui-row-cascade | ui_choreography | 3 | Orobi's tool rows stream in, 6 f apart per card, ink-in after |
| mi-spinner-to-check | micro_interaction | 3 | each row's working icon is the loop; it resolves to a green check as the file lands |
| tr-iris-from-glyph | transition | 4 | the loop on the last row is the seed; the live site opens inside it |
| pd-page-runway-flyover | product_demo | 5 | the finished site flown on a runway plane, carrier of the theme flip |
| cl-theme-flip-in-motion | color_and_light | 5 | the generative-UI "Light" tap flips the site from dark to light mid-flight |
| dn-linked-control-counters | data_and_numbers | 5 | Orobi answers with a UI block: one slider drives columns and photo size in the preview |
| ty-step-edit-performs-copy | typography_motion | — | dropped: no copy in this film is about fixing or undoing; the slider beat already performs "change" |
| tr-motion-masked-swap | transition | 6 | model names swap in the chip on the peak of a sweep |
| cam-crash-zoom-through | camera | 7 | the published URL card extrudes and the camera crashes through into the live site |
| le-stage-clears-to-brand | logo_and_end_card | 8 | the site drops away and uncovers the loop, which becomes Orobi and the lockup |
| ps-long-tail-beat-lengths | pacing_structure | whole film | beat lengths in timeline.ts follow a long tail |

**Rerolls and drops:** oh-macro-scramble-snap-out → oh-pile-up-freeze-turn (Oro has no single impressive metric; a fake number is banned filler). ty-step-edit-performs-copy dropped (see row).

## 4. Arc and device path
- **Arc:** hook/problem bars 1–2 → turn bar 3 → prompt bars 3–4 → proof bars 5–10 (build, live, change) → promise bars 11–14 (free, ship) → lockup bars 15–16 + tail.
- **Device path:** implosion point → loop draws (birth) → glides into the send button → spins as the active tool row's icon (relay down the cascade) → seed of the iris → rides the chat as Orobi's avatar → model chip icon → publish spinner → left behind when the site drops → becomes Orobi → lockup.

## 5. Beat sheet (120 BPM: bar = 120 f = 2 s)
| # | Bars | Time | Beat | Picture | Copy (fragment, mode) | Picture beat | Sound | Device |
|---|---|---|---|---|---|---|---|---|
| 1 | 1–2 | 0–4 s | hook | 3 toolchain cards on frame 0; 13 more pile in accelerating | "A website" → "takes all this." (build) | the pile thickens | one tick per card, rising; freeze in silence | — |
| 2 | 3 | 4–6 s | turn | pile implodes into a point; the loop draws itself; the Oro composer rises | "Or" → "one sentence." (replace) | loop glides into the send button | implosion suck, loop motif | born, then send button |
| 3 | 4 | 6–8 s | prompt | push in on the composer; the prompt types (UI) | — | typing | key ticks | idles in send |
| 4 | 5–6 | 8–12 s | build | send; tool rows cascade, each spinner → check | "Orobi" → "builds it," (build) | rows land | send tap, check pops climbing | spinner of each row |
| 5 | 7 | 12–14 s | live | iris from the loop into the live site building | "live." | sections land | whoosh, landings | iris seed |
| 6 | 8–9 | 14–18 s | change | back to chat: UI block slider drives columns 4 → 2 and size; tap "Light" | "Change anything." | slider drag | ticks on steps | Orobi avatar |
| 7 | 10 | 18–20 s | flyover | runway flight over the whole site; flips dark → light mid-flight | — | text-free | whoosh | — |
| 8 | 11–12 | 20–24 s | free | the model chip swaps names on sweeps | "Free models." → "No key." (replace) | swaps | soft clicks | chip icon |
| 9 | 13–14 | 24–28 s | ship | publish sheet: tap, spinner → check, URL card; crash zoom through | "Ship it." | URL lands | tap, check, whoosh | spinner |
| 10 | 15–16 + tail | 28–33 s | lockup | the site drops away; the loop becomes Orobi; lockup | "Oro" (wordmark) → "Your website, from one sentence." | lockup builds | stop-down, one hit, tail | becomes the mark |

## 6. Copy
| Chain | Fragment | Words | Mode | Beat # | Picture beat before next | Callback role |
|---|---|---|---|---|---|---|
| hook | A website | 2 | build | 1 | the pile accelerates | setup |
| hook | takes all this. | 3 | build | 1 | freeze, implosion | setup |
| turn | Or | 1 | replace | 2 | loop is born | refrain ("this" → "one") |
| turn | one sentence. | 2 | replace | 2 | loop flies to send | payoff |
| build | Orobi | 1 | build | 4 | first rows land | — |
| build | builds it, | 2 | build | 4 | iris into the site | — |
| live | live. | 1 | — | 5 | sections land | payoff |
| change | Change anything. | 2 | — | 6 | slider drags | — |
| free | Free models. | 2 | replace | 8 | names swap | — |
| free | No key. | 2 | replace | 8 | publish sheet | callback (the pile's key prompt) |
| ship | Ship it. | 2 | — | 9 | crash zoom | — |
| end | Oro | 1 | — | 10 | lockup | — |
| end | Your website, from one sentence. | 5 | — | 10 | — | resolution |
| **Total** | | **26 / 30** | | | | |

## 7. Final frame (the poster)
Dark ink-navy stage. Orobi (peach body, magenta markings) centred slightly above the eye line at about 26% of frame height, the "Oro" wordmark (Poppins 600, peach) beside it as one lockup, and "Your website, from one sentence." at the secondary size below, under a 10–15% push.

## 8. Gate
- [x] logline ≤ 15 words · [x] specificity test: a code chatbot would need the whole pile; Oro removes it · [x] 26 words ≤ 30 · [ ] grid-check passes (Step 3).
