import React, { useEffect, useState } from 'react'
import { api } from '../api/client.js'

/** 待处理缺口与跨季欠额结转：缺口只记录不自动撤销航次；结转部分承接、记录上限、旧季不清零 */
export default function ShortfallsPage() {
  const [shortfalls, setShortfalls] = useState([])
  const [carryovers, setCarryovers] = useState([])
  const [accounts, setAccounts] = useState([])
  const [error, setError] = useState(null)
  const [form, setForm] = useState({ fromAccountId: '', toAccountId: '', amount: '', capAmount: '' })

  const reload = () => {
    api.shortfalls().then(setShortfalls).catch((e) => setError(e.message))
    api.carryovers().then(setCarryovers).catch((e) => setError(e.message))
    api.accounts().then(setAccounts).catch((e) => setError(e.message))
  }
  useEffect(() => { reload() }, [])

  const accountLabel = (a) =>
    `#${a.accountId} ${a.vesselName} / ${a.speciesName} / ${a.seasonName}（可用 ${a.available} kg）`

  const submit = (e) => {
    e.preventDefault()
    api.carryover(form)
      .then(() => { reload(); setForm({ ...form, amount: '', capAmount: '' }) })
      .catch((err) => setError(err.message))
  }

  return (
    <section>
      <h2>待处理缺口与欠额结转</h2>
      <p className="hint">
        分类修订等原因使账户为负时生成待处理缺口，系统不自动撤销任何合法航次；
        跨季结转仅部分承接并记录上限，上一季负余额不清零。
      </p>
      {error && <div className="error" onClick={() => setError(null)}>{error}</div>}

      <h3>待处理缺口</h3>
      <table>
        <thead>
          <tr><th>编号</th><th>账户</th><th className="num">缺口 (kg)</th><th>状态</th><th>来源</th><th>时间</th></tr>
        </thead>
        <tbody>
          {shortfalls.map((s) => (
            <tr key={s.id}>
              <td>缺口#{s.id}</td>
              <td>{s.vesselCode} / {s.speciesCode} / {s.areaCode} / {s.seasonCode}</td>
              <td className="num neg">{s.amount}</td>
              <td>{s.status === 'PENDING' ? '待处理' : '已了结'}</td>
              <td>{s.reason}{s.sourceRevisionId ? `（修订#${s.sourceRevisionId}）` : ''}</td>
              <td>{new Date(s.createdAt).toLocaleString()}</td>
            </tr>
          ))}
          {shortfalls.length === 0 && <tr><td colSpan={6}>暂无缺口</td></tr>}
        </tbody>
      </table>

      <h3>发起跨季结转</h3>
      <form onSubmit={submit} className="bar wrap">
        <select value={form.fromAccountId} onChange={(e) => setForm({ ...form, fromAccountId: e.target.value })} required>
          <option value="">旧季（欠额）账户…</option>
          {accounts.map((a) => <option key={a.accountId} value={a.accountId}>{accountLabel(a)}</option>)}
        </select>
        <select value={form.toAccountId} onChange={(e) => setForm({ ...form, toAccountId: e.target.value })} required>
          <option value="">新季（承接）账户…</option>
          {accounts.map((a) => <option key={a.accountId} value={a.accountId}>{accountLabel(a)}</option>)}
        </select>
        <input type="number" step="0.001" placeholder="结转金额 kg" value={form.amount}
               onChange={(e) => setForm({ ...form, amount: e.target.value })} required />
        <input type="number" step="0.001" placeholder="本笔承接上限 kg" value={form.capAmount}
               onChange={(e) => setForm({ ...form, capAmount: e.target.value })} required />
        <button type="submit">结转</button>
      </form>

      <h3>结转记录</h3>
      <table>
        <thead>
          <tr><th>编号</th><th>船舶 / 物种</th><th>旧季 → 新季</th><th className="num">金额 (kg)</th><th className="num">上限 (kg)</th><th>时间</th></tr>
        </thead>
        <tbody>
          {carryovers.map((c) => (
            <tr key={c.id}>
              <td>结转#{c.id}</td>
              <td>{c.vesselCode} / {c.speciesCode}</td>
              <td>{c.fromSeasonCode} → {c.toSeasonCode}</td>
              <td className="num">{c.amount}</td>
              <td className="num">{c.capAmount}</td>
              <td>{new Date(c.createdAt).toLocaleString()}</td>
            </tr>
          ))}
          {carryovers.length === 0 && <tr><td colSpan={6}>暂无结转记录</td></tr>}
        </tbody>
      </table>
    </section>
  )
}
