# HyperBrowse Vercel Chromium MVP v0.5 (Mobile-first)

No Vercel environment variables required. No app password.

## Deploy
1. Upload `index.html`, `api/run.js`, `package.json` and `vercel.json` to a GitHub repository.
2. Import it at https://vercel.com/new with **Framework Preset: Other**.
3. Deploy without entering Environment Variables.
4. Open the site on your phone, tap Settings, choose Meta / Gemini / OpenRouter / Custom, input API key and model, tap **حفظ على الجهاز**, then run a test.

## Security and limitations
- Keys are stored in your **browser's localStorage** for convenience; this is not encrypted or secure against malicious JavaScript, browser extensions, device access, or XSS. Use limited-scope test keys and never share devices with stored keys.
- The selected provider key is sent to your Vercel Function **with each task over HTTPS** so the server can invoke the model; it is held transiently in memory and is not intentionally persisted. Do not enable logging request bodies. The key cannot stay strictly browser-only if the server calls the model.
- **No app password or login** means your public `/api/run` endpoint can be triggered by others. The site allowlist, seven-step cap and model key requirement limit some misuse but do not replace server-side authentication, rate limits, or abuse prevention. Use for private demos only; remove the deployment if abused. Anonymous requests can still consume Chromium compute.
- Custom API URL must be public HTTPS, not localhost/private address; it uses a simple syntactic blocklist, not complete DNS rebinding protection. Do not expose this endpoint unprotected for public use.
- Real headless Chromium is started per run, and screenshots are replayed after completion. No live video stream; no persistent sessions.
- Requires Chromium bundle support and adequate Vercel Function resources. Has **not** been deployed or integration tested on Vercel in this environment.
