---
name: backend
description: Oro's built-in REST API over db.json, plus production backends (Express, Supabase, auth)
---
# Backends in Oro

## 1. Built-in REST backend (works instantly in Oro's preview)
Oro's preview server includes a JSON database API, like json-server. Create `db.json` at the
project root with collections:
```json
{ "todos": [ { "id": 1, "title": "Ship it", "done": false } ], "users": [] }
```
Endpoints (all JSON, CORS enabled):
| Method | Path | Does |
|---|---|---|
| GET | `/api/todos` | list; query `?q=text`, `?done=true` (field filter), `?_sort=title&_order=desc`, `?_page=1&_limit=10` (sets `X-Total-Count`) |
| GET | `/api/todos/1` | one item (404 if missing) |
| POST | `/api/todos` | create; `id` auto-assigned; returns 201 + item |
| PUT/PATCH | `/api/todos/1` | replace / merge |
| DELETE | `/api/todos/1` | delete; 204 |
| GET | `/api` | list collections |
Data persists in `db.json`. Write a small client:
```js
// js/api.js
const BASE = '/api';
async function req(path, opts = {}){
  const res = await fetch(BASE + path, { headers: { 'Content-Type': 'application/json' }, ...opts,
    body: opts.body ? JSON.stringify(opts.body) : undefined });
  if(!res.ok) throw new Error((await res.json().catch(()=>({}))).error || `HTTP ${res.status}`);
  return res.status === 204 ? null : res.json();
}
export const api = {
  list: (c, params='') => req(`/${c}${params}`), get: (c, id) => req(`/${c}/${id}`),
  create: (c, body) => req(`/${c}`, { method:'POST', body }),
  update: (c, id, body) => req(`/${c}/${id}`, { method:'PATCH', body }),
  remove: (c, id) => req(`/${c}/${id}`, { method:'DELETE' }),
};
```
Always handle loading/error states around these calls (use_skill "ui-states").

## 2. Real production backends (code the user deploys elsewhere)
When asked for a real server, write it in a `server/` folder, complete and runnable:
- **Node + Express**: `server/package.json` (scripts: start, dev), `server/src/index.js`,
  routes split by resource, input validation (zod), central error handler returning
  `{error:{code,message}}`, CORS, helmet, rate limit, `.env.example`, README with run/deploy steps.
- **Database**: SQLite (better-sqlite3) for simple, Postgres (pg / Prisma) for production.
  Include schema/migration files and seed data.
- **Auth**: hashed passwords (bcrypt/argon2), httpOnly secure cookies or short-lived JWT +
  refresh, never store secrets in frontend code.
- **BaaS from the browser**: Supabase (`https://esm.sh/@supabase/supabase-js@2`) or Firebase
  modular SDK via CDN — put config in `js/config.js`, explain Row Level Security rules.
- REST conventions: plural nouns, proper status codes (200/201/204/400/401/403/404/409/422/500),
  pagination, consistent JSON shapes. Document endpoints in README.
Oro cannot run Node on the device: say so, keep the frontend working against the built-in
`/api` mock with the same shapes, so switching `BASE` to the deployed URL is the only change.
