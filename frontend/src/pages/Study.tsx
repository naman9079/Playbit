import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import {
  BookOpenText, Bot, Brain, Check, ChevronRight, Clock3, FileText,
  Flame, Lightbulb, MessageCircle, RotateCcw, Send, Sparkles, Upload,
  XCircle,
} from "lucide-react";
import { useSession } from "../stores/session";
import { api } from "../services/api";
import { Button } from "../components/ui/button";
import { extractStudyFile } from "../lib/study-import";

type ChatMessage = { role: "user" | "assistant"; text: string; hint?: string };
type Flashcard = { id: string; front: string; back: string; topic: string; dueAt: number; interval: number; ease: number };
type StudyQuestion = { question: string; answer: string; explanation: string; topic: string; evidence: string };
type Mastery = Record<string, { correct: number; total: number }>;
type StudyData = { title: string; notes: string; messages: ChatMessage[]; cards: Flashcard[]; questions: StudyQuestion[]; mastery: Mastery };
const empty: StudyData = { title: "", notes: "", messages: [], cards: [], questions: [], mastery: {} };
const storeKey = (userId: string) => `playbit-study-v2-${userId}`;
const legacyStoreKey = (userId: string) => `playbit-study-v1-${userId}`;
const clampText = (value: string) => value.slice(0, 120_000);

export default function Study() {
  const session = useSession((state) => state.session);
  const userId = session?.user.id;
  const [data, setData] = useState<StudyData>(empty);
  const [ready, setReady] = useState(false);
  const [tab, setTab] = useState<"chat" | "quiz" | "cards" | "heatmap">("chat");
  const [question, setQuestion] = useState("");
  const [busy, setBusy] = useState(false);
  const [chatError, setChatError] = useState("");
  const [quiz, setQuiz] = useState<StudyQuestion | null>(null);
  const [quizAnswer, setQuizAnswer] = useState("");
  const [quizChecked, setQuizChecked] = useState(false);
  const [quizBusy, setQuizBusy] = useState(false);
  const [deckBusy, setDeckBusy] = useState(false);
  const [cardShown, setCardShown] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!userId) { setData(empty); setReady(true); return; }
    try {
      const saved = localStorage.getItem(storeKey(userId));
      if (saved) setData({ ...empty, ...JSON.parse(saved) });
      else {
        const legacy = localStorage.getItem(legacyStoreKey(userId));
        const migrated = legacy ? { ...empty, ...JSON.parse(legacy), cards: [], questions: [], mastery: {} } : empty;
        setData(migrated);
        if (legacy) localStorage.setItem(storeKey(userId), JSON.stringify(migrated));
      }
    } catch { setData(empty); }
    setReady(true);
  }, [userId]);

  useEffect(() => {
    if (ready && userId) {
      try { localStorage.setItem(storeKey(userId), JSON.stringify(data)); } catch { /* Browser storage may be full. */ }
    }
  }, [data, ready, userId]);

  useEffect(() => { scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" }); }, [data.messages, tab]);

  const dueCards = useMemo(() => data.cards.filter((card) => card.dueAt <= Date.now()), [data.cards]);
  const topics = useMemo(() => Object.entries(data.mastery).sort((a, b) => {
    const scoreA = a[1].total ? a[1].correct / a[1].total : 0;
    const scoreB = b[1].total ? b[1].correct / b[1].total : 0;
    return scoreA - scoreB;
  }), [data.mastery]);

  async function importFile(file?: File) {
    if (!file) return;
    if (file.size > 20 * 1024 * 1024) { setChatError("That file is over 20 MB. Reduce its size or split it into smaller files."); return; }
    setChatError("Reading your file…");
    try {
      const extracted = await extractStudyFile(file);
      const text = clampText(extracted.replace(/\u0000/g, " ").trim());
      if (!text) throw new Error(`No selectable text was found in “${file.name}”. Scanned documents and images need OCR before upload.`);
      setData((current) => ({ ...current, title: file.name, notes: text, messages: [], cards: [], questions: [], mastery: {} }));
      setChatError("");
    } catch (error) {
      setChatError(error instanceof Error ? error.message : `Could not read “${file.name}”. Try exporting it to a common document format.`);
    }
  }

  async function sendMessage(event: FormEvent) {
    event.preventDefault();
    const trimmed = question.trim();
    if (!trimmed || busy) return;
    const userMessage: ChatMessage = { role: "user", text: trimmed };
    setData((current) => ({ ...current, messages: [...current.messages, userMessage] }));
    setQuestion(""); setBusy(true); setChatError("");
    try {
      const result = await api<{ answer: string; hint?: string }>("/study/chat", "POST", { question: trimmed, context: data.notes.slice(0, 24_000) });
      setData((current) => ({ ...current, messages: [...current.messages, { role: "assistant", text: result.answer, hint: result.hint }] }));
    } catch (error) {
      const message = error instanceof Error ? error.message : "The tutor couldn't answer just now.";
      setChatError(message);
    } finally { setBusy(false); }
  }

  async function generateQuestions() {
    const generated = await api<{ questions: StudyQuestion[] }>("/study/questions", "POST", {
      context: data.notes.slice(0, 24_000),
      focusTopics: topics.slice(0, 3).map(([topic]) => topic),
    });
    return generated.questions;
  }

  async function nextQuiz() {
    if (quizBusy) return;
    setChatError("");
    let current = data.questions;
    if (!current.length) {
      if (!data.notes.trim()) { setChatError("Add notes first so the quiz can use facts from your material."); return; }
      setQuizBusy(true);
      try { current = await generateQuestions(); }
      catch (error) { setChatError(error instanceof Error ? error.message : "Could not create a useful exam question from these notes."); return; }
      finally { setQuizBusy(false); }
    }
    const [next, ...remaining] = current;
    if (!next) { setChatError("No clear exam questions could be made from these notes. Add definitions, formulas, or factual explanations and try again."); return; }
    setQuiz(next); setQuizAnswer(""); setQuizChecked(false); setTab("quiz");
    setData((previous) => ({ ...previous, questions: remaining }));
  }

  async function generateFlashcards() {
    if (deckBusy || !data.notes.trim()) return;
    setDeckBusy(true); setChatError("");
    try {
      const questions = await generateQuestions();
      if (!questions.length) throw new Error("These notes do not have enough clear facts to make useful question-and-answer cards.");
      const cards = questions.map((item, index) => ({
        id: `${Date.now()}-${index}-${Math.random().toString(36).slice(2, 7)}`,
        front: item.question, back: item.answer, topic: item.topic, dueAt: Date.now(), interval: 0, ease: 2.5,
      }));
      setData((previous) => ({ ...previous, cards: [...previous.cards, ...cards] }));
    } catch (error) {
      setChatError(error instanceof Error ? error.message : "Could not make flashcards from these notes.");
    } finally { setDeckBusy(false); }
  }

  function scoreQuiz() {
    if (!quiz) return;
    const normalize = (value: string) => value.toLocaleLowerCase().replace(/[^\p{L}\p{N}]+/gu, " ").trim().replace(/\s+/g, " ");
    const correct = normalize(quizAnswer) === normalize(quiz.answer);
    setQuizChecked(true);
    setData((current) => {
      const previous = current.mastery[quiz.topic] ?? { correct: 0, total: 0 };
      return { ...current, mastery: { ...current.mastery, [quiz.topic]: { correct: previous.correct + Number(correct), total: previous.total + 1 } } };
    });
  }

  function reviewCard(rating: "again" | "hard" | "good" | "easy") {
    const card = dueCards[0]; if (!card) return;
    const nextInterval = rating === "again" ? 0 : rating === "hard" ? Math.max(1, Math.round(card.interval * 1.2) || 1) : rating === "good" ? Math.max(1, Math.round(card.interval * card.ease) || 1) : Math.max(3, Math.round(card.interval * (card.ease + 0.5)) || 3);
    const ease = Math.max(1.3, card.ease + (rating === "easy" ? 0.15 : rating === "hard" ? -0.15 : 0));
    const dueAt = Date.now() + (nextInterval === 0 ? 60_000 : nextInterval * 24 * 60 * 60 * 1000);
    setData((current) => ({ ...current, cards: current.cards.map((item) => item.id === card.id ? { ...item, dueAt, interval: nextInterval, ease } : item) }));
    setCardShown(false);
  }

  if (!ready) return <div className="empty">Loading your study desk…</div>;
  return <div className="study-page">
    <div className="page-heading study-heading">
      <span className="eyebrow">YOUR PERSONAL STUDY SPACE</span>
      <h1>Learn it. Then own it.</h1>
      <p>Bring your notes or syllabus. Your tutor, quizzes, and review cards will work from that material.</p>
    </div>

    <section className="study-source panel">
      <div className="study-source-head"><div><span className="study-icon"><BookOpenText size={20}/></span><div><strong>{data.title || "Add your study material"}</strong><small>Any file · auto-reads common documents · up to 20 MB</small></div></div>
        <input ref={fileRef} hidden type="file" accept="*/*" onChange={(event) => { void importFile(event.target.files?.[0]); event.currentTarget.value = ""; }}/>
        <Button variant="outline" onClick={() => fileRef.current?.click()}><Upload size={16}/> Upload notes</Button>
      </div>
      <textarea aria-label="Paste notes or syllabus" value={data.notes} maxLength={120_000} onChange={(event) => { const notes = event.target.value; setData((current) => ({ ...current, title: current.title || "Pasted study notes", notes, cards: [], questions: [], mastery: {} })); setQuiz(null); }} placeholder="Or paste notes / a syllabus here… The tutor will use only this material to answer." />
      <div className="study-source-foot"><span>{data.notes.length.toLocaleString()} / 120,000 characters · saved in this browser for your account</span>{data.notes && <button className="study-text-button" onClick={() => { setData((current) => ({ ...current, title: "", notes: "", cards: [], questions: [], mastery: {}, messages: [] })); setQuiz(null); }}>Clear material</button>}</div>
    </section>

    <div className="study-tabs" role="tablist" aria-label="Study tools">
      {([["chat", MessageCircle, "Tutor chat"], ["quiz", Brain, "Adaptive quiz"], ["cards", RotateCcw, "Flashcards"], ["heatmap", Flame, "Weak topics"]] as const).map(([id, Icon, label]) => <button key={id} role="tab" aria-selected={tab === id} className={tab === id ? "active" : ""} onClick={() => setTab(id)}><Icon size={17}/>{label}{id === "cards" && dueCards.length > 0 && <span className="study-count">{dueCards.length}</span>}</button>)}
    </div>

    {chatError && <div className="study-error" role="alert"><XCircle size={18}/>{chatError}<button aria-label="Dismiss" onClick={() => setChatError("")}><XCircle size={15}/></button></div>}

    {tab === "chat" && <section className="study-chat panel">
      <div className="study-panel-title"><span className="study-icon"><Bot size={21}/></span><div><strong>Playbit study tutor</strong><small>Answers grounded in your material · asks guiding questions</small></div><span className="study-online"><i/> READY</span></div>
      <div className="study-messages" ref={scrollRef}>
        {!data.messages.length && <div className="study-welcome"><Sparkles size={24}/><h2>What are you working on?</h2><p>Upload or paste your notes, then ask for an explanation, an example, or a hint.</p><div>{["Explain the main idea", "Give me a hint", "What should I remember?"] .map((suggestion) => <button key={suggestion} disabled={!data.notes} onClick={() => setQuestion(suggestion)}>{suggestion}<ChevronRight size={14}/></button>)}</div></div>}
        {data.messages.map((message, index) => <div className={`study-message ${message.role}`} key={`${index}-${message.text.slice(0, 12)}`}><span className="study-avatar">{message.role === "assistant" ? <Bot size={16}/> : "YOU"}</span><div><p>{message.text}</p>{message.hint && <aside><Lightbulb size={15}/><span><strong>Think about it</strong>{message.hint}</span></aside>}</div></div>)}
        {busy && <div className="study-message assistant"><span className="study-avatar"><Bot size={16}/></span><div className="study-thinking">Thinking from your notes…</div></div>}
      </div>
      <form className="study-composer" onSubmit={(event) => void sendMessage(event)}><input value={question} onChange={(event) => setQuestion(event.target.value)} placeholder={data.notes ? "Ask about your notes…" : "Add notes first to start a grounded chat"} disabled={!data.notes || busy}/><Button type="submit" disabled={!question.trim() || !data.notes || busy} aria-label="Send question"><Send size={17}/></Button></form>
      {!data.notes && <small className="study-privacy"><FileText size={13}/> Add material to enable note-grounded answers.</small>}
    </section>}

    {tab === "quiz" && <section className="panel study-tool-panel"><div className="study-panel-title"><span className="study-icon"><Brain size={21}/></span><div><strong>Exam-style practice</strong><small>Questions are grounded in your notes and adapt to weaker topics.</small></div></div>{quiz ? <div className="study-question"><span className="eyebrow">TOPIC · {quiz.topic}</span><h2>{quiz.question}</h2><form onSubmit={(event) => { event.preventDefault(); scoreQuiz(); }}><input autoComplete="off" value={quizAnswer} onChange={(event) => setQuizAnswer(event.target.value)} placeholder="Write your answer" disabled={quizChecked}/>{quizChecked ? <div className={`study-feedback ${quizAnswer.trim().toLocaleLowerCase() === quiz.answer.trim().toLocaleLowerCase() ? "correct" : "wrong"}`}>{quizAnswer.trim().toLocaleLowerCase() === quiz.answer.trim().toLocaleLowerCase() ? <Check size={17}/> : <Lightbulb size={17}/>}<span>{quizAnswer.trim().toLocaleLowerCase() === quiz.answer.trim().toLocaleLowerCase() ? "Correct." : `Review this answer: ${quiz.answer}`}<br/><small>{quiz.explanation}</small></span></div> : <Button type="submit" disabled={!quizAnswer.trim()}>Check answer <ChevronRight size={16}/></Button>}</form><details className="study-evidence"><summary>Show the supporting note</summary><p>{quiz.evidence}</p></details><Button variant="outline" disabled={quizBusy} onClick={() => void nextQuiz()}>{quizBusy ? "Preparing question…" : "Next question"} <ChevronRight size={16}/></Button></div> : <div className="study-empty"><Brain size={28}/><h2>Practice what you just studied.</h2><p>We’ll ask clear exam-style questions only when your notes contain enough factual material to support them.</p><Button onClick={() => void nextQuiz()} disabled={!data.notes || quizBusy}>{quizBusy ? "Preparing question…" : "Generate a question"} <ChevronRight size={17}/></Button></div>}</section>}

    {tab === "cards" && <section className="panel study-tool-panel"><div className="study-panel-title"><span className="study-icon"><RotateCcw size={21}/></span><div><strong>Spaced-repetition cards</strong><small>Rate recall to schedule the next review.</small></div><span className="study-online">{dueCards.length} DUE</span></div>{dueCards.length ? <div className="study-flashcard"><span className="eyebrow">{dueCards[0].topic}</span><h2>{cardShown ? dueCards[0].back : dueCards[0].front}</h2>{cardShown ? <div className="study-ratings">{([["again", "Again"], ["hard", "Hard"], ["good", "Good"], ["easy", "Easy"]] as const).map(([rating, label]) => <button key={rating} onClick={() => reviewCard(rating)}>{label}</button>)}</div> : <Button onClick={() => setCardShown(true)}>Show answer <ChevronRight size={16}/></Button>}<small><Clock3 size={13}/> {dueCards.length} card{dueCards.length === 1 ? "" : "s"} ready to review</small></div> : <div className="study-empty"><RotateCcw size={28}/><h2>{data.cards.length ? "All caught up." : "Build your first review deck."}</h2><p>{data.cards.length ? "Come back when the next card is due." : "Cards are written as real study questions with note-backed answers."}</p><Button variant="outline" disabled={!data.notes || deckBusy} onClick={() => void generateFlashcards()}>{deckBusy ? "Creating cards…" : data.cards.length ? "Add more study cards" : "Create flashcards"} <ChevronRight size={17}/></Button></div>}</section>}

    {tab === "heatmap" && <section className="panel study-tool-panel"><div className="study-panel-title"><span className="study-icon"><Flame size={21}/></span><div><strong>Your weak-topic heatmap</strong><small>Built from your answers to note-based quizzes.</small></div></div>{topics.length ? <div className="study-heatmap">{topics.map(([topic, score]) => { const accuracy = Math.round((score.correct / Math.max(1, score.total)) * 100); const level = accuracy < 40 ? "hot" : accuracy < 70 ? "warm" : "cool"; return <div className={`study-topic ${level}`} key={topic}><div><strong>{topic}</strong><span>{accuracy}%</span></div><div className="mastery-track"><div style={{ width: `${accuracy}%` }}/></div><small>{score.correct} correct of {score.total} · {level === "hot" ? "Review this next" : level === "warm" ? "Building confidence" : "Strong recall"}</small></div>; })}</div> : <div className="study-empty"><Flame size={28}/><h2>Your map starts with a few answers.</h2><p>Take adaptive quiz questions to see which concepts need another look.</p><Button onClick={nextQuiz} disabled={!data.notes}>Start a quick quiz <ChevronRight size={17}/></Button></div>}</section>}

    <p className="study-note"><Lightbulb size={15}/> Study material and review history are saved in this browser on this device. Chat answers use the configured Playbit tutor service.</p>
  </div>;
}
