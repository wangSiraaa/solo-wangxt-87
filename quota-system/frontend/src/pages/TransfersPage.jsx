import React, { useEffect, useState } from 'react'
import { api } from '../api/client.js'

/** 配额调拨：保存来源账户与生效期间，仅限同 物种/海区/季节 维度 */
export default function TransfersPage() {
  const [transfers, setTransfers] = useState([])
  const [accounts, setAccounts] = useState([])
  const [error, setError] = useState(null)
  const [form, setForm] = useState({
    fromAccountId: '', toAccountId: '', amount: '', effectiveFrom: '', effectiveTo: '', reason: ''
  })

  const reload = () => {
    api.transfers().then(setTransfers).catch((e) => setError(e.message))
    api.accounts().then(setAccounts).catch((e) => setError(e.message))
  }
  useEffect(() => { reload() }, [])

  const accountLabel = (a) =>
    `#${a.accountId} ${a.vesselName} / ${a.speciesName} / ${a.areaName} / ${a.seasonName}（可用 ${a.available} kg）`

  const accountBrief = (id) => {
    const a = accounts.find((x) => x.accountId === id)
    return a ? `${a.vesselName} / ${a.speciesName} / ${a.areaName} / ${a.seasonName}` : `账户#${id}`
  }

  const submit = (e) => {
    e.preventDefault()
    api.transfer(form)
      .then(() => { reload(); setForm({ ...form, amount: '', reason: '' }) })
      .catch((err) => setError(err.message))
  }

  return (
    <section>
      <h2>配额调拨</h2>
      <p className="hint">仅允许同物种、同海区、同季节账户之间调拨；来源与生效期间永久留痕。</p>
      {error && <div className="error" onClick={() => setError(null)}>{error}</div>}

      <form onSubmit={submit} className="bar wrap">
        <select value={form.fromAccountId} onChange={(e) => setForm({ ...form, fromAccountId: e.target.value })} required>
          <option value="">来源账户…</option>
          {accounts.map((a) => <option key={a.accountId} value={a.accountId}>{accountLabel(a)}</option>)}
        </select>
        <select value={form.toAccountId} onChange={(e) => setForm({ ...form, toAccountId: e.target.value })} required>
          <option value="">去向账户…</option>
          {accounts.map((a) => <option key={a.accountId} value={a.accountId}>{accountLabel(a)}</option>)}
        </select>
        <input type="number" placeholder="数量 kg" value={form.amount}
               onChange={(e) => setForm({ ...form, amount: e.target.value })} required />
        <input type="date" value={form.effectiveFrom}
               onChange={(e) => setForm({ ...form, effectiveFrom: e.target.value })} required />
        <input type="date" value={form.effectiveTo}
               onChange={(e) => setForm({ ...form, effectiveTo: e.target.value })} required />
        <input placeholder="调拨事由" value={form.reason}
               onChange={(e) => setForm({ ...form, reason: e.target.value })} required />
        <button type="submit">过账调拨</button>
      </form>

      <table>
        <thead>
          <tr><th>编号</th><th>来源账户</th><th>去向账户</th><th className="num">数量 (kg)</th><th>生效期间</th><th>事由</th></tr>
        </thead>
        <tbody>
          {transfers.map((t) => (
            <tr key={t.id}>
              <td>调拨#{t.id}</td>
              <td>{accountBrief(t.fromAccountId)}</td>
              <td>{accountBrief(t.toAccountId)}</td>
              <td className="num">{t.amount}</td>
              <td>{t.effectiveFrom} ~ {t.effectiveTo}</td>
              <td>{t.reason}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
