import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery } from "@tanstack/react-query";
import { motion } from "framer-motion";
import {
  Trophy,
  Target,
  Zap,
  Clock,
  ArrowRight,
  Check,
  X,
  Sparkles,
  RotateCcw,
  ChevronDown,
} from "lucide-react";
import { api } from "../services/api";
import { curriculumScope, subjectName } from "../lib/catalog";
import { Button } from "../components/ui/button";
import { ErrorBox, Loading, Stat } from "../components/common";
export default function Results() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const {
    data: r,
    error,
    isPending,
  } = useQuery({
    queryKey: ["report", id],
    queryFn: () => api(`/reports/${id}`),
  });
  const { data: me } = useQuery({
    queryKey: ["me"],
    queryFn: () => api("/me"),
  });
  const recovery = useMutation({
    mutationFn: () => api("/recovery/start", "POST", { matchId: id }),
    onSuccess: (m) => navigate(`/arena/${m.id}`),
  });
  const ai = useMutation({
    mutationFn: () => api(`/ai/reports/${id}`, "POST"),
  });
  const [open, setOpen] = useState<string | null>(null);
  if (isPending) return <Loading />;
  if (error) return <ErrorBox error={error} />;
  if (!r) return null;
  const s = r.summary;
  const diagnostic = r.match.mode === "diagnostic";
  return (
    <div className="results-page">
      <motion.section
        className="result-hero"
        initial={{ opacity: 0, scale: 0.97 }}
        animate={{ opacity: 1, scale: 1 }}
      >
        <div className="result-spark spark-left">✦</div>
        <div className="result-spark spark-right">✳</div>
        <span className="result-trophy">
          {s.outcome === "win" ? <Trophy size={46} /> : <Target size={46} />}
        </span>
        <div className="eyebrow">
          {subjectName(r.match.subject_id)} · {curriculumScope(r.match)} ·{" "}
          {diagnostic
            ? "PLACEMENT COMPLETE"
            : `${s.outcome === "complete" ? "CHALLENGE" : s.outcome.toUpperCase()} · ROUND COMPLETE`}
        </div>
        <h1>
          {diagnostic
            ? "Your journey has a starting point."
            : s.outcome === "win"
              ? "Now that’s a power move."
              : s.outcome === "draw"
                ? "Great minds. Even match."
                : s.outcome === "loss"
                  ? "A setback. A smarter comeback."
                  : "A little sharper than before."}
        </h1>
        <p>
          {diagnostic
            ? `Your provisional rating is ${s.rating_after}. It will develop through ranked play.`
            : "Every answer tells a story. Here’s what this round taught you."}
        </p>
        <div className="final-score">
          <strong>{s.score}</strong>
          <span>
            POINTS{" "}
            {["bot", "friend", "ranked"].includes(r.match.mode) &&
              `/ ${s.opponentScore} OPPONENT`}
          </span>
        </div>
        <div className="xp-earned">
          <Zap size={17} /> +{s.xp_earned} XP{" "}
          {r.match.mode === "ranked" && (
            <span>
              · Rating {s.rating_after - s.rating_before >= 0 ? "+" : ""}
              {s.rating_after - s.rating_before}
            </span>
          )}
        </div>
        {me && (
          <div className="level-progress">
            <span>LEVEL {me.level}</span>
            <div>
              <motion.div
                initial={{ width: 0 }}
                animate={{
                  width: `${Math.min(100, ((me.total_xp - me.levelStartXp) / (me.nextLevelXp - me.levelStartXp)) * 100)}%`,
                }}
              />
            </div>
            <span>
              {me.total_xp} / {me.nextLevelXp} XP
            </span>
          </div>
        )}
        <div className="button-row">
          <Button asChild>
            <Link to="/play">
              Play another round <ArrowRight size={18} />
            </Link>
          </Button>
          <Button variant="outline" asChild>
            <a href="#breakdown">
              Review my answers <ChevronDown size={17} />
            </a>
          </Button>
        </div>
      </motion.section>
      <div className="stats-grid">
        <Stat icon={<Target />} label="Accuracy" value={`${s.accuracy}%`} />
        <Stat
          icon={<Check />}
          label="Correct answers"
          value={`${s.correct} / ${r.questions.length}`}
        />
        <Stat
          icon={<Clock />}
          label="Average response"
          value={`${(s.averageResponseMs / 1000).toFixed(1)}s`}
        />
        <Stat icon={<Zap />} label="XP earned" value={`+${s.xp_earned}`} />
      </div>
      <div className="learning-report panel">
        <div className="learning-report-heading">
          <Sparkles size={25} />
          <div>
            <span className="eyebrow">YOUR LEARNING COMPANION</span>
            <h2>Let’s turn this round into progress.</h2>
          </div>
          <span className="source-pill">Verified analysis</span>
        </div>
        <div className="report-topic-grid">
          <div>
            <span className="text-lime">YOUR STRONGEST TOPIC THIS ROUND</span>
            <h3>{r.recommendations.strongTopic}</h3>
          </div>
          <div>
            <span className="text-purple">YOUR NEXT FOCUS</span>
            <h3>{r.recommendations.weakTopic}</h3>
          </div>
        </div>
        <p>{r.recommendations.message}</p>
        <div className="topic-results">
          {r.recommendations.topics.map((t: any) => (
            <span key={t.topic_id}>
              {t.name}{" "}
              <strong>
                {t.correct}/{t.attempts}
              </strong>
            </span>
          ))}
        </div>
        <Button
          variant="outline"
          disabled={ai.isPending}
          onClick={() => ai.mutate()}
        >
          <Sparkles size={16} />
          {ai.isPending
            ? "Choosing your next focus…"
            : "Get AI-assisted revision priorities"}
        </Button>
        {ai.data && (
          <div className="ai-response" role="status">
            <span className="source-pill">
              {ai.data.source === "verified-fallback"
                ? "AI unavailable · verified fallback"
                : "AI-selected · verified guidance"}
            </span>
            <p>{ai.data.message || ai.data.recommendations.message}</p>
            {ai.data.topicIds && <p>Focus: {ai.data.topicIds.join(", ")}</p>}
          </div>
        )}
        <ErrorBox error={ai.error} />
      </div>
      <div className="recovery-banner">
        <div className="recovery-icon">
          <RotateCcw size={32} />
        </div>
        <div>
          <span className="eyebrow">THE PLAYBIT RECOVERY CHALLENGE</span>
          <h2>Make your comeback count.</h2>
          <p>Three fresh questions. One focused topic. +20 bonus XP.</p>
        </div>
        <Button disabled={recovery.isPending} onClick={() => recovery.mutate()}>
          Start recovery <ArrowRight size={18} />
        </Button>
        <ErrorBox error={recovery.error} />
      </div>
      <section id="breakdown">
        <div className="section-title">
          <div>
            <span className="eyebrow">THE AHA! MOMENTS</span>
            <h2>Your round, question by question.</h2>
          </div>
        </div>
        {r.questions.map((q: any, i: number) => (
          <article
            className={`question-review ${q.is_correct ? "review-correct" : "review-incorrect"}`}
            key={q.id}
          >
            <button
              className="review-toggle"
              aria-expanded={open === q.id}
              onClick={() => setOpen(open === q.id ? null : q.id)}
            >
              <span className="review-status">
                {q.is_correct ? <Check size={20} /> : <X size={20} />}
              </span>
              <div>
                <small>
                  QUESTION {i + 1} · {q.topic} ·{" "}
                  {["", "EASY", "MEDIUM", "HARD"][q.difficulty]}
                </small>
                <h3>{q.question_text}</h3>
              </div>
              <span className="review-time">
                {(q.response_time_ms / 1000).toFixed(1)}s
                <ChevronDown size={18} />
              </span>
            </button>
            {open === q.id && (
              <div className="review-detail">
                <div className="review-answers">
                  <span>
                    Your answer{" "}
                    <strong>{q.submitted_answer || "No answer"}</strong>
                  </span>
                  <span>
                    Correct answer{" "}
                    <strong className="text-lime">{q.correct_answer}</strong>
                  </span>
                  <span>
                    Points <strong>+{q.awarded_points}</strong>
                  </span>
                </div>
                <div className="formula">
                  <span>THE FORMULA</span>
                  <strong>{q.formula}</strong>
                </div>
                <ol>
                  {q.steps.map((step: string, j: number) => (
                    <li key={j}>{step}</li>
                  ))}
                </ol>
                <p>{q.explanation}</p>
                {q.possibleMisconception && (
                  <p className="misconception">{q.possibleMisconception}</p>
                )}
                <Tutor matchId={id} questionId={q.id} />
              </div>
            )}
          </article>
        ))}
      </section>
    </div>
  );
}
function Tutor({
  matchId,
  questionId,
}: {
  matchId: string;
  questionId: string;
}) {
  const hint = useMutation({
    mutationFn: () => api("/ai/hint", "POST", { matchId, questionId }),
  });
  return (
    <>
      <Button
        variant="ghost"
        disabled={hint.isPending}
        onClick={() => hint.mutate()}
      >
        <Sparkles size={15} />
        {hint.isPending ? "Finding the right emphasis…" : "Help me understand"}
      </Button>
      <ErrorBox error={hint.error} />
      {hint.data && (
        <div className="ai-response">
          <small>
            {hint.data.source === "verified-fallback"
              ? "VERIFIED FALLBACK"
              : "AI-ASSISTED · VERIFIED CONTENT"}
          </small>
          <p>
            Focus on the {hint.data.focus}:{" "}
            {hint.data.focus === "formula"
              ? hint.data.formula
              : hint.data.focus === "concept"
                ? hint.data.explanation
                : hint.data.steps.join(" ")}
          </p>
        </div>
      )}
    </>
  );
}
