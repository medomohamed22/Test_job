# HyperBrowse Studio v0.9 — Vision vs DOM Benchmark

## Deploy (mobile-friendly)
Unzip and upload the files to your existing GitHub repository at its root; import or redeploy on Vercel with Framework Preset **Other**. The interface includes provider settings for Meta, Gemini, OpenRouter and custom OpenAI-compatible HTTPS API endpoints. Keys are entered in the browser UI (localStorage), then sent to your own Vercel function per run; no Vercel environment secrets required. **Use limited test keys.** The public serverless endpoints are unauthenticated and can incur costs, so do not treat this as a secure public SaaS deployment.

## New v0.9 benchmark
- **Visual baseline:** for each of three predetermined pages, capture a JPEG screenshot and **send it to a vision-capable model** for visual observation. Do not silently pretend vision worked if provider rejects image inputs.
- **Optimized:** navigate to the exact same three predefined URLs, extract structured information from HTML DOM, and call the model once for evidence synthesis; only capture and send a screenshot if expected DOM elements are absent.
- **Fair verification:** evaluate both runs against the same independent DOM extraction conditions. Both modes use fixed targets; this benchmark measures visual observation versus DOM-first observation **rather than fully autonomous AI browser navigation**.
- 4 site suites: Wikipedia, Hacker News, MDN, GitHub. 1–3 repeat runs, alternating execution order. Dashboard shows recent run and mean timings; JSON report contains every run.
- Metrics: elapsed time, AI calls, vision calls and fallbacks, screenshots, DOM reads, browser navigations, error count, verified outcome, and *actual reported input/output tokens when all provider calls supply usage*. Billing may differ.

## Constraints
- Vercel runs ephemeral serverless Chromium processes: no live streaming and no persistent Chrome between runs.
- Each benchmark API invocation may take significant time; repeated runs are sequential and may use paid API credits.
- Sites can change, block automation, time out or alter their selectors. Some models or providers do not support image messages; those cannot run the visual baseline.
- Never assume the reported mean from a few runs proves superiority: use both success rates and elapsed times. No performance gains claimed before real hosted experiments.
- Screenshots are sent to the selected provider in baseline; **do not test confidential/authenticated pages**.
- Navigation checks block private/local destinations, but public deployment of automated browsing remains risky; prefer private demos and strict usage budgets.

## Validate
`npm install`
`node --check api/benchmark.js`
`node --check api/run.js`
Use `vercel dev` locally or deploy to Vercel to test actual Chromium. No model keys or Vercel deployment testing was available during packaging.
