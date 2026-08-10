import { useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { LayoutDashboard, PackageOpen, MonitorSmartphone, QrCode, Users, LogOut, Menu, X } from "lucide-react";
import { logout, currentUser, isAdmin } from "../api";
import ThemeToggle from "./ThemeToggle";

// `admin: true` hides the entry from resellers — they have no business publishing releases or
// managing other people's accounts.
const NAV = [
  { to: "/overview", label: "Overview", icon: LayoutDashboard },
  { to: "/devices", label: "Devices", icon: MonitorSmartphone },
  { to: "/activation", label: "Activation", icon: QrCode },
  { to: "/versions", label: "App Versions", icon: PackageOpen, admin: true },
  { to: "/clients", label: "Clients", icon: Users, admin: true },
];

function Brand({ compact = false }) {
  return (
    <div className={compact ? "flex items-center gap-2.5" : "flex items-center gap-3 px-5 py-5"}>
      <img
        src="/logo.png"
        alt=""
        className={`${compact ? "h-8 w-8" : "h-10 w-10"} shrink-0 rounded-xl object-contain`}
      />
      <div className="min-w-0 leading-tight">
        <p className="truncate text-[15px] font-bold tracking-tight text-gold-ivory">10K Ultra</p>
        {!compact && <p className="truncate text-[11px] text-gold-muted">Control Panel</p>}
      </div>
    </div>
  );
}

function SidebarInner({ onNavigate, onSignOut }) {
  return (
    <>
      <Brand />
      <nav className="mt-1 flex-1 space-y-1 px-3">
        {NAV.filter((n) => !n.admin || isAdmin()).map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            onClick={onNavigate}
            className={({ isActive }) =>
              `relative flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-all ${
                isActive
                  ? "bg-gold-primary/14 text-gold-primary"
                  : "text-gold-muted hover:bg-gold-card2 hover:text-gold-ivory"
              }`
            }
          >
            {({ isActive }) => (
              <>
                {/* Gold spine marks the active section. */}
                <span
                  className={`absolute left-0 top-1/2 h-5 w-[3px] -translate-y-1/2 rounded-r-full bg-gold-primary transition-opacity ${
                    isActive ? "opacity-100" : "opacity-0"
                  }`}
                />
                <Icon size={18} />
                {label}
              </>
            )}
          </NavLink>
        ))}
      </nav>
      <div className="space-y-1 border-t border-gold-border p-3">
        {/* Whose data am I looking at? Important now that each account sees a different slice. */}
        <div className="mb-1 px-3 py-1">
          <p className="truncate text-xs font-medium text-gold-ivory">
            {currentUser().name || currentUser().email || "Signed in"}
          </p>
          <p className="truncate text-[11px] text-gold-muted">
            {isAdmin() ? "Administrator" : "Reseller"}
          </p>
        </div>
        <ThemeToggle showLabel />
        <button onClick={onSignOut} className="btn-ghost w-full justify-start">
          <LogOut size={16} /> Sign out
        </button>
      </div>
    </>
  );
}

export default function Layout() {
  const nav = useNavigate();
  const [open, setOpen] = useState(false);

  const signOut = () => {
    logout();
    nav("/login");
  };

  return (
    <div className="flex min-h-screen">
      {/* Persistent sidebar — desktop / tablet-landscape only */}
      <aside className="hidden h-screen w-64 shrink-0 flex-col border-r border-gold-border bg-gold-surface/80 backdrop-blur lg:sticky lg:top-0 lg:flex">
        <SidebarInner onSignOut={signOut} />
      </aside>

      {/* Mobile / tablet-portrait drawer */}
      {open && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div className="absolute inset-0 bg-black/70 backdrop-blur-sm" onClick={() => setOpen(false)} />
          <aside className="absolute left-0 top-0 flex h-full w-64 max-w-[80%] flex-col border-r border-gold-border bg-gold-surface shadow-glow">
            <button
              onClick={() => setOpen(false)}
              className="btn-ghost absolute right-3 top-3 h-9 w-9 p-0"
              aria-label="Close menu"
            >
              <X size={18} />
            </button>
            <SidebarInner onNavigate={() => setOpen(false)} onSignOut={signOut} />
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        {/* Mobile / tablet-portrait top bar */}
        <header className="sticky top-0 z-30 flex items-center gap-3 border-b border-gold-border bg-gold-surface/90 px-4 py-2.5 backdrop-blur lg:hidden">
          <button
            onClick={() => setOpen(true)}
            className="btn-ghost h-9 w-9 p-0"
            aria-label="Open menu"
          >
            <Menu size={18} />
          </button>
          <Brand compact />
          <ThemeToggle className="ml-auto" />
        </header>

        <main className="flex-1 overflow-y-auto">
          <div className="mx-auto max-w-6xl px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
