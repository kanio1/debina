import { CheckCircle2, Clock, ShieldCheck, XCircle, type LucideIcon } from "lucide-react";
import { Badge, type badgeVariants } from "@/components/ui/badge";
import type { VariantProps } from "class-variance-authority";

type BadgeVariant = VariantProps<typeof badgeVariants>["variant"];

// Backend statuses: PaymentStatus.java (RECEIVED/VALIDATED/REJECTED/DISPATCHED).
// Status is always text + icon, never color alone (A11Y-CHECKLIST.md "Status and color").
const STATUS_META: Record<string, { variant: BadgeVariant; icon: LucideIcon }> = {
  RECEIVED: { variant: "outline", icon: Clock },
  VALIDATED: { variant: "secondary", icon: ShieldCheck },
  DISPATCHED: { variant: "default", icon: CheckCircle2 },
  REJECTED: { variant: "destructive", icon: XCircle },
};

export function PaymentStatusBadge({ status }: { status: string | null }) {
  const displayStatus = status ?? "Business lifecycle not started";
  const fallback = { variant: "outline" as const, icon: Clock };
  const meta = status ? (STATUS_META[status] ?? fallback) : fallback;
  const Icon = meta.icon;
  return (
    <Badge variant={meta.variant}>
      <Icon aria-hidden="true" />
      {displayStatus}
    </Badge>
  );
}
