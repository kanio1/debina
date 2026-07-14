// Role -> Workspace map (EPIC-23 Story 23.3), sourced verbatim from
// sepa-nexus-react-nextjs-frontend-blueprint.md §9 "Role-to-Workspace Matrix" `[FREEZE]`.
// This file only encodes which of the 7 workspaces each of the 11 Keycloak roles may
// open — it does not invent a role or a workspace. `path` is left `undefined` for
// workspaces that don't have a route yet; the nav only ever renders workspaces that
// are both role-visible and actually built (EPIC-24 builds the rest one at a time).

export interface Workspace {
  id: string;
  label: string;
  /** §9 numbering, kept so this table stays traceable back to the blueprint. */
  number: number;
  /** Roles allowed to open this workspace, per §9 (read-only access is still "access" for nav purposes). */
  roles: string[];
  /** Route path, once the workspace is actually built. `undefined` = not built yet. */
  path?: string;
}

export const WORKSPACES: Workspace[] = [
  {
    id: "control-room",
    label: "Ops Control Room",
    number: 1,
    roles: [
      "operator",
      "settlement_operator",
      "egress_operator",
      "reconciliation_operator",
      "case_operator",
      "auditor",
    ],
  },
  {
    id: "payments",
    label: "Payments & Files",
    number: 2,
    roles: ["operator", "payment_viewer", "payment_submitter", "payment_approver", "auditor"],
    path: "/payments",
  },
  {
    id: "settlement",
    label: "Settlement & Liquidity",
    number: 3,
    roles: ["operator", "settlement_operator", "auditor"],
  },
  {
    id: "egress",
    label: "Egress & Delivery",
    number: 4,
    roles: ["operator", "egress_operator", "auditor"],
  },
  {
    id: "reconciliation",
    label: "Reconciliation & Cases",
    number: 5,
    roles: ["operator", "reconciliation_operator", "case_operator", "auditor"],
  },
  {
    id: "simulation",
    label: "Simulation Lab",
    number: 6,
    roles: ["operator", "simulation_operator", "auditor"],
  },
  {
    id: "reference-data",
    label: "Reference Data / Admin",
    number: 7,
    roles: ["reference_data_admin", "security_admin", "auditor"],
  },
];

/** Workspaces a given set of realm roles may see in nav, restricted to ones with a real route today. */
export function visibleWorkspacesForRoles(roles: string[]): Workspace[] {
  const roleSet = new Set(roles);
  return WORKSPACES.filter((workspace) => workspace.path && workspace.roles.some((role) => roleSet.has(role)));
}
