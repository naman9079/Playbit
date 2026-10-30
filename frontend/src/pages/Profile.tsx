import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Trophy, Zap, Target, Pencil, Check } from "lucide-react";
import { api } from "../services/api";
import { subjects } from "../lib/catalog";
import { Avatar, Loading, ErrorBox, Stat } from "../components/common";
import { Button } from "../components/ui/button";
import { History } from "./Progress";
export default function Profile() {
  const cache = useQueryClient();
  const {
    data: me,
    error,
    isPending,
  } = useQuery({ queryKey: ["me"], queryFn: () => api("/me") });
  const history = useQuery({
    queryKey: ["history"],
    queryFn: () => api<any[]>("/matches/history"),
  });
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState("");
  const [avatar, setAvatar] = useState(0);
  const save = useMutation({
    mutationFn: () =>
      api("/me", "PUT", {
        username: me!.username,
        displayName: name,
        avatar,
        language: me!.language,
        proficiency: me!.proficiency,
        subjects: me!.subjects,
      }),
    onSuccess: () => {
      setEditing(false);
      void cache.invalidateQueries({ queryKey: ["me"] });
    },
  });
  if (isPending) return <Loading />;
  if (error) return <ErrorBox error={error} />;
  if (!me) return null;
  if (!me.onboarded)
    return (
      <div className="panel">
        <h1>Let’s build your profile.</h1>
        <Button asChild>
          <Link to="/onboarding">Get started</Link>
        </Button>
      </div>
    );
  const assessed = me.ratings
    .filter((r: any) => r.assessed)
    .sort((a: any, b: any) => b.rating - a.rating);
  return (
    <div>
      <div className="page-heading">
        <span className="eyebrow">PLAYER ONE</span>
        <h1>Your story. Still leveling up.</h1>
      </div>
      <section className="profile-hero panel">
        <Avatar seed={me.avatar_url} size="xl" />
        <div>
          <span className="eyebrow">LEVEL {me.level} CONTENDER</span>
          <h1>{me.display_name}</h1>
          <p>
            @{me.username} · Joined{" "}
            {new Date(me.created_at).toLocaleDateString()}
          </p>
          <div className="level-progress">
            <span>{me.total_xp} XP</span>
            <div>
              <div
                style={{
                  width: `${((me.total_xp - me.levelStartXp) / (me.nextLevelXp - me.levelStartXp)) * 100}%`,
                }}
              />
            </div>
            <span>
              {me.nextLevelXp - me.total_xp} to level {me.level + 1}
            </span>
          </div>
        </div>
        <Button
          variant="outline"
          onClick={() => {
            setName(me.display_name);
            setAvatar(Number(me.avatar_url));
            setEditing(!editing);
          }}
        >
          <Pencil size={15} /> Edit profile
        </Button>
      </section>
      {editing && (
        <form
          className="panel edit-profile"
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate();
          }}
        >
          <label>
            Display name
            <input
              required
              value={name}
              maxLength={40}
              onChange={(e) => setName(e.target.value)}
            />
          </label>
          <div className="avatar-picker">
            {Array.from({ length: 8 }, (_, i) => (
              <button
                type="button"
                key={i}
                aria-label={`Avatar ${i + 1}`}
                aria-pressed={i === avatar}
                className={i === avatar ? "selected" : ""}
                onClick={() => setAvatar(i)}
              >
                <Avatar seed={i} />
              </button>
            ))}
          </div>
          <ErrorBox error={save.error} />
          <Button disabled={save.isPending}>
            Save changes <Check size={16} />
          </Button>
        </form>
      )}
      <div className="stats-grid">
        <Stat label="Total XP" value={me.total_xp} icon={<Zap />} />
        <Stat
          label="Matches played"
          value={me.stats.played}
          icon={<Target />}
        />
        <Stat
          label="Wins / losses"
          value={`${me.stats.wins} / ${me.stats.losses}`}
          icon={<Trophy />}
        />
        <Stat
          label="Win percentage"
          value={
            me.stats.played
              ? `${Math.round((me.stats.wins / me.stats.played) * 100)}%`
              : "—"
          }
          icon={<Target />}
        />
      </div>
      <div className="section-title">
        <h2>Your competitive skill</h2>
        <span className="muted">XP and subject ratings are independent</span>
      </div>
      <div className="subject-grid">
        {subjects.map((s) => {
          const r = me.ratings.find((r: any) => r.subject_id === s.id);
          return (
            <section className={`panel rating-card ${s.color}`} key={s.id}>
              <s.icon size={28} />
              <h3>{s.name}</h3>
              <strong>{r?.assessed ? r.rating : "Unplaced"}</strong>
              <p>
                {r?.assessed
                  ? `${r.provisional ? "Provisional · " : ""}${r.ranked_matches_played} ranked matches`
                  : "Complete your placement to begin"}
              </p>
            </section>
          );
        })}
      </div>
      {assessed.length > 0 && (
        <p className="profile-strength">
          Strongest rating: <strong>{assessed[0].name}</strong> · Next focus:{" "}
          <strong>{assessed.at(-1).name}</strong>
        </p>
      )}
      <ErrorBox error={history.error} />
      <History rows={history.data || []} />
    </div>
  );
}
