import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { LoginPage } from '../pages/LoginPage'
import { fail, makeUser, ok, renderApp } from './helpers'

describe('LoginPage', () => {
  it('validates empty fields without calling the API', async () => {
    const { api } = renderApp(<LoginPage />, { user: null })
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('Enter your username')).toBeInTheDocument()
    expect(screen.getByText('Enter your password')).toBeInTheDocument()
    expect(api.count('POST /auth/login')).toBe(0)
  })

  it('shows a clear message for invalid credentials and keeps the user on the page', async () => {
    renderApp(<LoginPage />, { user: null, routes: { 'POST /auth/login': fail(401, 'UNAUTHENTICATED', 'nope') } })
    await userEvent.type(screen.getByLabelText('Username'), 'alice')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password.')
    expect(screen.getByLabelText('Username')).toBeInTheDocument()
  })

  it('signs in and leaves the login page on success', async () => {
    const user = makeUser(['REQUESTER'], 'u-9', 'alice')
    const { api } = renderApp(<LoginPage />, {
      user: null, route: '/login', path: '/login', routes: { 'POST /auth/login': ok({ accessToken: 't', tokenType: 'Bearer', expiresIn: 900, user }) },
    })
    await userEvent.type(screen.getByLabelText('Username'), 'alice')
    await userEvent.type(screen.getByLabelText('Password'), 'secret')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('home page')).toBeInTheDocument()
    expect(screen.queryByLabelText('Username')).not.toBeInTheDocument()
    expect(api.calls[0].body).toEqual({ username: 'alice', password: 'secret' })
  })

  it('shows a network error message when the server is unreachable', async () => {
    const { api } = renderApp(<LoginPage />, { user: null })
    api.fn.mockRejectedValueOnce(new TypeError('offline'))
    await userEvent.type(screen.getByLabelText('Username'), 'alice')
    await userEvent.type(screen.getByLabelText('Password'), 'x')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/cannot reach the server/i)
  })
})
