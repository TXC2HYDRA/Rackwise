export type Role = 'REQUESTER' | 'REVIEWER' | 'ADMIN'
export type Priority = 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL'
export type RequestStatus = 'SUBMITTED' | 'PLANNED' | 'APPROVED' | 'REJECTED' | 'FULFILLED'
export type PlanStatus = 'PROPOSED' | 'APPROVED' | 'REJECTED' | 'APPLIED' | 'FAILED' | 'STALE'
export type Feasibility = 'FEASIBLE' | 'PARTIAL' | 'INFEASIBLE'
export type ServerStatus = 'ACTIVE' | 'MAINTENANCE' | 'DECOMMISSIONED'

export interface User { id: string; username: string; displayName: string; roles: Role[] }
export interface LoginResponse { accessToken: string; tokenType: string; expiresIn: number; user: User }
export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }

export interface DataCenter { id: string; code: string; name: string }
export interface RackGroupCapacity {
  id: string; dataCenterId: string; dataCenterCode: string; code: string; name: string
  slotCapacity: number; slotsUsed: number; slotsAvailable: number
  powerCapacityWatts: number; powerUsedWatts: number; powerAvailableWatts: number; version: number
}
export interface CapacitySummary {
  totalSlots: number; usedSlots: number; availableSlots: number
  totalPowerWatts: number; usedPowerWatts: number; availablePowerWatts: number
  rackGroups: RackGroupCapacity[]
}
export interface ServerView {
  id: string; hostname: string; rackGroupId: string; rackGroupCode: string; dataCenterCode: string
  cpuCores: number; ramGb: number; storageTb: number; powerWatts: number; status: ServerStatus
}

export interface CapacityRequest {
  id: string; requesterId: string; requesterName: string; dataCenterId: string; preferredRackGroupId: string | null
  allowOtherRackGroups: boolean; serverCount: number; cpuCores: number; ramGb: number; storageTb: number
  powerWatts: number; justification: string; priority: Priority; status: RequestStatus; createdAt: string
}
export interface GroupEvaluation {
  rackGroupId: string; rackGroupCode: string; freeSlots: number; freeWatts: number
  maxBySlots: number; maxByPower: number; fit: number; allocated: number
  binding: 'NONE' | 'SLOTS' | 'POWER' | 'NOT_CONSIDERED'
}
export interface Plan {
  id: string; requestId: string; planNumber: number; status: PlanStatus; feasibility: Feasibility
  requestedServers: number; allocatedServers: number; summary: string
  allocations: { rackGroupId: string; rackGroupCode: string; dataCenterCode: string; serverCount: number; rackGroupVersion: number }[]
  evaluation: GroupEvaluation[]; contentHash: string; createdBy: string; createdAt: string
  appliedAt: string | null; failureReason: string | null
}
export interface QueueItem { plan: Plan; request: CapacityRequest }
export interface HistoryItem {
  id: string; planId: string; requestId: string; planNumber: number; decision: 'APPROVED' | 'REJECTED'
  reviewerName: string; comment: string | null; decidedAt: string; planStatus: PlanStatus
  allocatedServers: number; requestedServers: number; requesterName: string
}
export interface AuditEvent {
  id: string; occurredAt: string; actorId: string | null; actorName: string; action: string; targetType: string
  targetId: string; result: 'SUCCESS' | 'FAILURE' | 'DENIED'; correlationId: string | null
  before: string | null; after: string | null; detail: string | null
}
