---
name: dashboard
description: Admin and analytics dashboards — app shell, KPI cards, charts, sortable/searchable data tables, filters, CRUD, every state
---
# Dashboards that look like a real product

Start with `add_ui_kit` (gives `.app-shell`, `.stats`, `.table`, `.chart`, `Oro.table`, `Oro.chart`,
`Oro.api`) and `use_skill "backend"` for the data (`db.json` collections served at `/api`).

## Layout
```html
<div class="app-shell">
  <aside class="sidebar"> logo · <a class="sidebar-link" href="index.html">Overview</a> … · account at the bottom </aside>
  <div>
    <header class="app-top"><button class="btn btn-icon btn-ghost" data-sidebar-toggle aria-label="Menu">☰</button> Title</header>
    <main class="app-main"> page title + primary action · filters · .stats · charts · table </main>
  </div>
</div>
```
Phones: the sidebar becomes a drawer (kit handles it); tables scroll horizontally inside
`.table-wrap`; KPI cards stack; nothing overflows at 360px.

## Content
- **KPI row** (`.stats` > `.stat`): 3–4 numbers that matter (revenue, orders, active users,
  conversion) with `.stat-delta` vs the previous period (`.is-down` when negative). Use
  `data-count` for count-up. Numbers are tabular, formatted (`Intl.NumberFormat`, currency, %).
- **Charts**: `Oro.chart(el, {type:'line', labels, values, format})` for trends,
  `type:'bar'` for comparisons, in a `.card` with a title and the period. Derive values from the
  data (group by day/week/category in JS) — never hard-code numbers that contradict the table.
- **Table**: `Oro.table(el, {columns, rows, pageSize: 10, search: input, onRow})` with real column
  formatting (dates via `Intl.DateTimeFormat`, money right-aligned with `num: true`, status as
  `.badge`), a search box, filter selects (status, date range) that refetch with query params
  (`?status=paid&_sort=createdAt&_order=desc`), and row click → detail drawer/modal.
- **CRUD**: "New" opens a dialog with a validated form (use_skill "forms"); create/update with
  `Oro.api.post/patch`, delete with `await Oro.confirm({danger: true})` then `Oro.api.del`; optimistic
  update + `Oro.toast`; refresh the KPIs and charts after a change.
- **Seed data**: write 20–60 realistic records into `db.json` (varied names, amounts, dates across
  the last 8 weeks, statuses) so every chart and filter has something to show.

## States (all of them)
Loading: skeletons shaped like the cards/table (`Oro.skeleton`). Empty: `.empty` with a reason and
the primary action. Error: `.alert-error` with Retry. Filters with no results: say which filter.

## Polish
One accent colour; neutrals for chrome; status colours only for meaning. Sticky table header,
hover rows, focus-visible everywhere, `aria-sort` on sortable headers (the kit sets it). Dark
mode via `data-theme-toggle`. Respect reduced motion.
