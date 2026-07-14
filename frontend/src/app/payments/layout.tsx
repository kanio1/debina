import { redirect } from "next/navigation";
import { AppShell } from "@/components/app-shell/app-shell";
import { getCurrentSession } from "@/lib/current-session";

export default async function PaymentsLayout({ children }: { children: React.ReactNode }) {
  const session = await getCurrentSession();
  if (!session) {
    redirect("/api/auth/login");
  }

  return (
    <AppShell
      user={{
        preferredUsername: session.claims.preferredUsername,
        tenantId: session.claims.tenantId,
      }}
    >
      {children}
    </AppShell>
  );
}
