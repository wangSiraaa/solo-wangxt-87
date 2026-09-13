import React, { useEffect, useState } from 'react'
import { api } from '../api/client.js'

const STATUS_LABEL = { DRAFT: '草稿', DECLARED: '已申报', CLOSED: '已结案', CANCELLED: '已取消' }

/** 航次与卸货：申报占用 → 多港卸货 → 核实转实扣/释放差额 → 结案 */
export default function VoyagesPage({ meta, focusVoyageId }) {
  const [voyages, setVoyages] = useState([])
  const [selected, setSelected] = useState(null)
  const [landings, setLandings] = useState([])
  const [error, setError] = useState(null)
  const [form, setForm] = useState({ voyageNo: '', vesselCode: '', speciesCode: '', areaCode: '', seasonCode: '', estimatedWeight: '' })
  const [landingForm, setLandingForm] = useState({ portName: '', receiptNo: '', estimatedWeight: '' })
  const [verifyWeights, setVerifyWeights] = useState({})

  const run = (p) => p.catch((e) => setError(e.message))
  const reload = () => run(api.voyages().then(setVoyages))

  useEffect(() => { reload() }, [])

  useEffect(() => {
    if (focusVoyageId) select({ id: focusVoyageId })
  }, [focusVoyageId])

  const select = (v) => {
    setSelected(v)
    run(api.landings(v.id).then(setLandings))
  }

  const create = (e) => {
    e.preventDefault()
    run(api.createVoyage(form).then(() => { setForm({ ...form, voyageNo: '', estimatedWeight: '' }); reload() }))
  }

  const action = (p) => run(p.then(() => { reload(); if (selected) select(selected) }))

  const input = (key, obj, setter, placeholder, type = 'text') => (
    <input
      type={type}
      placeholder={placeholder}
      value={obj[key]}
      onChange={(e) => setter({ ...obj, [key]: e.target.value })}
      required
    />
  )

  const selectField = (key, options, labelKey = 'name') => (
    <select value={form[key]} onChange={(e) => setForm({ ...form, [key]: e.target.value })} required>
      <option value="">选择…</option>
      {(options ?? []).map((o) => (
        <option key={o.code} value={o.code}>{o[labelKey]}</option>
      ))}
    </select>
  )

  return (
    <section>
      <h2>航次与卸货</h2>
      {error && <div className="error" onClick={() => setError(null)}>{error}</div>}

      <form onSubmit={create} className="bar">
        {input('voyageNo', form, setForm, '航次号')}
        {selectField('vesselCode', meta?.vessels)}
        {selectField('speciesCode', meta?.species)}
        {selectField('areaCode', meta?.areas)}
        {selectField('seasonCode', meta?.seasons)}
        {input('estimatedWeight', form, setForm, '预计重量 kg', 'number')}
        <button type="submit">建草稿</button>
      </form>

      <table>
        <thead>
          <tr><th>航次号</th><th>状态</th><th className="num">预计重量</th><th>操作</th></tr>
        </thead>
        <tbody>
          {voyages.map((v) => (
            <tr key={v.id} className={selected?.id === v.id ? 'selected' : ''}>
              <td><button className="link" onClick={() => select(v)}>{v.voyageNo}</button></td>
              <td>{STATUS_LABEL[v.status]}</td>
              <td className="num">{v.estimatedWeight}</td>
              <td className="actions">
                {v.status === 'DRAFT' && <>
                  <button onClick={() => action(api.declareVoyage(v.id))}>申报（占用额度）</button>
                  <button onClick={() => action(api.deleteVoyage(v.id))}>删除草稿</button>
                </>}
                {v.status === 'DECLARED' && <>
                  <button onClick={() => action(api.closeVoyage(v.id))}>结案</button>
                  <button onClick={() => action(api.cancelVoyage(v.id))}>取消</button>
                </>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {selected && (
        <div className="drawer">
          <h3>航次 {selected.voyageNo} 的卸货单（支持同船多港）</h3>
          {selected.status === 'DECLARED' && (
            <form
              className="bar"
              onSubmit={(e) => {
                e.preventDefault()
                action(api.addLanding(selected.id, landingForm))
                setLandingForm({ portName: '', receiptNo: '', estimatedWeight: '' })
              }}
            >
              {input('portName', landingForm, setLandingForm, '港口')}
              {input('receiptNo', landingForm, setLandingForm, '卸货凭证号（幂等键）')}
              {input('estimatedWeight', landingForm, setLandingForm, '本票预计 kg', 'number')}
              <button type="submit">登记卸货（待确认）</button>
            </form>
          )}
          <table>
            <thead>
              <tr><th>凭证号</th><th>港口</th><th>状态</th><th className="num">预计</th><th className="num">核实</th><th>核实操作</th></tr>
            </thead>
            <tbody>
              {landings.map((l) => (
                <tr key={l.id}>
                  <td>{l.receiptNo}</td>
                  <td>{l.portName}</td>
                  <td>{l.status === 'PENDING' ? '待确认（不计入实捕）' : '已核实'}</td>
                  <td className="num">{l.estimatedWeight}</td>
                  <td className="num">{l.verifiedWeight ?? '—'}</td>
                  <td>
                    {l.status === 'PENDING' && (
                      <span className="actions">
                        <input
                          type="number"
                          placeholder="核实重量"
                          value={verifyWeights[l.id] ?? ''}
                          onChange={(e) => setVerifyWeights({ ...verifyWeights, [l.id]: e.target.value })}
                        />
                        <button onClick={() => action(api.verifyLanding(l.id, verifyWeights[l.id]))}>
                          核实转实扣
                        </button>
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
