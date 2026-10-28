# Web search providers (2026-10)

What `web_search` can reach, in try-order, and what each costs. The chain
skips anything blocked or rate-limited (10-minute cooldown) and caches
queries for five minutes, so one bad endpoint costs a timeout, never the
answer. Research for the settings screen (Settings → Web search) and for
whoever adds the next provider.

## Keyless built-ins (no account, scraped)

| id | endpoint | notes |
|---|---|---|
| `ddg_html` | html.duckduckgo.com/html/ (POST q) | Best quality; challenges datacenter IPs (202), fine from phones |
| `ddg_lite` | lite.duckduckgo.com/lite/ (POST q) | No-JS table layout; same IP sensitivity |
| `bing` | www.bing.com/search | Answers even from datacenter IPs (verified 2026-10 from molab); markup: `li.b_algo`, title in `b_algoheader`, snippet in `b_lineclamp*` |
| `mojeek` | www.mojeek.com/search | Independent index; 403s datacenter IPs, OK residential; strict rate limits |
| `searx` | public SearXNG instances, rotated | Instance-dependent: captchas (searx.be), 429s (paulgo.io, opnxng) from shared IPs; a user's own instance is the reliable path (custom provider) |
| `wikipedia` | en.wikipedia.org/w/api.php list=search | Always up; entities and facts, not news |

Redirects: DuckDuckGo wraps URLs in `/l/?uddg=`; unwrapped in
`decodeDdgRedirect`. Ads (`result--ad`) skipped.

## Free-tier presets (user pastes a key; responses read by `JsonHits`)

| id | endpoint | key goes in | free tier (2026-10) |
|---|---|---|---|
| `tinyfish` | GET api.search.tinyfish.ai?query= | header `X-API-Key` | Free on every plan, 30 req/min, no card (agent.tinyfish.ai) |
| `brave` | GET api.search.brave.com/res/v1/web/search | header `X-Subscription-Token` | 2,000/mo non-commercial; own index |
| `exa` | POST api.exa.ai/search | header `x-api-key` | 1,000/mo; neural/semantic index, weak on freshness |
| `tavily` | POST api.tavily.com/search | bearer | 1,000 credits/mo, no card |
| `serper` | POST google.serper.dev/search | header `X-API-KEY` | 2,500 one-time; Google SERP |
| `parallel` | POST api.parallel.ai/v1beta/search | bearer | Starter credits; own index |
| `firecrawl` | POST api.firecrawl.dev/v2/search | bearer | Free credits; search + extraction |
| `linkup` | GET api.linkup.so/v1/search | bearer | €5/mo credits; paywalled sources |
| `searchapi` | GET www.searchapi.io/api/v1/search?engine=google | query `api_key` | 100 free |
| `you` | GET api.you.com/smart | header `x-api-key` | Developer tier; own index |
| `jina` | GET s.jina.ai/{query} (Accept: json) | bearer | Free tier with key |

Benchmarks (third-party, salt required): AIMultiple Feb-26 put Brave top on
relevance×quality; Valyu Feb-26 put Parallel top on freshness; Exa low on
freshness, strong on semantic. Hence: several sources, user's order wins.

Not presets: Google CSE (needs a cx as well as a key -- wire it as a custom
provider, the template takes both), Perplexity Sonar (no free API tier),
SerpAPI/DataForSEO (paid), Kagi (paid).

## Custom providers (Settings → Web search → Add)

A URL template with `{query}`, `{limit}`, `{key}`; GET or POST with a body
template; the key as bearer, a named header or a named query param. Three
answer dialects:

- **JSON** -- read by `JsonHits`: walks the document, scores every array of
  objects by URL-ish/title-ish/snippet-ish fields, keeps the best. Works for
  every API above and for the next one; no parser per provider.
- **SearXNG** -- a private instance with `format=json` (same sniffer; the
  kind exists so the UI can prefill the template).
- **HTML** -- regex trio (url+title pattern, optional snippet pattern) for
  endpoints that only serve markup.

Test button runs one query through one provider and shows what came back,
so a provider proves itself before the agents rely on it.

## Where it plugs in

`WebSearch.search(query, limit)` is called by the coding agent's `web_search`
tool, by subagents (READ capability), and by the browser agent's `web_search`.
Config lives in DataStore (`web_search_v1`, PreferencesRepository), pushed
into `WebSearch.configure` by OroApplication's collector; keys never leave
the device and never enter a model's context.
