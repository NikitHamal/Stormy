---
name: landing-page
description: Structure, copy and polish for landing pages, marketing sites and portfolios
---
# Landing pages that convert

## Structure (pick what fits; order matters)
1. **Header**: logo, 3–5 links, one CTA button. Sticky, blurred, compact on scroll; mobile menu.
   Style the links by class (`.nav-link`), not `.nav a`: that selector outranks `.btn` and turns the
   header CTA's text the link colour (brown text on a brown button).
2. **Hero**: benefit-led headline (≤ 8 words), one-sentence subhead, primary + secondary CTA,
   visual (product shot in a device/browser frame, illustration, or animated gradient).
   Social proof right under CTA ("Trusted by 12,000 teams" + avatars/stars).
3. **Logo strip** (marquee, greyscale → colour on hover).
4. **Problem → solution** or **Features**: 3–6 cards with icon, title, 1–2 lines. Or alternating
   image/text rows with "zig-zag" layout.
5. **How it works**: 3 numbered steps.
6. **Showcase / stats**: big numbers that count up.
7. **Testimonials**: real-sounding names, roles, photos (`https://i.pravatar.cc/96?img=12`).
8. **Pricing**: 3 tiers, middle highlighted "Most popular", monthly/yearly toggle with savings badge.
9. **FAQ**: accessible accordion (`<details><summary>` styled, animated chevron).
10. **Final CTA band** in brand colour. 11. **Footer**: columns, socials, legal, newsletter input.

## Copy
Write real, specific copy for the brief — never lorem ipsum. Headlines speak to outcomes ("Ship
your store this weekend"), not features. Buttons are verbs ("Start free trial"). Use the user's
brand name everywhere. If the brand/product name is unknown and matters, ask once.

## Visual polish checklist
- Hero takes ≥ 80svh on desktop, content vertically centred; on mobile no giant empty gaps.
- A distinctive visual idea (gradient mesh, bento grid, oversized type, 3D-tilted mockup).
- Bento grid for features: `grid-template-columns:repeat(4,1fr)`, items spanning 2 cols/rows.
- Section rhythm: alternate background (`--bg` / `--surface`) or use dividers; consistent padding.
- Scroll reveals with stagger; hero headline animates word by word; numbers count up.
- Every link works (`#section` anchors with `scroll-margin-top` for the sticky header).
- `<title>`, meta description, Open Graph tags, favicon (inline SVG data URI is fine).
