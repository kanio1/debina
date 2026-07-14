// Shared `LoadingState`/`EmptyState`/`ErrorState`/`UnauthorizedState` family
// (sepa-nexus-first-3-screens-ui-spec.md §18 component list; component-foundation
// blueprint §6 "State handling" finding: one reusable component family across all
// screens, not 40+ hand-written per-screen variants). `empty` and `unauthorized` are
// deliberately distinct kinds so a screen can never render one where the other belongs
// (component-foundation §6 "Info-leak risk" finding, `[MUST-FIX]`).

export type ScreenStateKind = "loading" | "error" | "empty" | "unauthorized" | "unauthenticated";

const DEFAULT_MESSAGE: Record<ScreenStateKind, string> = {
  loading: "Loading…",
  error: "Something went wrong.",
  empty: "Nothing here yet.",
  unauthorized: "You do not have access to this view.",
  unauthenticated: "Sign in to continue.",
};

interface ScreenStateContentProps {
  kind: ScreenStateKind;
  /** `data-testid` prefix for this screen/component, e.g. "payments.list" (see CONVENTIONS.md). */
  testIdPrefix: string;
  message?: string;
}

/** The bare message node — reusable inside a `<TableCell>`, a `<div>`, or any other wrapper. */
export function ScreenStateContent({ kind, testIdPrefix, message }: ScreenStateContentProps) {
  return <span data-testid={`${testIdPrefix}.${kind}`}>{message ?? DEFAULT_MESSAGE[kind]}</span>;
}

/** Block-level wrapper for non-table screens (e.g. a page body, not inside a `<table>`). */
export function ScreenState({ kind, testIdPrefix, message }: ScreenStateContentProps) {
  return (
    <div
      role={kind === "error" ? "alert" : "status"}
      className="flex min-h-24 items-center justify-center p-6 text-sm text-muted-foreground"
    >
      <ScreenStateContent kind={kind} testIdPrefix={testIdPrefix} message={message} />
    </div>
  );
}
