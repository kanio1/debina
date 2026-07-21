/** Internal type. DO NOT USE DIRECTLY. */
type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] };
/** Internal type. DO NOT USE DIRECTLY. */
export type Incremental<T> = T | { [P in keyof T]?: P extends ' $fragmentName' | '__typename' ? T[P] : never };
export type AuditQueryFilter = {
  actorId: string | null | undefined;
  batchId: string | null | undefined;
  branchId: string | null | undefined;
  commandType: string | null | undefined;
  correlationId: string | null | undefined;
  occurredFrom: string | null | undefined;
  occurredTo: string | null | undefined;
  outcome: string | null | undefined;
  paymentId: string | null | undefined;
  targetId: string | null | undefined;
  targetType: string | null | undefined;
  tenantId: string | null | undefined;
};

export type ApprovalQueueQueryVariables = Exact<{
  first: number;
  after: string | null | undefined;
}>;


export type ApprovalQueueQuery = { approvalQueue: { nextCursor: string | null, items: Array<{ approvalId: string, paymentId: string, approvalStatus: string, makerUserId: string, submittedAt: string, expiresAt: string, expiredButUnprocessed: boolean, matrixRuleId: string, amount: string, currency: string, debtorIban: string, creditorIban: string }> } };

export type ApprovalQueryVariables = Exact<{
  paymentId: string;
}>;


export type ApprovalQuery = { approval: { approvalId: string, paymentId: string, approvalStatus: string, makerUserId: string, submittedAt: string, expiresAt: string, expiredButUnprocessed: boolean, matrixRuleId: string, amount: string, currency: string, debtorIban: string, creditorIban: string, decisionComment: string | null, decidedAt: string | null } | null };

export type PaymentAuditTrailQueryVariables = Exact<{
  paymentId: string;
  first: number;
  after: string | null | undefined;
}>;


export type PaymentAuditTrailQuery = { paymentAuditTrail: { nextCursor: string | null, items: Array<{ auditEntryId: string, tenantId: string, branchId: string | null, occurredAt: string, actorType: string, actorId: string, authorizedRole: string, correlationId: string, commandType: string, targetType: string, targetId: string, paymentId: string | null, batchId: string | null, outcome: string, decisionComment: string | null, beforeState: { approvalId: string | null, approvalStatus: string | null }, afterState: { approvalId: string | null, approvalStatus: string | null } }> } };

export type AuditEntriesQueryVariables = Exact<{
  auditFilter: AuditQueryFilter;
  first: number;
  after: string | null | undefined;
}>;


export type AuditEntriesQuery = { auditEntries: { nextCursor: string | null, items: Array<{ auditEntryId: string, tenantId: string, branchId: string | null, occurredAt: string, actorType: string, actorId: string, authorizedRole: string, correlationId: string, commandType: string, targetType: string, targetId: string, paymentId: string | null, batchId: string | null, outcome: string, decisionComment: string | null, beforeState: { approvalId: string | null, approvalStatus: string | null }, afterState: { approvalId: string | null, approvalStatus: string | null } }> } };
