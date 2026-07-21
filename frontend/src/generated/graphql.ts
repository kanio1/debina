/** Internal type. DO NOT USE DIRECTLY. */
type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] };
/** Internal type. DO NOT USE DIRECTLY. */
export type Incremental<T> = T | { [P in keyof T]?: P extends ' $fragmentName' | '__typename' ? T[P] : never };
export type Maybe<T> = T | null;
export type InputMaybe<T> = Maybe<T>;
/** All built-in and custom scalars, mapped to their actual values */
export type Scalars = {
  ID: { input: string; output: string; }
  String: { input: string; output: string; }
  Boolean: { input: boolean; output: boolean; }
  Int: { input: number; output: number; }
  Float: { input: number; output: number; }
};

export type Approval = {
  __typename?: 'Approval';
  amount: Scalars['String']['output'];
  approvalId: Scalars['ID']['output'];
  approvalStatus: Scalars['String']['output'];
  creditorIban: Scalars['String']['output'];
  currency: Scalars['String']['output'];
  debtorIban: Scalars['String']['output'];
  decidedAt: Maybe<Scalars['String']['output']>;
  decisionComment: Maybe<Scalars['String']['output']>;
  expiredButUnprocessed: Scalars['Boolean']['output'];
  expiresAt: Scalars['String']['output'];
  makerUserId: Scalars['String']['output'];
  matrixRuleId: Scalars['ID']['output'];
  paymentId: Scalars['ID']['output'];
  submittedAt: Scalars['String']['output'];
};

export type ApprovalConnection = {
  __typename?: 'ApprovalConnection';
  items: Array<Approval>;
  nextCursor: Maybe<Scalars['String']['output']>;
};

export type Query = {
  __typename?: 'Query';
  approval: Maybe<Approval>;
  approvalQueue: ApprovalConnection;
};


export type QueryApprovalArgs = {
  paymentId: Scalars['ID']['input'];
};


export type QueryApprovalQueueArgs = {
  after: InputMaybe<Scalars['String']['input']>;
  first: Scalars['Int']['input'];
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
