import { useState, useCallback } from 'react'
import { authApi } from '../api/auth'

export interface AuthState {
  token: string | null
  userId: string | null
  email: string | null
}

export function useAuth() {
  const [auth, setAuth] = useState<AuthState>(() => ({
    token: localStorage.getItem('token'),
    userId: localStorage.getItem('userId'),
    email: localStorage.getItem('email'),
  }))

  const login = useCallback(async (email: string, password: string) => {
    const res = await authApi.login(email, password)
    localStorage.setItem('token', res.token)
    localStorage.setItem('userId', res.userId)
    localStorage.setItem('email', res.email)
    setAuth({ token: res.token, userId: res.userId, email: res.email })
    return res
  }, [])

  const register = useCallback(async (email: string, password: string) => {
    const res = await authApi.register(email, password)
    localStorage.setItem('token', res.token)
    localStorage.setItem('userId', res.userId)
    localStorage.setItem('email', res.email)
    setAuth({ token: res.token, userId: res.userId, email: res.email })
    return res
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('email')
    setAuth({ token: null, userId: null, email: null })
  }, [])

  return { auth, login, register, logout }
}
