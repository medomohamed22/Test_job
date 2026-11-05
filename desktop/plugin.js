// Hermes Ark — Desktop half: a "Ark" page (timeline, contents, changes, restore), a status-bar
// shield, and palette commands. Plain ESM, no build: UI is written with createElement, and only
// `@hermes/plugin-sdk` and `react` are imported (the loader refuses anything else).
import {
  Badge,
  Button,
  Codicon,
  ConfirmDialog,
  EmptyState,
  ErrorState,
  GlyphSpinner,
  PALETTE_AREA,
  ROUTES_AREA,
  ScrollArea,
  SegmentedControl,
  SIDEBAR_NAV_AREA,
  STATUSBAR_AREAS,
  StatusDot,
  host,
  queryClient,
  relativeTime,
  useQuery
} from '@hermes/plugin-sdk'
import { createElement as h, Fragment, useEffect, useMemo, useState } from 'react'

const ID = 'hermes-ark'
const PATH = '/ark'
const KEY = [ID]

const KIND = {
  memory: { icon: 'lightbulb', label: 'Memory' },
  soul: { icon: 'heart', label: 'SOUL' },
  skill: { icon: 'tools', label: 'Skills' },
  config: { icon: 'settings-gear', label: 'Config' },
  cron: { icon: 'watch', label: 'Cron' },
  sessions: { icon: 'comment-discussion', label: 'Sessions' },
  plugin: { icon: 'extensions', label: 'Plugins' },
  secrets: { icon: 'key', label: 'Credentials' },
  media: { icon: 'file-media', label: 'Media' },
  other: { icon: 'file', label: 'Other' }
}

const TRIGGER = {
  'auto:pre-write': { icon: 'shield', label: 'Before a memory/skill write' },
  auto: { icon: 'history', label: 'Automatic' },
  'pre-restore': { icon: 'discard', label: 'Safety copy before a restore' },
  agent: { icon: 'robot', label: 'Saved by the agent' },
  desktop: { icon: 'device-desktop', label: 'Saved from Desktop' },
  cli: { icon: 'terminal', label: 'Saved from the terminal' },
  chat: { icon: 'comment', label: 'Saved from chat' }
}

const RESTORE_CHOICES = [
  { id: 'memory', label: 'Memory' },
  { id: 'soul', label: 'SOUL' },
  { id: 'skills', label: 'Skills' },
  { id: 'config', label: 'Config' },
  { id: 'cron', label: 'Cron' },
  { id: 'all', label: 'Everything' }
]

// ---- styles: theme variables only, so every theme (light/dark/custom) reskins the page ----------
const S = {
  page: { display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 },
  header: { display: 'flex', alignItems: 'center', gap: 12, padding: '14px 20px 10px', borderBottom: '1px solid var(--ui-stroke-tertiary, var(--ui-stroke-secondary))' },
  title: { fontSize: 15, fontWeight: 600, color: 'var(--ui-text-primary)' },
  sub: { fontSize: 12, color: 'var(--ui-text-tertiary)' },
  cards: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: 10, padding: '12px 20px' },
  card: { border: '1px solid var(--ui-stroke-secondary)', borderRadius: 6, padding: '10px 12px', minWidth: 0 },
  cardLabel: { fontSize: 11, color: 'var(--ui-text-tertiary)', display: 'flex', alignItems: 'center', gap: 6 },
  cardValue: { fontSize: 13, color: 'var(--ui-text-primary)', marginTop: 4, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' },
  alert: { margin: '0 20px 10px', padding: '10px 12px', borderRadius: 6, border: '1px solid var(--ui-stroke-secondary)', background: 'var(--ui-bg-quaternary)', display: 'flex', gap: 10, alignItems: 'flex-start', fontSize: 12 },
  body: { display: 'grid', gridTemplateColumns: 'minmax(260px, 340px) 1fr', flex: 1, minHeight: 0, borderTop: '1px solid var(--ui-stroke-tertiary, var(--ui-stroke-secondary))' },
  list: { borderRight: '1px solid var(--ui-stroke-tertiary, var(--ui-stroke-secondary))', minHeight: 0 },
  day: { fontSize: 11, fontWeight: 600, color: 'var(--ui-text-tertiary)', padding: '12px 14px 4px', textTransform: 'uppercase', letterSpacing: 0.4 },
  row: (active) => ({ display: 'flex', gap: 10, alignItems: 'flex-start', width: '100%', textAlign: 'left', padding: '8px 14px', cursor: 'pointer', background: active ? 'var(--ui-bg-quaternary)' : 'transparent', border: 'none', color: 'inherit' }),
  rowMain: { fontSize: 12.5, color: 'var(--ui-text-primary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' },
  rowMeta: { fontSize: 11, color: 'var(--ui-text-tertiary)', marginTop: 2 },
  detail: { padding: '16px 20px 24px', minHeight: 0 },
  section: { fontSize: 11, fontWeight: 600, color: 'var(--ui-text-tertiary)', textTransform: 'uppercase', letterSpacing: 0.4, margin: '18px 0 8px' },
  group: { display: 'flex', alignItems: 'center', gap: 8, padding: '6px 0', fontSize: 12.5, borderBottom: '1px solid var(--ui-stroke-tertiary, transparent)' },
  change: (status) => ({ display: 'flex', alignItems: 'center', gap: 8, padding: '3px 0', fontSize: 12, fontFamily: 'var(--font-mono, ui-monospace, monospace)', color: status === 'removed' ? 'var(--ui-text-danger, var(--ui-text-secondary))' : 'var(--ui-text-secondary)' }),
  mono: { fontFamily: 'var(--font-mono, ui-monospace, monospace)', fontSize: 11.5 },
  restoreBox: { border: '1px solid var(--ui-stroke-secondary)', borderRadius: 6, padding: 12, display: 'flex', flexDirection: 'column', gap: 10 }
}

// ---- helpers --------------------------------------------------------------------------------
const size = (n) => {
  if (!n) return '0 B'
  const u = ['B', 'KB', 'MB', 'GB']
  let i = 0
  while (n >= 1024 && i < u.length - 1) { n /= 1024; i++ }
  return `${i ? n.toFixed(1) : n} ${u[i]}`
}
const when = (iso) => { const t = Date.parse(iso); return Number.isFinite(t) ? relativeTime(t) : '' }
const dayOf = (iso) => { const d = new Date(iso); return Number.isFinite(d.getTime()) ? d.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' }) : '' }
const timeOf = (iso) => { const d = new Date(iso); return Number.isFinite(d.getTime()) ? d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' }) : '' }
const icon = (name, extra) => h(Codicon, { name, style: { fontSize: 14, color: 'var(--ui-text-tertiary)', ...extra } })
const invalidate = () => queryClient.invalidateQueries({ queryKey: KEY })

function report(res, okMessage) {
  const ok = res && res.ok !== false
  host.notify({ kind: ok ? 'success' : 'error', message: (res && res.message) || okMessage })
  invalidate()
}

// ---- data ------------------------------------------------------------------------------------
function makeApi(ctx) {
  return {
    status: () => ctx.rest('/status'),
    snapshots: () => ctx.rest('/snapshots'),
    detail: (id) => ctx.rest(`/snapshots/${encodeURIComponent(id)}`),
    diff: (id) => ctx.rest(`/diff?new=${encodeURIComponent(id)}`),
    preview: (what, snapshot) => ctx.rest('/restore/preview', { method: 'POST', body: { what, snapshot } }),
    restore: (what, snapshot) => ctx.rest('/restore', { method: 'POST', body: { what, snapshot, confirm: true }, timeoutMs: 120000 }),
    snap: (label) => ctx.rest('/snapshot', { method: 'POST', body: { label }, timeoutMs: 120000 }),
    verify: (remote) => ctx.rest('/verify', { method: 'POST', body: { remote, quick: false }, timeoutMs: 600000 }),
    push: () => ctx.rest('/push', { method: 'POST', timeoutMs: 600000 })
  }
}

// ---- page ------------------------------------------------------------------------------------
function ArkPage({ api }) {
  const status = useQuery({ queryKey: [...KEY, 'status'], queryFn: api.status, refetchInterval: 30000 })
  const list = useQuery({ queryKey: [...KEY, 'snapshots'], queryFn: api.snapshots, refetchInterval: 30000 })
  const [selected, setSelected] = useState(null)
  const [busy, setBusy] = useState(null)
  const snaps = (list.data && list.data.snapshots) || []

  useEffect(() => { if (!selected && snaps.length) setSelected(snaps[0].id) }, [snaps.length])

  const run = async (name, fn, okMessage) => {
    setBusy(name)
    try { report(await fn(), okMessage) } catch (e) { host.notify({ kind: 'error', message: String((e && e.message) || e) }) } finally { setBusy(null) }
  }

  if (status.isLoading) return h('div', { style: { padding: 40, display: 'grid', placeItems: 'center' } }, h(GlyphSpinner, {}))
  if (status.error) return h(ErrorState, { title: 'Ark backend unavailable', description: 'Enable the hermes-ark plugin for this profile (hermes plugins enable hermes-ark) and restart the gateway.' })
  const st = status.data
  if (st.state === 'not_initialized') {
    return h(EmptyState, { title: 'Ark is not set up yet', description: 'Run `hermes ark init` in a terminal to create an encrypted backup repository. Snapshots then happen automatically.' })
  }
  if (st.state === 'locked') {
    return h(EmptyState, { title: 'Repository locked', description: 'Set ARK_PASSPHRASE in this profile\'s secrets so Ark can open its encrypted repository.' })
  }

  const guard = (st.events || []).filter((e) => e.kind === 'guard').slice(0, 1)[0]
  return h('div', { style: S.page },
    h('div', { style: S.header },
      h(Codicon, { name: 'shield', style: { fontSize: 20, color: 'var(--ui-accent)' } }),
      h('div', { style: { flex: 1, minWidth: 0 } },
        h('div', { style: S.title }, 'Ark'),
        h('div', { style: S.sub }, 'Encrypted, versioned backups of this Hermes — memory, SOUL, skills, config, cron, sessions')),
      h(Button, { variant: 'secondary', disabled: !!busy, onClick: () => run('verify', () => api.verify(false), 'Verified') },
        busy === 'verify' ? h(GlyphSpinner, {}) : h(Codicon, { name: 'verified' }), 'Verify'),
      st.remote && st.remote.url ? h(Button, { variant: 'secondary', disabled: !!busy, onClick: () => run('push', api.push, 'Pushed') },
        busy === 'push' ? h(GlyphSpinner, {}) : h(Codicon, { name: 'cloud-upload' }), 'Push') : null,
      h(Button, { disabled: !!busy, onClick: () => run('snap', () => api.snap('from Desktop'), 'Snapshot saved') },
        busy === 'snap' ? h(GlyphSpinner, {}) : h(Codicon, { name: 'add' }), 'Snapshot now')),
    h(StatusCards, { st }),
    guard ? h('div', { style: S.alert },
      h(Codicon, { name: 'warning', style: { fontSize: 16, color: 'var(--ui-text-warning, var(--ui-accent))' } }),
      h('div', { style: { flex: 1 } },
        h('div', { style: { fontWeight: 600, color: 'var(--ui-text-primary)' } }, 'Health guard: ', guard.message),
        h('div', { style: S.sub }, `${when(guard.at)} · the version before the change is saved`)),
      guard.snapshot ? h(Button, { size: 'sm', variant: 'outline', onClick: () => setSelected(guard.snapshot) }, 'Review & restore') : null) : null,
    h('div', { style: S.body },
      h('div', { style: S.list }, h(ScrollArea, { style: { height: '100%' } }, h(Timeline, { snaps, selected, onSelect: setSelected }))),
      h(ScrollArea, { style: { height: '100%' } },
        selected ? h(Detail, { key: selected, api, id: selected }) : h(EmptyState, { title: 'No snapshots yet', description: 'Take one with “Snapshot now”.' }))))
}

function StatusCards({ st }) {
  const latest = st.latest
  const remote = st.remote || {}
  const ver = st.last_verify
  const card = (ic, label, value, tone) => h('div', { style: S.card },
    h('div', { style: S.cardLabel }, icon(ic, { fontSize: 13 }), label, tone ? h(StatusDot, { tone }) : null),
    h('div', { style: S.cardValue, title: value }, value))
  return h('div', { style: S.cards },
    card('lock', 'Protection', st.encrypted ? 'AES-256-GCM encrypted' : 'Not encrypted', st.encrypted ? 'good' : 'warn'),
    card('history', 'Latest snapshot', latest ? `${when(latest.created)} · ${latest.files} files` : 'none yet', latest ? 'good' : 'muted'),
    card('cloud', 'Off-site copy', remote.url ? (remote.last_push ? `pushed ${when(remote.last_push.at)}` : 'configured, not pushed') : 'none', remote.url && remote.last_push ? 'good' : 'muted'),
    card('verified', 'Last verify', ver ? `${ver.ok ? 'all restorable' : 'problems found'} · ${when(ver.at)}` : 'never', ver ? (ver.ok ? 'good' : 'bad') : 'muted'),
    card('shield', 'Autopilot', st.autopilot && st.autopilot.on ? `on · guard ${st.autopilot.health_guard ? 'on' : 'off'}` : 'off', st.autopilot && st.autopilot.on ? 'good' : 'warn'))
}

function Timeline({ snaps, selected, onSelect }) {
  const days = useMemo(() => {
    const out = []
    for (const s of snaps) {
      const d = dayOf(s.created)
      if (!out.length || out[out.length - 1].day !== d) out.push({ day: d, items: [] })
      out[out.length - 1].items.push(s)
    }
    return out
  }, [snaps])
  if (!snaps.length) return h(EmptyState, { title: 'No snapshots yet' })
  return h(Fragment, {}, days.map((g) => h(Fragment, { key: g.day },
    h('div', { style: S.day }, g.day),
    g.items.map((s) => {
      const t = TRIGGER[s.trigger] || { icon: 'circle-small', label: s.trigger }
      return h('button', { key: s.id, type: 'button', style: S.row(s.id === selected), onClick: () => onSelect(s.id) },
        icon(t.icon, { marginTop: 1 }),
        h('div', { style: { minWidth: 0, flex: 1 } },
          h('div', { style: S.rowMain }, s.label || t.label),
          h('div', { style: S.rowMeta }, `${timeOf(s.created)} · ${s.files} files · ${s.new_bytes ? '+' + size(s.new_bytes) : 'no new data'}`)),
        s.scope ? h(Badge, { variant: 'muted', size: 'xs' }, 'brain') : null)
    }))))
}

function Detail({ api, id }) {
  const detail = useQuery({ queryKey: [...KEY, 'detail', id], queryFn: () => api.detail(id) })
  const diff = useQuery({ queryKey: [...KEY, 'diff', id], queryFn: () => api.diff(id) })
  if (detail.isLoading) return h('div', { style: { padding: 30 } }, h(GlyphSpinner, {}))
  if (detail.error) return h(ErrorState, { title: 'Could not read this snapshot', description: String(detail.error.message || detail.error) })
  const d = detail.data
  const t = TRIGGER[d.trigger] || { label: d.trigger }
  const changes = (diff.data && diff.data.changes) || []
  return h('div', { style: S.detail },
    h('div', { style: { display: 'flex', alignItems: 'center', gap: 8 } },
      h('div', { style: { ...S.title, fontSize: 14 } }, d.label || t.label),
      d.scope ? h(Badge, { variant: 'muted' }, 'brain only') : h(Badge, {}, 'full'),
      d.secrets ? h(Badge, { variant: 'warn' }, 'includes credentials') : null),
    h('div', { style: { ...S.sub, marginTop: 4 } }, `${new Date(d.created).toLocaleString()} · ${t.label} · ${d.host || ''}`),
    h('div', { style: { ...S.mono, ...S.sub, marginTop: 2 } }, d.id),

    h('div', { style: S.section }, 'Contents'),
    d.groups.map((g) => {
      const k = KIND[g.kind] || KIND.other
      return h('div', { key: g.profile + g.kind, style: S.group },
        icon(k.icon),
        h('div', { style: { width: 110, color: 'var(--ui-text-primary)' } }, k.label, g.profile !== 'default' ? h('span', { style: S.sub }, ` · ${g.profile}`) : null),
        h('div', { style: { ...S.sub, width: 120 } }, `${g.files} file${g.files === 1 ? '' : 's'} · ${size(g.bytes)}`),
        h('div', { style: { ...S.sub, flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' } }, (g.kind === 'skill' || g.kind === 'plugin') ? g.items.join(', ') : ''))
    }),

    h('div', { style: S.section }, d.previous ? 'Changes since the previous snapshot' : 'First snapshot'),
    diff.isLoading ? h(GlyphSpinner, {}) : changes.length === 0 && d.previous ? h('div', { style: S.sub }, 'No changes.') :
      changes.slice(0, 80).map((c) => h('div', { key: c.path, style: S.change(c.status) },
        h('span', { style: { width: 12, color: c.status === 'added' ? 'var(--ui-text-success, var(--ui-accent))' : undefined } }, c.status === 'added' ? '+' : c.status === 'removed' ? '−' : '~'),
        icon((KIND[c.kind] || KIND.other).icon, { fontSize: 12 }),
        h('span', { style: { flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' } }, c.path),
        h('span', { style: S.sub }, c.status === 'modified' ? `${size(c.size_before)} → ${size(c.size_after)}` : size(c.size_after || c.size_before)))),
    changes.length > 80 ? h('div', { style: S.sub }, `… and ${changes.length - 80} more`) : null,

    h('div', { style: S.section }, 'Restore from this snapshot'),
    h(RestoreBox, { api, id: d.id, skills: (d.groups.find((g) => g.kind === 'skill' && g.profile === 'default') || { items: [] }).items }))
}

function RestoreBox({ api, id, skills }) {
  const [what, setWhat] = useState('memory')
  const [plan, setPlan] = useState(null)
  const [loading, setLoading] = useState(false)
  const target = what
  const openPreview = async (w) => {
    setLoading(true)
    try { setPlan({ ...(await api.preview(w || target, id)), request: w || target }) } catch (e) { host.notify({ kind: 'error', message: String((e && e.message) || e) }) } finally { setLoading(false) }
  }
  return h('div', { style: S.restoreBox },
    h(SegmentedControl, { options: RESTORE_CHOICES, value: what, onChange: setWhat, sizing: 'content' }),
    skills.length ? h('div', { style: { ...S.sub, display: 'flex', flexWrap: 'wrap', gap: 6, alignItems: 'center' } }, 'Or one skill:',
      skills.slice(0, 12).map((s) => h(Button, { key: s, size: 'xs', variant: 'chip', onClick: () => openPreview(`skill:${s}`) }, s))) : null,
    h('div', { style: { display: 'flex', gap: 8, alignItems: 'center' } },
      h(Button, { variant: 'outline', disabled: loading, onClick: () => openPreview() }, loading ? h(GlyphSpinner, {}) : h(Codicon, { name: 'discard' }), 'Preview restore…'),
      h('span', { style: S.sub }, 'Nothing changes until you confirm. A safety snapshot is taken first.')),
    plan ? h(ConfirmDialog, {
      open: true,
      onClose: () => setPlan(null),
      title: `Restore ${plan.what}?`,
      description: plan.blocked ? 'This includes the sessions database, which can only be restored with Hermes stopped: run `hermes gateway stop` then `hermes ark restore` in a terminal.' :
        (plan.write.length + plan.delete.length === 0 ? `Already identical to this snapshot (${plan.unchanged} files).` : `${plan.write.length} file(s) will be restored and ${plan.delete.length} removed. A safety snapshot is taken first, so this can be undone.`),
      confirmLabel: 'Restore',
      busyLabel: 'Restoring…',
      destructive: true,
      onConfirm: async () => {
        if (plan.blocked || plan.write.length + plan.delete.length === 0) return
        const res = await api.restore(plan.request, plan.snapshot)
        report(res, 'Restored')
        if (res && res.ok === false) throw new Error(res.message)
      }
    }, h('div', { style: { maxHeight: 220, overflow: 'auto', marginTop: 8 } },
      plan.note ? h('div', { style: { ...S.sub, marginBottom: 6 } }, plan.note) : null,
      plan.write.map((w) => h('div', { key: w.path, style: S.change('modified') }, h('span', { style: { width: 12 } }, '↺'), h('span', { style: { flex: 1 } }, w.path), h('span', { style: S.sub }, `${w.action} · ${size(w.bytes)}`))),
      plan.delete.map((p) => h('div', { key: p, style: S.change('removed') }, h('span', { style: { width: 12 } }, '−'), h('span', { style: { flex: 1 } }, p), h('span', { style: S.sub }, 'not in snapshot'))))) : null)
}

// ---- status bar shield --------------------------------------------------------------------------
function ShieldChip({ api }) {
  const { data } = useQuery({ queryKey: [...KEY, 'status'], queryFn: api.status, refetchInterval: 60000, retry: false })
  if (!data || data.state !== 'ready') return null
  const recentGuard = (data.events || []).find((e) => e.kind === 'guard' && Date.now() - Date.parse(e.at) < 24 * 3600 * 1000)
  const label = recentGuard ? 'Ark: check memory' : data.latest ? `Ark · ${when(data.latest.created)}` : 'Ark'
  return h('button', {
    type: 'button',
    title: recentGuard ? `Health guard: ${recentGuard.message}` : 'Hermes Ark backups',
    onClick: () => host.navigate(PATH),
    style: { display: 'inline-flex', alignItems: 'center', gap: 4, padding: '0 6px', fontSize: 11, background: 'transparent', border: 'none', cursor: 'pointer', color: recentGuard ? 'var(--ui-text-warning, var(--ui-accent))' : 'var(--ui-text-tertiary)' }
  }, h(Codicon, { name: recentGuard ? 'warning' : 'shield', style: { fontSize: 12 } }), label)
}

export default {
  id: ID,
  name: 'Hermes Ark',
  register(ctx) {
    const api = makeApi(ctx)
    ctx.registerMany([
      { id: 'page', area: ROUTES_AREA, data: { path: PATH }, render: () => h(ArkPage, { api }) },
      { id: 'nav', area: SIDEBAR_NAV_AREA, data: { path: PATH, label: 'Ark', codicon: 'shield' } },
      { id: 'chip', area: STATUSBAR_AREAS.right, order: 140, render: () => h(ShieldChip, { api }) },
      { id: 'open', area: PALETTE_AREA, data: { id: 'hermes-ark.open', label: 'Ark: Open backups', keywords: ['backup', 'restore', 'snapshot', 'ark'], run: () => host.navigate(PATH) } },
      {
        id: 'snap',
        area: PALETTE_AREA,
        data: {
          id: 'hermes-ark.snapshot',
          label: 'Ark: Take a snapshot now',
          keywords: ['backup', 'snapshot', 'ark'],
          run: async () => {
            try { report(await api.snap('from Desktop palette'), 'Snapshot saved') } catch (e) { host.notify({ kind: 'error', message: String((e && e.message) || e) }) }
          }
        }
      }
    ])
  }
}
