import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  Bot,
  Check,
  Clock,
  Copy,
  ArrowRight,
  ShieldCheck,
  Swords,
  X,
} from "lucide-react";
import { useMatch } from "../hooks/useMatch";
import { useSession } from "../stores/session";
import { api } from "../services/api";
import { curriculumScope, subjectName } from "../lib/catalog";
import { Avatar, ErrorBox, Loading } from "../components/common";
import { Button } from "../components/ui/button";
export function Room() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const cache = useQueryClient();
  const { data: m, error, isPending } = useMatch(id);
  const [copied, setCopied] = useState(false);
  const [copyError, setCopyError] = useState<unknown>();
  const ready = useMutation({
    mutationFn: () => api(`/rooms/${id}/ready`, "POST"),
    onSuccess: () => cache.invalidateQueries({ queryKey: ["match", id] }),
  });
  const leave = useMutation({
    mutationFn: () => api(`/rooms/${id}/leave`, "POST"),
    onSuccess: () => {
      void cache.invalidateQueries({ queryKey: ["history"] });
      navigate("/play");
    },
  });
  useEffect(() => {
    if (m?.status === "active") navigate(`/arena/${id}`, { replace: true });
  }, [m?.status, id, navigate]);
  if (isPending) return <Loading />;
  if (error) return <ErrorBox error={error} />;
  if (!m) return null;
  return (
    <div className="narrow-page">
      <div className="page-heading center">
        <span className="eyebrow">BETTER WITH A RIVAL</span>
        <h1>Your private arena.</h1>
        <p>
          {subjectName(m.subject_id)} · {curriculumScope(m)} · 5 questions · Unranked friend duel
        </p>
      </div>
      <div className="panel room-panel">
        {m.status === "cancelled" ? (
          <>
            <h2>This room was closed.</h2>
            <Button asChild>
              <Link to="/play">Back to play</Link>
            </Button>
          </>
        ) : (
          <>
            <span className="eyebrow">SHARE YOUR ROOM CODE</span>
            <div className="room-code">{m.room_code}</div>
            <Button
              variant="outline"
              onClick={async () => {
                try {
                  await navigator.clipboard.writeText(
                    `${window.location.origin}/play?mode=friend&room=${m.room_code}`,
                  );
                  setCopied(true);
                } catch (ex) {
                  setCopyError(ex);
                }
              }}
            >
              {copied ? <Check size={16} /> : <Copy size={16} />}{" "}
              {copied ? "Invite copied" : "Copy invite link"}
            </Button>
            <ErrorBox error={copyError} />
            <div className="room-players">
              {m.players.map((p: any) => (
                <div key={p.user_id}>
                  <Avatar seed={p.avatar_url} size="lg" />
                  <h3>{p.display_name}</h3>
                  <span className={p.ready ? "text-lime" : "muted"}>
                    {p.ready ? "Ready to play" : "Getting ready"}
                  </span>
                </div>
              ))}
              {m.players.length < 2 && (
                <div className="waiting-player">
                  <span>?</span>
                  <h3>Your next rival</h3>
                  <p>Waiting for a friend…</p>
                </div>
              )}
            </div>
            <p>
              When both players are ready, the countdown begins automatically.
            </p>
            <ErrorBox error={ready.error || leave.error} />
            <div className="button-row">
              <Button
                disabled={
                  ready.isPending ||
                  m.players.find(
                    (p: any) =>
                      p.user_id === useSession.getState().session?.user.id,
                  )?.ready
                }
                onClick={() => ready.mutate()}
              >
                I’m ready <Check size={18} />
              </Button>
              <Button variant="ghost" onClick={() => leave.mutate()}>
                Leave room
              </Button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
export default function Arena() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const cache = useQueryClient();
  const user = useSession((s) => s.session?.user.id);
  const { data: m, error, isPending, dataUpdatedAt } = useMatch(id);
  const [now, setNow] = useState(Date.now());
  const [selected, setSelected] = useState<string | null>(null);
  const [localAccepted, setLocalAccepted] = useState(false);
  const boundaryRefreshFor = useRef<string | null>(null);
  const question = m?.question;
  const submit = useMutation({
    mutationFn: (answer: string) =>
      api(`/matches/${id}/answers`, "POST", {
        questionId: question.id,
        answer,
      }),
    onSuccess: (result) => {
      // An answer response may arrive after the next round is already displayed.
      if (cache.getQueryData<any>(["match", id])?.question?.id === result.questionId)
        setLocalAccepted(true);
      void cache.invalidateQueries({ queryKey: ["match", id] }, { cancelRefetch: false });
    },
  });
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 100);
    return () => clearInterval(timer);
  }, []);
  useEffect(() => {
    setSelected(null);
    setLocalAccepted(false);
    submit.reset();
  }, [question?.id]);
  useEffect(() => {
    if (m?.status === "completed") {
      void cache.invalidateQueries({ queryKey: ["me"] });
      void cache.invalidateQueries({ queryKey: ["history"] });
      navigate(`/results/${id}`, { replace: true });
    } else if (m?.status === "waiting")
      navigate(`/room/${id}`, { replace: true });
  }, [m?.status, id, navigate, cache]);
  const accepted = localAccepted || question?.submitted != null;
  const serverNow = m
    ? new Date(m.serverNow).getTime() + (now - dataUpdatedAt)
    : now;
  const remaining = question
    ? Math.max(0, (new Date(question.deadline_at).getTime() - serverNow) / 1000)
    : 0;
  const secondsToStart = m
    ? Math.max(
        0,
        Math.ceil((new Date(m.started_at).getTime() - serverNow) / 1000),
      )
    : 0;
  const boundaryKey =
    question?.id ??
    (m?.status === "active" ? `start:${m.started_at}` : null);
  const boundaryReached = question
    ? remaining <= 0
    : m?.status === "active" && secondsToStart <= 0;
  useEffect(() => {
    function keydown(e: KeyboardEvent) {
      if (!question || accepted || submit.isPending || remaining <= 0) return;
      const index = Number(e.key) - 1;
      if (index >= 0 && index < 4) {
        setSelected(question.options[index]);
        submit.mutate(question.options[index]);
      }
    }
    window.addEventListener("keydown", keydown);
    return () => window.removeEventListener("keydown", keydown);
  }, [question, accepted, submit.isPending, remaining]);
  useEffect(() => {
    if (!boundaryKey) return;
    if (!boundaryReached) {
      if (boundaryRefreshFor.current === boundaryKey)
        boundaryRefreshFor.current = null;
      return;
    }
    if (boundaryRefreshFor.current === boundaryKey) return;
    boundaryRefreshFor.current = boundaryKey;
    void cache.invalidateQueries(
      { queryKey: ["match", id], exact: true },
      { cancelRefetch: false },
    );
  }, [boundaryKey, boundaryReached, cache, id]);
  if (isPending) return <Loading />;
  if (error) return <ErrorBox error={error} />;
  if (!m) return null;
  const me = m.players.find((p: any) => p.user_id === user);
  const opponent = m.players.find((p: any) => p.user_id !== user);
  const feedback = m.feedback?.at(-1);
  return (
    <div className="arena-page">
      <div className="arena-heading">
        <span className="eyebrow">
          <Swords size={16} />{" "}
          {m.mode === "diagnostic"
            ? "PLACEMENT"
            : m.mode === "recovery"
              ? "RECOVERY CHALLENGE"
              : m.mode === "ranked"
                ? "RANKED BATTLE"
                : m.mode === "bot"
                  ? "BOT BATTLE"
                  : "YOUR ARENA"}
        </span>
        <span>{subjectName(m.subject_id)} · {curriculumScope(m)}</span>
      </div>
      <div className="scoreboard">
        <div className="score-player">
          <Avatar seed={me?.avatar_url} size="lg" />
          <div>
            <small>YOU</small>
            <h3>{me?.display_name}</h3>
          </div>
          <strong>{me?.score || 0}</strong>
        </div>
        <span className="versus">
          {m.mode === "solo" ||
          m.mode === "recovery" ||
          m.mode === "diagnostic" ? (
            <TargetIcon />
          ) : (
            "VS"
          )}
        </span>
        {opponent ? (
          <div className="score-player opponent">
            <strong>{opponent.score}</strong>
            <div>
              <small>OPPONENT</small>
              <h3>{opponent.display_name}</h3>
            </div>
            <Avatar seed={opponent.avatar_url} size="lg" />
          </div>
        ) : m.mode === "bot" ? (
          <div className="score-player opponent">
            <strong>{m.bot_score}</strong>
            <div>
              <small>
                SIMULATED BOT · {["", "EASY", "MEDIUM", "HARD"][m.difficulty]}
              </small>
              <h3>Byte</h3>
            </div>
            <span className="bot-avatar">
              <Bot size={35} />
            </span>
          </div>
        ) : (
          <div className="score-player opponent">
            <div>
              <small>FOCUS MODE</small>
              <h3>You’ve got this.</h3>
            </div>
          </div>
        )}
      </div>
      <div className="question-progress">
        {Array.from({ length: m.question_count }, (_, i) => (
          <span
            key={i}
            className={
              i < (question?.question_order ?? m.feedback.length)
                ? "done"
                : i === question?.question_order
                  ? "current"
                  : ""
            }
          />
        ))}
      </div>
      {question ? (
        <section className="question-panel">
          <div className="question-meta">
            <span>
              QUESTION {question.question_order + 1}{" "}
              <span className="muted">/ {m.question_count}</span>
            </span>
            <span className={`timer ${remaining < 5 ? "urgent" : ""}`}>
              <Clock size={18} />
              {Math.ceil(remaining)}
              <small>SEC</small>
            </span>
            <span className="difficulty">
              {["", "EASY", "MEDIUM", "HARD"][question.difficulty]}
            </span>
          </div>
          <div className="timer-track">
            <div style={{ width: `${(remaining / 15) * 100}%` }} />
          </div>
          <h1>{question.question_text}</h1>
          <div className="answers">
            {question.options.map((option: string, i: number) => (
              <button
                key={option}
                className={`answer ${(question.submitted ?? selected) === option ? "chosen" : ""}`}
                disabled={accepted || submit.isPending || remaining <= 0}
                onClick={() => {
                  setSelected(option);
                  submit.mutate(option);
                }}
              >
                <span>{String.fromCharCode(65 + i)}</span>
                <strong>{option}</strong>
                <small>{i + 1}</small>
                {accepted && (question.submitted ?? selected) === option && (
                  <Check size={19} />
                )}
              </button>
            ))}
          </div>
          <div className="answer-status" aria-live="polite">
            {accepted ? (
              <>
                <Check size={17} />
                <span>
                  Answer locked. The next question arrives when the timer ends.
                </span>
              </>
            ) : (
              <>
                <ShieldCheck size={17} />
                <span>
                  Choose your answer or use keys 1–4. Every second counts.
                </span>
              </>
            )}
          </div>
          <ErrorBox error={submit.error} />
        </section>
      ) : (
        <section className="question-panel countdown">
          <span className="eyebrow">TAKE A BREATH. FIND YOUR FOCUS.</span>
          <h1>
            {secondsToStart > 0 ? secondsToStart : "Finishing your round…"}
          </h1>
          <p>
            {secondsToStart > 0
              ? "Your first question is on its way."
              : "Saving your score and learning report."}
          </p>
        </section>
      )}
      {feedback && (
        <div
          className={`round-feedback ${feedback.is_correct ? "correct" : "incorrect"}`}
          role="status"
        >
          {feedback.is_correct ? <Check size={20} /> : <X size={20} />}
          <span>
            Previous round:{" "}
            {feedback.is_correct
              ? `Nice work! +${feedback.awarded_points} points`
              : `Correct answer: ${feedback.correct_answer}`}
          </span>
        </div>
      )}
      <div className="arena-footer">
        <ShieldCheck size={15} />
        <span>
          Server-verified scoring · Refreshing keeps your match intact
        </span>
      </div>
    </div>
  );
}
function TargetIcon() {
  return <span>✳</span>;
}
