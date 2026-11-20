import type { ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { AdminInventoryPage } from '../pages/AdminInventoryPage'
import { ApprovalHistoryPage } from '../pages/ApprovalHistoryPage'
import { ApprovalQueuePage } from '../pages/ApprovalQueuePage'
import { AuditPage } from '../pages/AuditPage'
import { DashboardPage } from '../pages/DashboardPage'
import { InventoryPage } from '../pages/InventoryPage'
import { LoginPage } from '../pages/LoginPage'
import { MyRequestsPage } from '../pages/MyRequestsPage'
import { NewRequestPage } from '../pages/NewRequestPage'
import { PlanDetailPage } from '../pages/PlanDetailPage'
import { RequestDetailPage } from '../pages/RequestDetailPage'
import type { Role } from '../lib/types'
import { useAuth } from './auth'

function Protected({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { user, has } = useAuth()
  const location = useLocation()
  if (!user) return <Navigate to="/login" state={{ from: location.pathname }} replace />
  if (roles && !has(...roles)) {
    return (
      <div role="alert" className="mx-auto max-w-md py-20 text-center">
        <h1 className="text-xl font-semibold">Access denied</h1>
        <p className="mt-2 text-sm text-slate-500">Your role does not include access to this page.</p>
      </div>
    )
  }
  return <>{children}</>
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<Protected><AppLayout /></Protected>}>
        <Route index element={<DashboardPage />} />
        <Route path="inventory" element={<InventoryPage />} />
        <Route path="requests" element={<MyRequestsPage />} />
        <Route path="requests/new" element={<Protected roles={['REQUESTER', 'ADMIN']}><NewRequestPage /></Protected>} />
        <Route path="requests/:id" element={<RequestDetailPage />} />
        <Route path="plans/:id" element={<PlanDetailPage />} />
        <Route path="approvals" element={<Protected roles={['REVIEWER', 'ADMIN']}><ApprovalQueuePage /></Protected>} />
        <Route path="approvals/history" element={<Protected roles={['REVIEWER', 'ADMIN']}><ApprovalHistoryPage /></Protected>} />
        <Route path="audit" element={<Protected roles={['REVIEWER', 'ADMIN']}><AuditPage /></Protected>} />
        <Route path="admin/inventory" element={<Protected roles={['ADMIN']}><AdminInventoryPage /></Protected>} />
        <Route path="*" element={<div className="py-20 text-center"><h1 className="text-xl font-semibold">Page not found</h1></div>} />
      </Route>
    </Routes>
  )
}
