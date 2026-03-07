import { Toaster } from 'react-hot-toast'
import { useAuth } from './hooks/useAuth'
import { AuthForm } from './components/AuthForm'
import { MapView } from './components/MapView'

export function App() {
  const { auth, login, register, logout } = useAuth()

  if (!auth.token) {
    return (
      <>
        <AuthForm onLogin={login} onRegister={register} />
        <Toaster position="top-right" />
      </>
    )
  }

  return (
    <>
      <MapView token={auth.token} onLogout={logout} />
      <Toaster position="top-right" />
    </>
  )
}
