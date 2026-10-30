import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import {
  AlertCircle,
  ArrowUpRight,
  LoaderCircle,
  Orbit,
  Sparkles,
} from "lucide-react";
import { Button } from "./ui/button";
export function Avatar({
  seed = 0,
  size = "md",
}: {
  seed?: number | string;
  size?: string;
}) {
  const n = Number(seed) || 0;
  const colors = [
    "#aa8bfa",
    "#71dfde",
    "#ceef8a",
    "#f4b089",
    "#ee93be",
    "#8aaafa",
    "#ecd679",
    "#bdaaf0",
  ];
  return (
    <span
      className={`avatar avatar-${size}`}
      style={{ background: colors[n % 8] }}
    >
      <svg viewBox="0 0 48 48" aria-hidden="true">
        <path
          d={
            n % 2
              ? "M10 38V18L18 9l6 8 6-8 8 9v20z"
              : "M8 34V19l8-8h16l8 8v15l-8 6H16z"
          }
          fill="#222038"
        />
        <rect x="14" y="20" width="7" height="9" rx="3" fill={colors[n % 8]} />
        <rect x="27" y="20" width="7" height="9" rx="3" fill={colors[n % 8]} />
        <path
          d="M20 33h8"
          stroke={colors[n % 8]}
          strokeWidth="3"
          strokeLinecap="round"
        />
      </svg>
    </span>
  );
}
export function Loading() {
  return (
    <div className="empty">
      <LoaderCircle className="spin" />
      <p>Loading your next move…</p>
    </div>
  );
}
export function ErrorBox({ error }: { error: unknown }) {
  return error ? (
    <div className="error" role="alert">
      <AlertCircle size={18} />
      {error instanceof Error ? error.message : String(error)}
    </div>
  ) : null;
}
export function Empty({ children }: { children: ReactNode }) {
  return (
    <div className="empty">
      <Orbit size={36} />
      <p>{children}</p>
    </div>
  );
}
export function SectionTitle({
  eyebrow,
  title,
  children,
}: {
  eyebrow?: string;
  title: string;
  children?: ReactNode;
}) {
  return (
    <div className="section-title">
      <div>
        {eyebrow && <div className="eyebrow">{eyebrow}</div>}
        <h2>{title}</h2>
      </div>
      {children}
    </div>
  );
}
export function Stat({
  label,
  value,
  icon,
}: {
  label: string;
  value: ReactNode;
  icon?: ReactNode;
}) {
  return (
    <div className="stat">
      {icon}
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
export function SetupNotice() {
  return (
    <div className="setup-notice">
      <Sparkles size={20} />
      <div>
        <strong>Your arena is ready for connection.</strong>
        <p>
          Add your Supabase URL and anon key to <code>frontend/.env.local</code>
          , configure the backend database, then restart the servers. Your
          progress will be saved to your account.
        </p>
      </div>
    </div>
  );
}
export function ActionLink({
  to,
  children,
}: {
  to: string;
  children: ReactNode;
}) {
  return (
    <Button asChild variant="ghost">
      <Link to={to}>
        {children}
        <ArrowUpRight size={16} />
      </Link>
    </Button>
  );
}
