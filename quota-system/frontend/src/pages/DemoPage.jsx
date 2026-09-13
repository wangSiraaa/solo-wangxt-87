import React, { useState } from 'react'
import { api } from '../api/client.js'

/** 一键运行样例验证场景并展示逐步日志 */
export default function DemoPage() {
  const [log, setLog] = useState(null)
  const [running, setRunning] = useState(false)
  const [error, setError] = useState(null)

  const run = () => {
    setRunning(true)
    setError(null)
    api.runDemo()
      .then((res) => setLog(res.log))
      .catch((e) => setError(e.message))
      .finally(() => setRunning(false))
  }

  return (
    <section>
      <h2>样例验证</h2>
      <p className="hint">
        覆盖：预计大于实捕的差额释放、同船多港卸货、两航次争用额度、同一卸货凭证重复回传幂等、
        删除草稿不丢捕捞事实、跨维度调拨拒绝、同维度调拨留痕。
      </p>
      <button onClick={run} disabled={running}>{running ? '运行中…' : '运行全部场景'}</button>
      {error && <div className="error">{error}</div>}
      {log && (
        <ol className="log">
          {log.map((line, i) => <li key={i}>{line}</li>)}
        </ol>
      )}
    </section>
  )
}
