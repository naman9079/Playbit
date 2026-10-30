import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
} from "recharts";
import { Target, Clock, Check, ArrowUpRight } from "lucide-react";
import { api } from "../services/api";
import { subjectName } from "../lib/catalog";
import {
  ErrorBox,
  Loading,
  Empty,
  Stat,
  SectionTitle,
} from "../components/common";
import { Button } from "../components/ui/button";
export default function Progress() {
  const {
    data: p,
    isPending,
    error,
  } = useQuery({ queryKey: ["progress"], queryFn: () => api("/progress") });
  if (isPending) return <Loading />;
  if (error) return <ErrorBox error={error} />;
  if (!p) return null;
  const completed = p.history.filter((m: any) => m.status === "completed");
  const chart = [...completed]
    .reverse()
    .map((m: any, i: number) => ({ round: i + 1, score: m.score }));
  return (
    <div>
      <div className="page-heading">
        <span className="eyebrow">SMALL STEPS. REAL GROWTH.</span>
        <h1>Look how far you’re going.</h1>
        <p>
          Every attempt counts. Your learning story, built from your actual
          games.
        </p>
      </div>
      <div className="stats-grid three">
        <Stat
          label="Questions attempted"
          value={p.totals.attempts}
          icon={<Target />}
        />
        <Stat
          label="Overall accuracy"
          value={
            p.totals.attempts
              ? `${Math.round((p.totals.correct / p.totals.attempts) * 100)}%`
              : "—"
          }
          icon={<Check />}
        />
        <Stat
          label="Average response"
          value={
            p.totals.attempts
              ? `${(p.totals.average_ms / 1000).toFixed(1)}s`
              : "—"
          }
          icon={<Clock />}
        />
      </div>
      <div className="progress-grid">
        <section className="panel">
          <SectionTitle title="Your momentum" eyebrow="RECENT MATCH SCORES" />
          {chart.length ? (
            <div className="chart">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={chart}>
                  <defs>
                    <linearGradient id="scoreFill" x1="0" y1="0" x2="0" y2="1">
                      <stop
                        offset="0%"
                        stopColor="#aa88ff"
                        stopOpacity={0.35}
                      />
                      <stop offset="100%" stopColor="#aa88ff" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid stroke="#292936" vertical={false} />
                  <XAxis
                    dataKey="round"
                    tick={{ fill: "#9393a7" }}
                    axisLine={false}
                    tickLine={false}
                  />
                  <YAxis
                    domain={[0, 600]}
                    tick={{ fill: "#9393a7" }}
                    axisLine={false}
                    tickLine={false}
                  />
                  <Tooltip
                    contentStyle={{
                      background: "#20202e",
                      border: "1px solid #3c3c51",
                      borderRadius: 12,
                    }}
                  />
                  <Area
                    type="monotone"
                    dataKey="score"
                    stroke="#aa88ff"
                    strokeWidth={3}
                    fill="url(#scoreFill)"
                  />
                </AreaChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <Empty>Play a round to start your progress chart.</Empty>
          )}
        </section>
        <section className="panel">
          <SectionTitle title="Your skill map" eyebrow="TOPIC MASTERY" />
          {p.mastery.length ? (
            p.mastery.map((m: any) => (
              <div className="mastery-row" key={m.topic_id}>
                <div>
                  <strong>{m.name}</strong>
                  <span>{Math.round(m.mastery_score)}%</span>
                </div>
                <div className="mastery-track">
                  <div style={{ width: `${m.mastery_score}%` }} />
                </div>
                <small>
                  {m.attempts_count} attempts · {subjectName(m.subject_id)}
                </small>
              </div>
            ))
          ) : (
            <Empty>
              Your topic strengths will appear after your first placement.
            </Empty>
          )}
        </section>
      </div>
      <History rows={p.history} />
    </div>
  );
}
export function History({ rows }: { rows: any[] }) {
  return (
    <section className="panel history-panel">
      <SectionTitle title="Your recent rounds" eyebrow="THE JOURNEY SO FAR" />
      {rows.length ? (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Subject / mode</th>
                <th>Result</th>
                <th>Score</th>
                <th>XP</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((m) => (
                <tr key={m.id}>
                  <td>
                    <strong>{subjectName(m.subject_id)}</strong>
                    <small>
                      {m.mode} · {new Date(m.created_at).toLocaleDateString()}
                    </small>
                  </td>
                  <td>
                    <span
                      className={`outcome ${m.outcome === "win" ? "text-lime" : ""}`}
                    >
                      {m.outcome || m.status}
                    </span>
                  </td>
                  <td>{m.score}</td>
                  <td className="text-lime">+{m.xp_earned}</td>
                  <td>
                    {m.status !== "cancelled" && (
                      <Button size="sm" asChild variant="ghost">
                        <Link
                          aria-label={
                            m.status === "completed"
                              ? "View report"
                              : "Resume match"
                          }
                          to={`/${m.status === "completed" ? "results" : m.status === "waiting" ? "room" : "arena"}/${m.id}`}
                        >
                          <ArrowUpRight size={17} />
                        </Link>
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <Empty>
          Your first game is the start of a great story.{" "}
          <Link to="/play">Let’s play →</Link>
        </Empty>
      )}
    </section>
  );
}
