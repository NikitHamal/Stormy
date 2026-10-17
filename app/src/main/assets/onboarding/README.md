# Oro motion artwork

These scenes adapt the user-supplied Prompt-to-page, idle mascot, provider orbit,
and infinity-loader references. The mascot silhouette, clipped magenta markings,
peach skin, and ink eyes retain the supplied SVG geometry. No generated imagery.

- `welcome.html`: 3.2-second breathing and 4.2-second blink.
- `prompt.html`: 11-second prompt → infinity thinking → page assembly → ready loop.
- `models.html`: quiet provider orbit with staggered emphasis.
- `orobi.svg`: standalone vector logo.

The native Compose pager owns navigation, text, accessibility, and persistence.
Artwork is decorative. Local WebViews disable script, file/content access, and
network loads; they expose no bridge. CSP also blocks external resources. No CDN
fonts, provider requests, or paid calls are needed by onboarding.

`ORO_THEME` and `ORO_MOTION` are substituted by the native host. `still` freezes
inactive pages and respects Android animator-duration-scale = 0; CSS also respects
prefers-reduced-motion. Static scenes show the completed page, not a loading state.
Native controls remain available with large text, small displays, and landscape.
