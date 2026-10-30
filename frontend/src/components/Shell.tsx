import { NavLink, Link, Outlet } from "react-router-dom";
import {
  House,
  Gamepad2,
  Trophy,
  ChartNoAxesCombined,
  UserRound,
  Zap,
  ArrowUpRight,
  LogOut,
  Command,
} from "lucide-react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../services/api";
import { useSession } from "../stores/session";
import { supabase } from "../lib/supabase";
import { Avatar } from "./common";
import { Button } from "./ui/button";
export default function Shell() {
  const session = useSession((s) => s.session);
  const cache = useQueryClient();
  const { data: me } = useQuery({
    queryKey: ["me"],
    queryFn: () => api("/me"),
    enabled: !!session,
  });
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <Link to="/" className="brand">
          <span className="brand-mark">
            <Command size={23} />
          </span>
          PLAYBIT<span className="brand-dot">•</span>
        </Link>
        <div className="nav-label">YOUR PLAYGROUND</div>
        <nav>
          {[
            [House, "Home", "/"],
            [Gamepad2, "Play", "/play"],
            [Trophy, "Leaderboards", "/leaderboards"],
            [ChartNoAxesCombined, "Progress", "/progress"],
            [UserRound, "Profile", "/profile"],
          ].map(([Icon, label, to]) => {
            const I = Icon as typeof House;
            return (
              <NavLink
                key={String(to)}
                to={String(to)}
                end={to === "/"}
                className={({ isActive }) =>
                  `nav-item ${isActive ? "active" : ""}`
                }
              >
                <I size={20} />
                {String(label)}
                {to === "/play" && <span className="nav-new">GO</span>}
              </NavLink>
            );
          })}
        </nav>
        <div className="sidebar-bottom">
          <div className="practice-note">
            <span className="tiny-icon">
              <Zap size={18} />
            </span>
            <strong>
              A little better.
              <br />
              Every single day.
            </strong>
            <p>Your next breakthrough starts with one game.</p>
            <Link to="/play">
              Let’s play <ArrowUpRight size={15} />
            </Link>
          </div>
          <div className="sidebar-profile">
            <Avatar seed={me?.avatar_url} />
            <div>
              <strong>{me?.display_name || "Your next chapter"}</strong>
              <small>
                {me
                  ? `Level ${me.level} · ${me.total_xp} XP`
                  : "Starts with a little curiosity"}
              </small>
            </div>
            {session && (
              <button
                aria-label="Sign out"
                className="icon-button"
                onClick={async () => {
                  await supabase?.auth.signOut();
                  cache.clear();
                }}
              >
                <LogOut size={17} />
              </button>
            )}
          </div>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <div className="topbar-label">
            <span className="status-dot" />
            THE LEARNING ARENA <span className="topbar-divider">/</span>
            <span>Make your next move.</span>
          </div>
          <div className="topbar-actions">
            <span className="season">
              <Sparkle /> BUILT FOR CURIOUS MINDS
            </span>
            {me ? (
              <Link className="xp-pill" to="/profile">
                <Zap size={15} />
                {me.total_xp} XP
                <Avatar seed={me.avatar_url} size="sm" />
              </Link>
            ) : (
              <Button asChild variant="outline" size="sm">
                <Link to="/auth">
                  Sign in <ArrowUpRight size={14} />
                </Link>
              </Button>
            )}
          </div>
        </header>
        <main>
          <Outlet />
        </main>
        <footer>
          <span>
            PLAYBIT <span className="muted">/</span> Play smart. Compete hard.
            Level up.
          </span>
          <span>
            Made for the next version of you <Zap size={12} />
          </span>
        </footer>
      </div>
    </div>
  );
}
function Sparkle() {
  return <span className="text-lime">✳</span>;
}
