const base = '/api'

async function request(path, options = {}) {
  const res = await fetch(base + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  })
  if (!res.ok) {
    let message = res.statusText
    try {
      const body = await res.json()
      if (body.error) message = body.error
    } catch { /* ignore */ }
    throw new Error(message)
  }
  return res.json()
}

export const api = {
  meta: () => request('/meta'),
  accounts: () => request('/accounts'),
  ledger: (id) => request(`/accounts/${id}/ledger`),
  transfers: () => request('/transfers'),
  transfer: (payload) => request('/transfers', { method: 'POST', body: JSON.stringify(payload) }),
  voyages: () => request('/voyages'),
  createVoyage: (payload) => request('/voyages', { method: 'POST', body: JSON.stringify(payload) }),
  declareVoyage: (id) => request(`/voyages/${id}/declare`, { method: 'POST' }),
  landings: (id) => request(`/voyages/${id}/landings`),
  addLanding: (id, payload) => request(`/voyages/${id}/landings`, { method: 'POST', body: JSON.stringify(payload) }),
  verifyLanding: (id, verifiedWeight) =>
    request(`/voyages/landings/${id}/verify`, { method: 'POST', body: JSON.stringify({ verifiedWeight }) }),
  closeVoyage: (id) => request(`/voyages/${id}/close`, { method: 'POST' }),
  cancelVoyage: (id) => request(`/voyages/${id}/cancel`, { method: 'POST' }),
  deleteVoyage: (id) => request(`/voyages/${id}`, { method: 'DELETE' }),
  runDemo: () => request('/demo/run', { method: 'POST' }),
  // 分类修订
  landingComponents: (id) => request(`/landings/${id}/components`),
  landingRevisions: (id) => request(`/landings/${id}/revisions`),
  applyRevision: (id, payload) =>
    request(`/landings/${id}/revisions`, { method: 'POST', body: JSON.stringify(payload) }),
  overturnRevision: (id) => request(`/revisions/${id}/overturn`, { method: 'POST' }),
  // 缺口与结转
  shortfalls: () => request('/shortfalls'),
  carryovers: () => request('/carryovers'),
  carryover: (payload) => request('/carryovers', { method: 'POST', body: JSON.stringify(payload) }),
  // 回放
  replay: (id, at) => request(`/accounts/${id}/replay${at ? `?at=${encodeURIComponent(at)}` : ''}`)
}
