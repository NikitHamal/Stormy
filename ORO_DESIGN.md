# Oro / orobi — first design pass

A restrained mobile workspace with one expressive brand element: orobi.
The supplied peach-and-magenta infinity mascot is the visual anchor. Cool-neutral
surfaces, Poppins typography, a clear primary action, and short copy keep the
onboarding readable. The app retains its package and data paths for compatibility.

## Changes

- Three onboarding scenes: meet orobi, prompt-to-page, provider choice.
- Native pager, swipe, back-to-previous-page, skip, and explicit final action.
- Save onboarding completion before navigation; guard duplicate submissions and
  provide a retry state when persistence fails.
- Scrollable copy for compact screens / large text, two columns on wide screens,
  native text and controls for screen readers, decorative artwork hidden from them.
- Light/dark artwork with reduced-motion and inactive-page static states.
- Supplied mascot geometry used for adaptive/legacy launchers and home/chat welcomes.
- Oro app label, orobi assistant identity, and shared cool-neutral theme tokens.

## Validation

The local browser renders all three motion scenes in light and dark modes without
page errors or horizontal overflow at 390 × 330. Android compilation was attempted
with JDK 21; this workspace initially has no Android SDK. See the branch's Android
CI for authoritative compile/test results. Browser previews are artwork/design
previews, not screenshots from an Android emulator.

## Scope

This pass prioritizes onboarding and brand continuity into the home/chat surfaces.
Editor, Git, providers, storage, model behavior, and project flows retain their
existing functionality. Subsequent product changes can build on these foundations.
