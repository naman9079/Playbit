import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import {
  BookOpenText, Bot, Brain, ChevronRight, FileText,
  Flame, Lightbulb, MessageCircle, Plus, RotateCcw, Send, Sparkles,
  FileImage, X, XCircle,
} from "lucide-react";
import { useSession } from "../stores/session";
import { api, uploadStudyImage } from "../services/api";
import { Button } from "../components/ui/button";
import { extractStudyFile } from "../lib/study-import";

type ChatMessage = { role: "user" | "assistant"; text: string; hint?: string };
type TopicCard = { id: string; name: string; points: string[] };
type StudyQuestion = { question: string; sourceTitle: string; sourceUrl: string; sourceDomain: string };
type StudySource = { title: string; url: string; domain: string; snippet: string };
type Mastery = Record<string, { correct: number; total: number }>;
type StudyData = { title: string; notes: string; messages: ChatMessage[]; topicCards: TopicCard[]; questions: StudyQuestion[]; questionHistory: StudyQuestion[]; onlineSources: StudySource[]; onlineMessage: string; noteChunkIndex: number; mastery: Mastery };
const empty: StudyData = { title: "", notes: "", messages: [], topicCards: [], questions: [], questionHistory: [], onlineSources: [], onlineMessage: "", noteChunkIndex: 0, mastery: {} };
const storeKey = (userId: string) => `playbit-study-v2-${userId}`;
const legacyStoreKey = (userId: string) => `playbit-study-v1-${userId}`;
const clampText = (value: string) => value.slice(0, 120_000);

function suggestSearchQuery(title: string, notes: string) {
  const filename = title.replace(/\.[^.]+$/, "").replace(/[_-]+/g, " ").replace(/\s+/g, " ").trim();
  const heading = notes.split(/\r?\n+/).map((line) => line.replace(/^[\s#\d.)-]+/, "").trim())
    .find((line) => line.length >= 4 && line.length <= 80 && /[a-z]/i.test(line) && !/^page\s+\d+$/i.test(line));
  return [filename, heading].filter(Boolean).join(" ").slice(0, 180);
}

function splitStudyNotes(notes: string, maxLength = 16_000) {
  const chunks: string[] = [];
  let remaining = notes.trim();
  while (remaining.length > maxLength) {
    let cut = remaining.lastIndexOf("\n", maxLength);
    if (cut < maxLength * 0.55) cut = remaining.lastIndexOf(". ", maxLength);
    if (cut < maxLength * 0.55) cut = remaining.lastIndexOf(" ", maxLength);
    if (cut < maxLength * 0.55) cut = maxLength;
    chunks.push(remaining.slice(0, cut).trim());
    remaining = remaining.slice(cut).trim();
  }
  if (remaining) chunks.push(remaining);
  return chunks;
}

function topicKey(value: string) {
  return value.toLocaleLowerCase().replace(/[^\p{L}\p{N}]+/gu, " ").trim();
}

export default function Study() {
  const session = useSession((state) => state.session);
  const userId = session?.user.id;
  const [data, setData] = useState<StudyData>(empty);
  const [ready, setReady] = useState(false);
  const [tab, setTab] = useState<"chat" | "quiz" | "cards" | "heatmap">("chat");
  const [question, setQuestion] = useState("");
  const [searchQuery, setSearchQuery] = useState("");
  const [busy, setBusy] = useState(false);
  const [chatError, setChatError] = useState("");
  const [quiz, setQuiz] = useState<StudyQuestion | null>(null);
  const [quizBusy, setQuizBusy] = useState(false);
  const [topicBusy, setTopicBusy] = useState(false);
  const [topicProgress, setTopicProgress] = useState("");
  const [attachMenuOpen, setAttachMenuOpen] = useState(false);
  const [showNotesEditor, setShowNotesEditor] = useState(false);
  const [notesDraft, setNotesDraft] = useState("");
  const [fileStatus, setFileStatus] = useState("");
  const fileRef = useRef<HTMLInputElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!userId) { setData(empty); setSearchQuery(""); setReady(true); return; }
    try {
      const saved = localStorage.getItem(storeKey(userId));
      if (saved) {
        const parsed = JSON.parse(saved) as Partial<StudyData>;
        const isSourced = (item: any) => typeof item?.sourceUrl === "string" && item.sourceUrl.startsWith("https://");
        setData({ ...empty, ...parsed, topicCards: parsed.topicCards ?? [], questions: (parsed.questions ?? []).filter(isSourced), questionHistory: (parsed.questionHistory ?? []).filter(isSourced) });
        setSearchQuery(suggestSearchQuery(parsed.title ?? "", parsed.notes ?? ""));
      }
      else {
        const legacy = localStorage.getItem(legacyStoreKey(userId));
        const migrated = legacy ? { ...empty, ...JSON.parse(legacy), topicCards: [], questions: [], mastery: {} } : empty;
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

  const topics = useMemo(() => Object.entries(data.mastery).sort((a, b) => {
    const scoreA = a[1].total ? a[1].correct / a[1].total : 0;
    const scoreB = b[1].total ? b[1].correct / b[1].total : 0;
    return scoreA - scoreB;
  }), [data.mastery]);

  async function importFile(file?: File) {
    if (!file) return;
    const isScreenshot = file.type.startsWith("image/") || /\.(png|jpe?g|webp)$/i.test(file.name);
    const imageLimit = 15 * 1024 * 1024;
    if (isScreenshot && file.size > imageLimit) { setChatError("Screenshots must be 15 MB or smaller. Resize the image and try again."); return; }
    if (file.size > 20 * 1024 * 1024) { setChatError("That file is over 20 MB. Reduce its size or split it into smaller files."); return; }
    setChatError("");
    setFileStatus(isScreenshot ? "Reading text from your screenshot…" : "Reading your file…");
    try {
      const imageFile = isScreenshot && !file.type.startsWith("image/")
        ? new File([file], file.name, { type: /\.png$/i.test(file.name) ? "image/png" : /\.webp$/i.test(file.name) ? "image/webp" : "image/jpeg" })
        : file;
      const extracted = isScreenshot
        ? (await uploadStudyImage<{ text: string }>(imageFile)).text
        : await extractStudyFile(file);
      const text = clampText(extracted.replace(/\u0000/g, " ").trim());
      if (!text) throw new Error(`No readable text was found in “${file.name}”. For scanned pages, upload a clear PNG or JPG screenshot.`);
      setData((current) => {
        const combined = clampText([current.notes.trim(), current.notes.trim() ? `\n\n--- ${file.name} ---\n` : "", text].join(""));
        const title = current.title ? `${current.title}, ${file.name}` : file.name;
        return { ...current, title, notes: combined, messages: [...current.messages, { role: "assistant", text: `I’ve added ${file.name} to your study material. Ask me anything about it.` }], topicCards: [], questions: [], questionHistory: [], onlineSources: [], onlineMessage: "", noteChunkIndex: 0, mastery: {} };
      });
      setSearchQuery((current) => current || suggestSearchQuery(file.name, text));
      setFileStatus("");
      setAttachMenuOpen(false);
      setShowNotesEditor(false);
    } catch (error) {
      setFileStatus("");
      setChatError(error instanceof Error ? error.message : `Could not read “${file.name}”. Try exporting it to a common document format.`);
    }
  }

  function savePastedNotes() {
    const notes = clampText(notesDraft.trim());
    if (!notes) { setChatError("Paste some notes before adding them to your study material."); return; }
    const title = data.title ? `${data.title}, pasted notes` : "Pasted study notes";
    setData((current) => ({
      ...current,
      title,
      notes: clampText([current.notes.trim(), current.notes.trim() ? "\n\n--- Pasted notes ---\n" : "", notes].join("")),
      messages: [...current.messages, { role: "assistant", text: "I’ve added your pasted notes. Ask me about any part of the material." }],
      topicCards: [], questions: [], questionHistory: [], onlineSources: [], onlineMessage: "", noteChunkIndex: 0, mastery: {},
    }));
    setSearchQuery((current) => current || suggestSearchQuery(title, notes));
    setNotesDraft("");
    setShowNotesEditor(false);
    setAttachMenuOpen(false);
    setChatError("");
  }

  function clearStudyMaterial() {
    setData((current) => ({ ...current, title: "", notes: "", messages: [], topicCards: [], questions: [], questionHistory: [], onlineSources: [], onlineMessage: "", noteChunkIndex: 0, mastery: {} }));
    setSearchQuery("");
    setQuiz(null);
    setChatError("");
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
    if (!data.notes.trim()) throw new Error("Upload a chapter PDF or notes first.");
    const query = searchQuery.trim() || suggestSearchQuery(data.title, data.notes);
    if (!query) throw new Error("Enter the chapter name or topic to search for existing questions.");
    const seenQuestions = data.questionHistory.map((item) => item.question).slice(-40);
    const result = await api<{ questions: StudyQuestion[]; sources: StudySource[]; message?: string }>("/study/online-questions", "POST", { query, seenQuestions });
    setData((previous) => ({ ...previous, onlineSources: result.sources ?? [], onlineMessage: result.message ?? "" }));
    const seen = new Set(seenQuestions.map((item) => item.toLocaleLowerCase().replace(/[^\p{L}\p{N}]+/gu, " ").trim()));
    const unique = result.questions.filter((item) => {
      const key = item.question.toLocaleLowerCase().replace(/[^\p{L}\p{N}]+/gu, " ").trim();
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
    if (!unique.length) throw new Error(result.message || "No new question text was found. Edit the chapter search terms or open the listed sources.");
    setData((previous) => ({ ...previous, questions: unique }));
    return unique;
  }

  async function nextQuiz() {
    if (quizBusy) return;
    setChatError("");
    let current = data.questions;
    if (!current.length) {
      if (!data.notes.trim()) { setChatError("Upload a chapter PDF or notes first."); return; }
      setQuizBusy(true);
      try { current = await generateQuestions(); }
      catch (error) { setChatError(error instanceof Error ? error.message : "Could not find questions from online sources."); return; }
      finally { setQuizBusy(false); }
    }
    const [next, ...remaining] = current;
    if (!next) { setChatError("No online question text was found. Try a shorter chapter name or a different search term."); return; }
    setQuiz(next); setTab("quiz");
    setData((previous) => ({ ...previous, questions: remaining, questionHistory: [...previous.questionHistory, next].slice(-40) }));
  }

  async function searchOnlineQuestions() {
    if (quizBusy) return;
    setChatError(""); setQuiz(null); setData((current) => ({ ...current, questions: [] })); setQuizBusy(true);
    try {
      const found = await generateQuestions();
      const [first, ...remaining] = found;
      if (!first) throw new Error("No question text was found in those search results.");
      setQuiz(first); setTab("quiz");
      setData((previous) => ({ ...previous, questions: remaining, questionHistory: [...previous.questionHistory, first].slice(-40) }));
    } catch (error) {
      setChatError(error instanceof Error ? error.message : "Could not find question text in online sources.");
    } finally { setQuizBusy(false); }
  }

  async function generateTopicCards() {
    if (topicBusy || !data.notes.trim()) return;
    const chunks = splitStudyNotes(data.notes);
    setTopicBusy(true); setChatError(""); setTopicProgress(`Reading ${chunks.length} section${chunks.length === 1 ? "" : "s"} of your material…`);
    setData((current) => ({ ...current, topicCards: [] }));
    let summaryCount = 0;
    try {
      const batchSize = 4;
      let lastFailure: unknown;
      for (let index = 0; index < chunks.length; index += batchSize) {
        const batch = chunks.slice(index, index + batchSize);
        setTopicProgress(`Explaining sections ${index + 1}–${index + batch.length} of ${chunks.length}…`);
        const results = await Promise.allSettled(batch.map((context) => api<{ topics: { name: string; points: string[] }[] }>("/study/topic-notes", "POST", { title: data.title || "Uploaded study material", context })));
        const found = results.flatMap((result) => {
          if (result.status === "rejected") { lastFailure = result.reason; return []; }
          return result.value.topics ?? [];
        }).filter((topic) => topic.name && topic.points?.length);
        summaryCount += found.length;
        setData((previous) => {
          const byTopic = new Map(previous.topicCards.map((topic) => [topicKey(topic.name), topic]));
          for (const topic of found) {
            const key = topicKey(topic.name);
            const existing = byTopic.get(key);
            const points = [...new Set([...(existing?.points ?? []), ...topic.points])].slice(0, 6);
            byTopic.set(key, { id: key, name: existing?.name ?? topic.name, points });
          }
          return { ...previous, topicCards: [...byTopic.values()] };
        });
      }
      if (!summaryCount) {
        if (lastFailure instanceof Error) throw lastFailure;
        throw new Error("No clear topics could be found in this text. Check that the PDF has selectable text, then try again.");
      }
      setTopicProgress("");
      setTab("cards");
    } catch (error) {
      setTopicProgress("");
      setChatError(error instanceof Error ? error.message : "Could not explain the topics in this material.");
    } finally { setTopicBusy(false); }
  }

  if (!ready) return <div className="empty">Loading your study desk…</div>;
  return <div className="study-page">
    <div className="page-heading study-heading">
      <span className="eyebrow">YOUR AI STUDY COMPANION</span>
      <h1>Study with Playbit</h1>
      <p>Ask questions about your material, then turn what you learn into practice.</p>
    </div>

    <div className="study-tabs" role="tablist" aria-label="Study tools">
      {([["chat", MessageCircle, "Tutor chat"], ["quiz", Brain, "Adaptive quiz"], ["cards", RotateCcw, "Flashcards"], ["heatmap", Flame, "Weak topics"]] as const).map(([id, Icon, label]) => <button key={id} role="tab" aria-selected={tab === id} className={tab === id ? "active" : ""} onClick={() => { setTab(id); setChatError(""); }}><Icon size={17}/>{label}{id === "cards" && data.topicCards.length > 0 && <span className="study-count">{data.topicCards.length}</span>}</button>)}
    </div>

    {chatError && <div className="study-error" role="alert"><XCircle size={18}/>{chatError}<button aria-label="Dismiss" onClick={() => setChatError("")}><XCircle size={15}/></button></div>}

    {tab === "chat" && <section className="study-chat study-chat-workspace panel">
      <div className="study-panel-title"><span className="study-icon"><Bot size={21}/></span><div><strong>Playbit study tutor</strong><small>Ask about your notes or request a simple explanation</small></div><span className="study-online"><i/> READY</span></div>
      {data.notes && <div className="study-material-chip"><span className="study-material-icon"><BookOpenText size={16}/></span><span><strong>{data.title || "Study material"}</strong><small>{data.notes.length.toLocaleString()} characters added to this chat</small></span><button type="button" onClick={clearStudyMaterial} aria-label="Remove study material"><X size={16}/></button></div>}
      <div className="study-messages" ref={scrollRef}>
        {!data.messages.length && <div className="study-welcome"><Sparkles size={24}/><h2>What are you studying today?</h2><p>Add a PDF, document, screenshot, or paste notes. Then ask for a clear explanation or a hint.</p><div>{["Explain the main idea", "Give me a hint", "What should I remember?"] .map((suggestion) => <button key={suggestion} disabled={!data.notes} onClick={() => setQuestion(suggestion)}>{suggestion}<ChevronRight size={14}/></button>)}</div></div>}
        {data.messages.map((message, index) => <div className={`study-message ${message.role}`} key={`${index}-${message.text.slice(0, 12)}`}><span className="study-avatar">{message.role === "assistant" ? <Bot size={16}/> : "YOU"}</span><div><p>{message.text}</p>{message.hint && <aside><Lightbulb size={15}/><span><strong>Think about it</strong>{message.hint}</span></aside>}</div></div>)}
        {busy && <div className="study-message assistant"><span className="study-avatar"><Bot size={16}/></span><div className="study-thinking">Thinking from your notes…</div></div>}
      </div>
      {showNotesEditor && <div className="study-paste-editor"><textarea aria-label="Paste notes or syllabus" value={notesDraft} maxLength={120_000} onChange={(event) => setNotesDraft(event.target.value)} placeholder="Paste your notes or syllabus here…"/><div><small>{notesDraft.length.toLocaleString()} characters</small><Button type="button" variant="outline" onClick={() => setShowNotesEditor(false)}>Cancel</Button><Button type="button" onClick={savePastedNotes}>Add notes</Button></div></div>}
      <div className="study-composer-wrap">
        {attachMenuOpen && <div className="study-attach-menu" role="menu"><button type="button" role="menuitem" onClick={() => fileRef.current?.click()}><FileImage size={17}/><span>Upload a file<small>PDF, DOCX, PPTX, XLSX, text, PNG, JPG, WebP</small></span></button><button type="button" role="menuitem" onClick={() => { setNotesDraft(data.notes); setShowNotesEditor(true); setAttachMenuOpen(false); }}><FileText size={17}/><span>Paste notes<small>Add copied text or a syllabus</small></span></button></div>}
        <input ref={fileRef} hidden type="file" accept="*/*" onChange={(event) => { void importFile(event.target.files?.[0]); event.currentTarget.value = ""; }}/>
        <form className="study-composer" onSubmit={(event) => void sendMessage(event)}>
          <button type="button" className="study-plus-button" aria-label="Add study material" aria-haspopup="menu" aria-expanded={attachMenuOpen} disabled={Boolean(fileStatus)} onClick={() => setAttachMenuOpen((open) => !open)}><Plus size={22}/></button>
          <input value={question} onChange={(event) => setQuestion(event.target.value)} placeholder={data.notes ? "Ask anything about your material…" : "Add a file or notes to start your study chat"} disabled={!data.notes || busy}/>
          <Button type="submit" disabled={!question.trim() || !data.notes || busy} aria-label="Send question"><Send size={17}/></Button>
        </form>
      </div>
      {fileStatus && <small className="study-upload-status" role="status">{fileStatus}</small>}
      <small className="study-privacy"><FileText size={13}/> Uploads stay in this browser; extracted text is sent to Playbit’s backend for study help. Images are read by Gemini.</small>
    </section>}

    {tab === "quiz" && <section className="panel study-tool-panel"><div className="study-panel-title"><span className="study-icon"><Brain size={21}/></span><div><strong>Questions from the web</strong><small>Exact question excerpts from public pages, with source links.</small></div></div><p className="study-search-note">Playbit searches existing exam and textbook pages. It does not generate or rewrite questions from your PDF.</p><form className="study-online-search" onSubmit={(event) => { event.preventDefault(); void searchOnlineQuestions(); }}><input aria-label="Chapter or topic to search" value={searchQuery} onChange={(event) => setSearchQuery(event.target.value)} placeholder="Chapter or topic, for example Class 8 · Linear Equations" maxLength={180}/><Button type="submit" disabled={!data.notes || !searchQuery.trim() || quizBusy}>{quizBusy ? "Searching the web…" : "Find questions"} <ChevronRight size={16}/></Button></form>{quiz ? <div className="study-question"><span className="eyebrow">FOUND ON {quiz.sourceDomain}</span><h2>{quiz.question}</h2><p className="study-question-source">Source: <a href={quiz.sourceUrl} target="_blank" rel="noreferrer">{quiz.sourceTitle}</a></p><p className="study-search-note">This is question text found on the linked page. Open it for the original context, answer key, and exam instructions.</p><Button variant="outline" disabled={quizBusy} onClick={() => void nextQuiz()}>{quizBusy ? "Searching the web…" : "Next question"} <ChevronRight size={16}/></Button></div> : <><div className="study-empty"><Brain size={28}/><h2>Find questions from your chapter.</h2><p>{data.onlineMessage || "Upload a chapter PDF, check the detected topic, and search for questions already published online."}</p><Button onClick={() => void searchOnlineQuestions()} disabled={!data.notes || quizBusy || !searchQuery.trim()}>{quizBusy ? "Searching the web…" : "Search online questions"} <ChevronRight size={17}/></Button></div>{data.onlineSources.length > 0 && <div className="study-online-sources"><strong>Search results</strong>{data.onlineSources.map((source) => <a key={source.url} href={source.url} target="_blank" rel="noreferrer"><span>{source.title}</span><small>{source.domain} · {source.snippet}</small></a>)}</div>}</>}</section>}

    {tab === "cards" && <section className="panel study-tool-panel"><div className="study-panel-title"><span className="study-icon"><RotateCcw size={21}/></span><div><strong>Simple topic cards</strong><small>Short explanations from the topics in your uploaded material.</small></div><span className="study-online">{data.topicCards.length} TOPICS</span></div><div className="study-topic-actions"><p>We read the material in sections and explain each topic in a few easy points. Explanations stay grounded in your PDF or notes.</p><Button variant="outline" disabled={!data.notes || topicBusy} onClick={() => void generateTopicCards()}>{topicBusy ? "Making topic cards…" : data.topicCards.length ? "Refresh explanations" : "Explain every topic"} <ChevronRight size={17}/></Button></div>{topicBusy && <p className="study-topic-progress" role="status">{topicProgress}</p>}{data.topicCards.length ? <div className="study-topic-cards">{data.topicCards.map((card, index) => <article className="study-topic-card" key={card.id}><span className="eyebrow">TOPIC {index + 1}</span><h2>{card.name}</h2><ul>{card.points.map((point, pointIndex) => <li key={pointIndex}>{point}</li>)}</ul></article>)}</div> : <div className="study-empty"><RotateCcw size={28}/><h2>Turn your chapter into easy notes.</h2><p>{data.notes ? "Create one simple explanation card for each topic found in your uploaded material." : "Upload a subject or chapter PDF first, then make short, easy-to-understand topic cards from it."}</p><Button disabled={!data.notes || topicBusy} onClick={() => void generateTopicCards()}>{topicBusy ? "Making topic cards…" : "Explain every topic"} <ChevronRight size={17}/></Button></div>}</section>}

    {tab === "heatmap" && <section className="panel study-tool-panel"><div className="study-panel-title"><span className="study-icon"><Flame size={21}/></span><div><strong>Your weak-topic heatmap</strong><small>Built from answers you previously checked in Playbit.</small></div></div>{topics.length ? <div className="study-heatmap">{topics.map(([topic, score]) => { const accuracy = Math.round((score.correct / Math.max(1, score.total)) * 100); const level = accuracy < 40 ? "hot" : accuracy < 70 ? "warm" : "cool"; return <div className={`study-topic ${level}`} key={topic}><div><strong>{topic}</strong><span>{accuracy}%</span></div><div className="mastery-track"><div style={{ width: `${accuracy}%` }}/></div><small>{score.correct} correct of {score.total} · {level === "hot" ? "Review this next" : level === "warm" ? "Building confidence" : "Strong recall"}</small></div>; })}</div> : <div className="study-empty"><Flame size={28}/><h2>No checked answers yet.</h2><p>Online questions link to their original answer source; Playbit does not invent an answer key or score your response.</p><Button onClick={() => setTab("quiz")} disabled={!data.notes}>Find online questions <ChevronRight size={17}/></Button></div>}</section>}

    <p className="study-note"><Lightbulb size={15}/> Your uploaded file stays in this browser. Extracted text is sent to Playbit’s backend and Google Gemini to create explanations. Google may use data submitted on its free tier to improve its products. Online exam questions link to their source pages.</p>
  </div>;
}
