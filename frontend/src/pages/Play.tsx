import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  Bot,
  Swords,
  Users,
  Target,
  ArrowRight,
  ShieldCheck,
  Clock,
  Check,
  Copy,
  LoaderCircle,
} from "lucide-react";
import { api } from "../services/api";
import { subjects } from "../lib/catalog";
import { usePreferences } from "../stores/session";
import { Button } from "../components/ui/button";
import { ErrorBox, Loading, SectionTitle, Avatar } from "../components/common";
const modes = [
  {
    id: "bot",
    icon: Bot,
    title: "Battle a bot",
    desc: "Meet Byte, your always-ready sparring partner.",
    tag: "WARM-UP",
    color: "purple",
  },
  {
    id: "ranked",
    icon: Swords,
    title: "Find an opponent",
    desc: "A real player. A fair challenge. Your next rival.",
    tag: "RANKED",
    color: "pink",
  },
  {
    id: "friend",
    icon: Users,
    title: "Play with a friend",
    desc: "One invitation. Two players. Bragging rights.",
    tag: "PRIVATE DUEL",
    color: "cyan",
  },
  {
    id: "solo",
    icon: Target,
    title: "Solo practice",
    desc: "Find your rhythm with adaptive questions.",
    tag: "YOUR PACE",
    color: "lime",
  },
];
export default function Play() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { subject, setSubject } = usePreferences();
  const [mode, setMode] = useState(
    modes.some((m) => m.id === params.get("mode"))
      ? params.get("mode")!
      : "bot",
  );
  const [difficulty, setDifficulty] = useState(0);
  const [classLevel, setClassLevel] = useState("");
  const [chapter, setChapter] = useState("");
  const [topic, setTopic] = useState("");
  const [code, setCode] = useState(params.get("room") || "");
  const [searching, setSearching] = useState(false);
  const [queueError, setQueueError] = useState<unknown>();
  const [queueInfo, setQueueInfo] = useState<Record<string, any>>({
    elapsed: 0,
  });
  const {
    data: me,
    isPending,
    error,
  } = useQuery({ queryKey: ["me"], queryFn: () => api("/me") });
  const curriculum = useQuery({
    queryKey: ["curriculum", subject, classLevel],
    queryFn: () =>
      api<{
        classLevels: number[];
        chapters: { id: string; name: string; topics: { id: string; name: string }[] }[];
      }>(`/subjects/${subject}/curriculum${classLevel ? `?classLevel=${classLevel}` : ""}`),
  });
  const chapters = curriculum.data?.chapters ?? [];
  const selectedChapter = chapters.find((item) => item.id === chapter);
  const availableTopics = selectedChapter
    ? selectedChapter.topics
    : chapters.flatMap((item) => item.topics);
  const rating = me?.ratings.find((r: any) => r.subject_id === subject);
  const start = useMutation({
    mutationFn: (diagnostic: boolean) =>
      api(
        diagnostic
          ? "/diagnostics/start"
          : mode === "friend"
            ? "/rooms/create"
            : `/matches/${mode}`,
        "POST",
        {
          subjectId: subject,
          classLevel: classLevel ? Number(classLevel) : null,
          chapterId: chapter || null,
          topicId: topic || null,
          difficulty,
        },
      ),
    onSuccess: (r) =>
      navigate(r.status === "waiting" ? `/room/${r.id}` : `/arena/${r.id}`),
  });
  const join = useMutation({
    mutationFn: () => api("/rooms/join", "POST", { code: code.toUpperCase() }),
    onSuccess: (r) => navigate(`/room/${r.id}`),
  });
  const history = useQuery({
    queryKey: ["history"],
    queryFn: () => api<any[]>("/matches/history"),
  });
  useEffect(() => {
    if (!searching) return;
    let stopped = false;
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      try {
        const r = await api("/matchmaking/join", "POST", {
          subjectId: subject,
          classLevel: classLevel ? Number(classLevel) : null,
          chapterId: chapter || null,
          topicId: topic || null,
          difficulty: 0,
        });
        if (stopped) return;
        setQueueInfo(r);
        if (r.matchId) {
          setSearching(false);
          navigate(`/arena/${r.matchId}`);
        } else timer = setTimeout(poll, 2000);
      } catch (ex) {
        if (!stopped) {
          setQueueError(ex);
          setSearching(false);
        }
      }
    }
    void poll();
    return () => {
      stopped = true;
      clearTimeout(timer);
    };
  }, [searching, subject, classLevel, chapter, topic, navigate]);
  async function cancel() {
    try {
      const r = await api("/matchmaking/cancel", "POST");
      setSearching(false);
      if (r.matchId) navigate(`/arena/${r.matchId}`);
    } catch (ex) {
      setQueueError(ex);
    }
  }
  if (isPending) return <Loading />;
  if (error) return <ErrorBox error={error} />;
  if (!me?.onboarded)
    return (
      <div className="narrow-page panel">
        <h1>Your player profile awaits.</h1>
        <p>Choose your avatar and subjects before entering the arena.</p>
        <Button asChild>
          <Link to="/onboarding">
            Create my profile <ArrowRight size={18} />
          </Link>
        </Button>
      </div>
    );
  const active = history.data?.find(
    (m) => m.status === "active" || m.status === "waiting",
  );
  return (
    <div>
      <div className="page-heading">
        <span className="eyebrow">THE ARENA IS YOURS</span>
        <h1>Ready. Set. Outthink.</h1>
        <p>Pick a subject, choose your challenge, and make your move.</p>
      </div>
      {active && (
        <div className="resume-banner">
          <Clock size={20} />
          <span>You have an unfinished {active.mode} game.</span>
          <Button asChild size="sm">
            <Link
              to={`/${active.status === "waiting" ? "room" : "arena"}/${active.id}`}
            >
              Resume game
            </Link>
          </Button>
        </div>
      )}
      <SectionTitle title="What are we playing?" eyebrow="01 / YOUR SUBJECT" />
      <div className="selection-grid subject-select">
        {subjects.map((s) => (
          <button
            disabled={searching}
            onClick={() => {
              setSubject(s.id);
              setChapter("");
              setTopic("");
            }}
            className={`selection-card ${s.color} ${subject === s.id ? "selected" : ""}`}
            key={s.id}
          >
            <s.icon size={28} />
            <div>
              <strong>{s.name}</strong>
              <small>{s.topics.join(" · ")}</small>
            </div>
            {subject === s.id && <Check size={18} />}
          </button>
        ))}
      </div>
      {!rating?.assessed ? (
        <div className="placement panel">
          <div className="placement-symbol">✳</div>
          <div>
            <span className="eyebrow">YOUR STARTING LINE</span>
            <h2>Let’s find your level.</h2>
            <p>
              Five questions. Fifteen seconds each. Your{" "}
              {subjects.find((s) => s.id === subject)?.short.toLowerCase()}{" "}
              journey starts with a quick placement.
            </p>
            <div className="inline-badges">
              <span>
                <Clock size={14} /> About 80 seconds
              </span>
              <span>
                <ShieldCheck size={14} /> A provisional game rating
              </span>
            </div>
            <ErrorBox error={start.error} />
            <Button
              disabled={start.isPending}
              onClick={() => start.mutate(true)}
            >
              Start my placement <ArrowRight size={18} />
            </Button>
          </div>
        </div>
      ) : (
        <>
          <SectionTitle
            title="Choose your kind of challenge"
            eyebrow="02 / YOUR MODE"
          >
            <span className="rating-pill">
              {rating.rating} rating {rating.provisional && "· Provisional"}
            </span>
          </SectionTitle>
          <div className="mode-grid">
            {modes.map((m) => (
              <button
                disabled={searching}
                key={m.id}
                className={`mode-card ${m.color} ${mode === m.id ? "selected" : ""}`}
                onClick={() => setMode(m.id)}
              >
                <div className="mode-top">
                  <m.icon size={30} />
                  <span>{m.tag}</span>
                </div>
                <h3>{m.title}</h3>
                <p>{m.desc}</p>
                <span className="mode-radio">{mode === m.id && <span />}</span>
              </button>
            ))}
          </div>
          <div className="panel game-setup">
            <div>
              <h3>Make it your match</h3>
              <p>
                {mode === "ranked"
                  ? "Find an assessed player with the same class, chapter, and topic filters."
                  : "5 questions · 15 seconds each · up to 120 points per answer"}
              </p>
              <div className="form-grid">
                <label>
                  Class
                  <select
                    disabled={searching}
                    value={classLevel}
                    onChange={(e) => {
                      setClassLevel(e.target.value);
                      setChapter("");
                      setTopic("");
                    }}
                  >
                    <option value="">Any class</option>
                    {curriculum.data?.classLevels.map((level) => (
                      <option value={level} key={level}>
                        Class {level}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Chapter
                  <select
                    disabled={searching || !classLevel}
                    value={chapter}
                    onChange={(e) => {
                      setChapter(e.target.value);
                      setTopic("");
                    }}
                  >
                    <option value="">{classLevel ? "All chapters" : "Choose a class first"}</option>
                    {chapters.map((item) => (
                      <option value={item.id} key={item.id}>
                        {item.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Topic
                  <select
                    disabled={searching}
                    value={topic}
                    onChange={(e) => setTopic(e.target.value)}
                  >
                    <option value="">
                      {chapter ? "All topics in this chapter" : "All topics · balanced mix"}
                    </option>
                    {availableTopics.map((t) => (
                      <option value={t.id} key={t.id}>
                        {t.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Difficulty
                  <select
                    disabled={mode === "ranked" || searching}
                    value={difficulty}
                    onChange={(e) => setDifficulty(Number(e.target.value))}
                  >
                    <option value={0}>Adaptive · matched to you</option>
                    <option value={1}>Easy · build your foundations</option>
                    <option value={2}>Medium · find your edge</option>
                    <option value={3}>Hard · push your limits</option>
                  </select>
                </label>
              </div>
              <small className="muted">
                Choose a class to see its combined school-maths chapter list.
                Chapter names and order can vary between textbooks. Chapter and
                topic filters are shared by both players in ranked duels.
              </small>
            </div>
            <div className="start-panel">
              <span className="start-art">
                {mode === "bot" ? (
                  <Bot size={44} />
                ) : mode === "ranked" ? (
                  <Swords size={44} />
                ) : mode === "friend" ? (
                  <Users size={44} />
                ) : (
                  <Target size={44} />
                )}
              </span>
              <p>
                {mode === "bot"
                  ? "Byte is a simulated bot. Bot games earn XP without changing your ranked rating."
                  : mode === "friend"
                    ? "Friend duels are unranked. Share a code and get ready together."
                    : mode === "ranked"
                      ? "Ranked play changes your subject rating. No available rival? Try a bot warm-up."
                      : "Make mistakes, get explanations, and grow your topic mastery."}
              </p>
              <Button
                disabled={start.isPending || searching}
                onClick={() =>
                  mode === "ranked" ? setSearching(true) : start.mutate(false)
                }
              >
                {mode === "friend"
                  ? "Create a room"
                  : mode === "ranked"
                    ? "Find my opponent"
                    : "Let’s play"}
                <ArrowRight size={18} />
              </Button>
            </div>
          </div>
          <ErrorBox error={start.error} />
        </>
      )}
      {(mode === "friend" || code) && (
        <div className="panel join-room">
          <div>
            <h3>Got an invite?</h3>
            <p>Enter your friend’s six-character room code.</p>
          </div>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              join.mutate();
            }}
          >
            <input
              aria-label="Room code"
              required
              minLength={6}
              maxLength={6}
              value={code}
              onChange={(e) => setCode(e.target.value.toUpperCase())}
              placeholder="ABC123"
            />
            <Button variant="outline" disabled={join.isPending}>
              Join room <ArrowRight size={17} />
            </Button>
          </form>
          <ErrorBox error={join.error} />
        </div>
      )}
      <ErrorBox error={queueError} />
      {searching && (
        <div className="modal-backdrop">
          <div className="search-modal panel">
            <div className="search-orbit">
              <Avatar seed={me.avatar_url} size="lg" />
              <LoaderCircle className="spin" size={110} />
            </div>
            <span className="eyebrow">LOOKING FOR YOUR NEXT RIVAL</span>
            <h2>Great minds think… competitively.</h2>
            <p>
              {subjects.find((s) => s.id === subject)?.name} ·{" "}
              {queueInfo.rating || rating.rating} rating
            </p>
            <strong className="search-time">{queueInfo.elapsed}s</strong>
            <p className="muted">
              Searching within ±{queueInfo.tolerance || 150} rating points
            </p>
            <Button variant="outline" onClick={cancel}>
              Cancel search
            </Button>
            {queueInfo.elapsed >= 20 && (
              <p>
                No opponent yet. Cancel and choose “Battle a bot” for a warm-up.
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
