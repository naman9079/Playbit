import { useState } from "react";
import { Link, Navigate, useSearchParams } from "react-router-dom";
import { ArrowRight, Command } from "lucide-react";
import { supabase, configured } from "../lib/supabase";
import { useSession } from "../stores/session";
import { Button } from "../components/ui/button";
import { ErrorBox, SetupNotice } from "../components/common";
function readableAuthError(error: unknown) {
  const message = error instanceof Error ? error.message : String(error);
  if (message.toLowerCase().includes("email rate limit exceeded")) {
    return new Error(
      "Supabase’s built-in email sender has reached its 2-emails-per-hour project limit. In Supabase, open Authentication → Emails → SMTP Settings and configure a custom SMTP provider. Until it is configured, wait for the limit to reset; if you already have an account, choose Sign in.",
    );
  }
  return error;
}
export default function Auth() {
  const session = useSession((s) => s.session);
  const [params] = useSearchParams();
  const [signup, setSignup] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<unknown>();
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const next = params.get("next");
  if (session)
    return (
      <Navigate
        to={next?.startsWith("/") && !next.startsWith("//") ? next : "/play"}
        replace
      />
    );
  return (
    <div className="auth-layout">
      <div className="auth-story">
        <Command size={46} />
        <h1>
          Your next
          <br />
          level starts
          <br />
          <span>right here.</span>
        </h1>
        <p>
          A little curiosity. A little competition.
          <br />A whole new way to learn.
        </p>
        <div className="auth-orbit">✳</div>
      </div>
      <section className="form-card">
        <span className="eyebrow">WELCOME TO PLAYBIT</span>
        <h2>{signup ? "Join the playground." : "Back for another round?"}</h2>
        <p>
          {signup
            ? "Create your account. Find your edge."
            : "Sign in to pick up where you left off."}
        </p>
        {!configured && <SetupNotice />}
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            setError(null);
            setMessage("");
            setBusy(true);
            try {
              if (!supabase)
                throw new Error(
                  "Supabase is not configured yet. Follow the setup instructions.",
                );
              const result = signup
                ? await supabase.auth.signUp({
                    email,
                    password,
                    options: {
                      emailRedirectTo: window.location.origin + "/play",
                    },
                  })
                : await supabase.auth.signInWithPassword({ email, password });
              if (result.error) throw result.error;
              if (signup && !result.data.session)
                setMessage(
                  "Check your email to confirm your account, then sign in here.",
                );
            } catch (ex) {
              setError(readableAuthError(ex));
            } finally {
              setBusy(false);
            }
          }}
        >
          <label>
            Email address
            <input
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
            />
          </label>
          <label>
            Password
            <input
              type="password"
              minLength={signup ? 8 : undefined}
              required
              autoComplete={signup ? "new-password" : "current-password"}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="At least 8 characters"
            />
          </label>
          <ErrorBox error={error} />
          {message && (
            <p role="status" className="success">
              {message}
            </p>
          )}
          <Button disabled={busy || !configured}>
            {busy ? "One moment…" : signup ? "Create account" : "Let’s go"}
            <ArrowRight size={18} />
          </Button>
        </form>
        <p className="form-switch">
          {signup ? "Already a player?" : "New around here?"}{" "}
          <button
            onClick={() => {
              setSignup(!signup);
              setError(null);
            }}
          >
            {signup ? "Sign in" : "Create an account"}
          </button>
        </p>
        <Link className="muted" to="/">
          ← Back to the playground
        </Link>
      </section>
    </div>
  );
}
