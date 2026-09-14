import React, { useEffect, useState } from 'react'
import { api } from '../api/client.js'

const TYPE_LABEL = {
  ALLOCATION: '核拨',
  TRANSFER_IN: '调入',
  TRANSFER_OUT: '调出',
  RESERVATION: '申报占用',
  RESERVATION_RELEASE: '释放占用',
  ACTUAL_DEDUCTION: '实捕核销',
  CATCH_ADJUSTMENT: '分类修订调整',
  CARRYOVER_OUT: '欠额结转转出',
  CARRYOVER_IN: '欠额结转承接'
}

/** 余额账本：从余额一路追到航次与调拨记录 */
export default function AccountsPage({ onOpenVoyage, onOpenTransfer }) {
  const [accounts, setAccounts] = useState([])
  const [ledger, setLedger] = useState(null)
  const [ledgerFor, setLedgerFor] = useState(null)
  const [error, setError] = useState(null)
  const [replayAt, setReplayAt] = useState('')
  const [replayResult, setReplayResult] = useState(null)

  const reload = () => api.accounts().then(setAccounts).catch((e) => setError(e.message))
  useEffect(() => { reload() }, [])

  const openLedger = (account) => {
    setLedgerFor(account)
    setReplayResult(null)
    setReplayAt('')
    api.ledger(account.accountId).then(setLedger).catch((e) => setError(e.message))
  }

  const replay = () => {
    if (!replayAt) return
    api.replay(ledgerFor.accountId, new Date(replayAt).toISOString())
      .then(setReplayResult)
      .catch((e) => setError(e.message))
  }

  return (
    <section>
      <h2>配额余额（按 物种 / 海区 / 季节 / 船舶 分户）</h2>
      {error && <div className="error">{error}</div>}
      <table>
        <thead>
          <tr>
            <th>船舶</th><th>物种</th><th>海区</th><th>季节</th>
            <th className="num">配额 (kg)</th><th className="num">在途占用</th>
            <th className="num">实捕核销</th><th className="num">结转净额</th>
            <th className="num">可用余额</th><th></th>
          </tr>
        </thead>
        <tbody>
          {accounts.map((a) => (
            <tr key={a.accountId} className={Number(a.available) < 0 ? 'deficit' : ''}>
              <td>{a.vesselName}</td><td>{a.speciesName}</td><td>{a.areaName}</td><td>{a.seasonName}</td>
              <td className="num">{a.quota}</td>
              <td className="num">{a.reserved}</td>
              <td className="num">{a.actual}</td>
              <td className="num">{a.carryoverNet}</td>
              <td className="num strong">{a.available}</td>
              <td><button onClick={() => openLedger(a)}>追溯明细</button></td>
            </tr>
          ))}
        </tbody>
      </table>

      {ledgerFor && (
        <div className="drawer">
          <h3>
            账本明细：{ledgerFor.vesselName} / {ledgerFor.speciesName} / {ledgerFor.areaName} / {ledgerFor.seasonName}
            <button className="close" onClick={() => { setLedgerFor(null); setLedger(null) }}>关闭</button>
          </h3>
          <div className="bar">
            <span className="hint">按当时分类回放：</span>
            <input type="datetime-local" value={replayAt}
                   onChange={(e) => setReplayAt(e.target.value)} />
            <button onClick={replay}>回放</button>
            {replayResult && (
              <span>
                截至该时：配额 {replayResult.balance.quota} / 占用 {replayResult.balance.reserved}
                {' '}/ 实捕 {replayResult.balance.actual} / 结转 {replayResult.balance.carryoverNet}
                {' '}/ 可用 <b>{replayResult.balance.available}</b> kg
                （{replayResult.entries.length} 条账目）
              </span>
            )}
          </div>
          <table>
            <thead>
              <tr><th>时间</th><th>类型</th><th className="num">金额 (kg)</th><th>关联单据</th><th>备注</th></tr>
            </thead>
            <tbody>
              {(ledger ?? []).map((e) => (
                <tr key={e.id}>
                  <td>{new Date(e.createdAt).toLocaleString()}</td>
                  <td>{TYPE_LABEL[e.type] ?? e.type}</td>
                  <td className={`num ${e.amount < 0 ? 'neg' : 'pos'}`}>{e.amount}</td>
                  <td>
                    {e.refType === 'VOYAGE' ? (
                      <button className="link" onClick={() => onOpenVoyage(e.refId)}>
                        航次 {e.refLabel}
                      </button>
                    ) : e.refType === 'TRANSFER' ? (
                      <button className="link" onClick={() => onOpenTransfer(e.refId)}>
                        {e.refLabel}
                      </button>
                    ) : e.refType === 'LANDING' ? (
                      <span>卸货 {e.refLabel}</span>
                    ) : (
                      <span>{e.refLabel}</span>
                    )}
                  </td>
                  <td>{e.note}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
