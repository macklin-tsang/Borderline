import { useState, FormEvent } from 'react'

interface Props {
  onLogin: (email: string, password: string) => Promise<unknown>
  onRegister: (email: string, password: string) => Promise<unknown>
}

const s: Record<string, React.CSSProperties> = {
  container: {
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    height: '100%', background: '#0f172a',
  },
  card: {
    background: '#1e293b', borderRadius: 12, padding: '2rem',
    width: 360, boxShadow: '0 25px 50px rgba(0,0,0,0.5)',
  },
  title: { fontSize: '1.5rem', fontWeight: 700, marginBottom: '1.5rem', color: '#f1f5f9' },
  label: { display: 'block', marginBottom: '0.25rem', fontSize: '0.85rem', color: '#94a3b8' },
  input: {
    width: '100%', padding: '0.6rem 0.75rem', borderRadius: 8, border: '1px solid #334155',
    background: '#0f172a', color: '#e2e8f0', fontSize: '0.95rem', marginBottom: '1rem',
    outline: 'none',
  },
  btnPrimary: {
    width: '100%', padding: '0.65rem', borderRadius: 8, border: 'none',
    background: '#3b82f6', color: '#fff', fontWeight: 600, fontSize: '0.95rem',
    cursor: 'pointer', marginBottom: '0.75rem',
  },
  btnSecondary: {
    width: '100%', padding: '0.65rem', borderRadius: 8, border: '1px solid #334155',
    background: 'transparent', color: '#94a3b8', fontSize: '0.9rem',
    cursor: 'pointer',
  },
  error: { color: '#f87171', fontSize: '0.85rem', marginBottom: '1rem' },
}

export function AuthForm({ onLogin, onRegister }: Props) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const submit = async (fn: (e: string, p: string) => Promise<unknown>) => {
    setError('')
    setLoading(true)
    try {
      await fn(email, password)
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
    } finally {
      setLoading(false)
    }
  }

  const handleLogin = (ev: FormEvent) => { ev.preventDefault(); submit(onLogin) }

  return (
    <div style={s.container}>
      <form style={s.card} onSubmit={handleLogin}>
        <div style={s.title}>Borderline</div>
        {error && <div style={s.error}>{error}</div>}
        <label style={s.label}>Email</label>
        <input
          style={s.input} type="email" required autoFocus
          value={email} onChange={e => setEmail(e.target.value)}
        />
        <label style={s.label}>Password</label>
        <input
          style={s.input} type="password" required
          value={password} onChange={e => setPassword(e.target.value)}
        />
        <button style={s.btnPrimary} type="submit" disabled={loading}>
          {loading ? 'Loading…' : 'Sign in'}
        </button>
        <button
          style={s.btnSecondary} type="button" disabled={loading}
          onClick={() => submit(onRegister)}
        >
          Create account
        </button>
      </form>
    </div>
  )
}
