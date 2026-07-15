"use client";

import { usePathname } from "next/navigation";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarTrigger,
} from "@/components/ui/sidebar";
import { visibleWorkspacesForRoles } from "@/lib/role-workspace-map";

export interface AppShellUser {
  preferredUsername: string | null;
  tenantId: string | null;
  roles: string[];
}

interface AppShellProps {
  user: AppShellUser;
  children: React.ReactNode;
}

export function AppShell({ user, children }: AppShellProps) {
  const workspaces = visibleWorkspacesForRoles(user.roles);
  const pathname = usePathname();

  return (
    <SidebarProvider>
      <Sidebar data-testid="app-shell.sidebar">
        <SidebarHeader>
          <span className="px-2 text-sm font-semibold">SEPA Nexus</span>
        </SidebarHeader>
        <SidebarContent>
          <SidebarGroup>
            <SidebarGroupLabel>Workspace</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>
                {workspaces.map((workspace) => {
                  const isActive = workspace.path != null && pathname.startsWith(workspace.path);
                  return (
                    <SidebarMenuItem key={workspace.id}>
                      <SidebarMenuButton
                        isActive={isActive}
                        render={
                          <a
                            href={workspace.path}
                            aria-current={isActive ? "page" : undefined}
                            data-testid={`app-shell.nav.${workspace.id}`}
                          />
                        }
                      >
                        {workspace.label}
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  );
                })}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        </SidebarContent>
        <SidebarFooter>
          <span
            className="px-2 text-xs text-muted-foreground"
            data-testid="app-shell.current-user"
          >
            {user.preferredUsername ?? "unknown user"}
          </span>
        </SidebarFooter>
      </Sidebar>
      <SidebarInset>
        <header className="flex h-14 items-center gap-2 border-b px-4">
          <SidebarTrigger data-testid="app-shell.sidebar-trigger" />
          <div className="flex-1" />
          <a
            href="/api/auth/logout"
            className="text-sm text-muted-foreground hover:text-foreground"
            data-testid="app-shell.logout-link"
          >
            Log out
          </a>
        </header>
        {/* SidebarInset already renders <main> — a second one here would be a duplicate landmark. */}
        <div className="flex-1 p-4">{children}</div>
      </SidebarInset>
    </SidebarProvider>
  );
}
