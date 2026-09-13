import React, { useEffect, useState } from 'react'
import { api } from './api/client.js'
import AccountsPage from './pages/AccountsPage.jsx'
import VoyagesPage from './pages/VoyagesPage.jsx'
import TransfersPage from './pages/TransfersPage.jsx'
import DemoPage from './pages/DemoPage.jsx'

const TABS = [
  ['accounts', '余额账本'],
  ['voyages', '航次与卸货'],
  ['transfers', '配额调拨'],
  ['demo', '样例验证']
]

export default function App() {
  const [tab, setTab] = useState('accounts')
  const [meta, setMeta] = useState(null)
  const [error, setError] = useState(null)
  const [focusVoyageId, setFocusVoyageId] = useState(null)

  useEffect(() => {
    api.meta().then(setMeta).catch((e) => setError(e.message))
  }, [])

  const openVoyage = (id) => {
    setFocusVoyageId(id)
    setTab('voyages')
  }

  return (
    <div className="app">
      <header>
        <h1>渔业合作社配额账本</h1>
        <p className="disclaimer">
          {meta?.disclaimer ?? '本系统使用虚构物种与许可规则，不连接任何监管系统，不作为真实捕捞许可。'}
        </p>
        <nav>
          {TABS.map(([key, label]) => (
            <button key={key} className={tab === key ? 'active' : ''} onClick={() => setTab(key)}>
              {label}
            </button>
          ))}
        </nav>
      </header>
      {error && <div className="error">{error}</div>}
      <main>
        {tab === 'accounts' && <AccountsPage onOpenVoyage={openVoyage} />}
        {tab === 'voyages' && <VoyagesPage meta={meta} focusVoyageId={focusVoyageId} />}
        {tab === 'transfers' && <TransfersPage />}
        {tab === 'demo' && <DemoPage />}
      </main>
    </div>
  )
}
