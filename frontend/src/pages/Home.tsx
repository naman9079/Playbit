import { Link } from "react-router-dom";
import { motion } from "framer-motion";
import {
  ArrowRight,
  ArrowUpRight,
  Swords,
  Bot,
  Target,
  Zap,
  ShieldCheck,
  Flame,
  Sparkles,
  MoveUpRight,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useSession, usePreferences } from "../stores/session";
import { api } from "../services/api";
import { subjects } from "../lib/catalog";
import { Avatar, SectionTitle, ActionLink } from "../components/common";
import { Button } from "../components/ui/button";
export default function Home() {
  const session = useSession((s) => s.session);
  const { data: me } = useQuery({
    queryKey: ["me"],
    queryFn: () => api("/me"),
    enabled: !!session,
  });
  const setSubject = usePreferences((s) => s.setSubject);
  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      className="home"
    >
      <div className="welcome-row">
        <div>
          <div className="eyebrow">LESS SCROLLING. MORE LEVELING UP.</div>
          <h1>
            {me
              ? `Good to see you, ${me.display_name.split(" ")[0]}.`
              : "Big brain energy. Welcome in."}{" "}
            <span className="wave">✦</span>
          </h1>
          <p>A sharper mind is a game away. What will you conquer today?</p>
        </div>
        <span className="edition">
          <span />
          THE PLAYBIT EXPERIENCE
        </span>
      </div>
      <section className="hero">
        <div className="hero-grid" />
        <div className="hero-copy">
          <span className="hero-kicker">
            <span className="live-dot" /> YOUR MIND. YOUR COMPETITIVE EDGE.
          </span>
          <h2>
            Play smart.
            <br />
            Compete hard.
            <br />
            <span>Level up.</span>
          </h2>
          <p>
            Turn “I think I can” into “watch me.”
            <br />
            Quick brain battles. Real progress. Your next win.
          </p>
          <div className="hero-actions">
            <Button asChild className="button-light">
              <Link to="/play">
                Enter the arena <ArrowUpRight size={20} />
              </Link>
            </Button>
            <span>
              5 questions.
              <br />
              <strong>Countless aha! moments.</strong>
            </span>
          </div>
          <div className="hero-proof">
            <ShieldCheck size={15} /> Skill-based play <span>•</span>
            <Sparkles size={15} /> Personalized learning
          </div>
        </div>
        <div
          className="hero-art"
          aria-label="Original Playbit game illustration"
        >
          <div className="orbit orbit-one" />
          <div className="orbit orbit-two" />
          <div className="float-symbol symbol-one">÷</div>
          <div className="float-symbol symbol-two">x²</div>
          <div className="float-symbol symbol-three">+</div>
          <div className="art-star star-one">✳</div>
          <div className="art-star star-two">✦</div>
          <div className="battle-card">
            <div className="battle-card-top">
              <span>THE DAILY MINDSET</span>
              <Zap size={17} />
            </div>
            <div className="mascot">
              <div className="mascot-antenna" />
              <div className="mascot-body">
                <div className="mascot-face">
                  <i />
                  <i />
                </div>
                <div className="mascot-mouth" />
              </div>
              <div className="mascot-shadow" />
            </div>
            <h3>
              Small games.
              <br />
              Big brain gains.
            </h3>
            <div className="battle-card-bottom">
              <span>PLAYER ONE</span>
              <span>
                YOU <ArrowUpRight size={13} />
              </span>
            </div>
          </div>
          <div className="floating-tag">
            <span className="tag-icon">
              <Flame size={19} />
            </span>
            <div>
              <strong>Meet your next level</strong>
              <span>One good challenge at a time</span>
            </div>
          </div>
        </div>
      </section>
      <div className="home-columns">
        <div>
          <SectionTitle
            title="Pick your playground"
            eyebrow="01 / FIND YOUR FOCUS"
          >
            <span className="subtle-label">
              3 subjects. So many possibilities.
            </span>
          </SectionTitle>
          <div className="subject-grid">
            {subjects.map((s, i) => {
              const Icon = s.icon;
              const rating = me?.ratings?.find(
                (r: any) => r.subject_id === s.id,
              );
              return (
                <Link
                  to="/play"
                  onClick={() => setSubject(s.id)}
                  className={`subject-card ${s.color}`}
                  key={s.id}
                >
                  <div className="subject-card-top">
                    <span className="subject-icon">
                      <Icon size={23} />
                    </span>
                    <ArrowUpRight className="card-arrow" size={20} />
                  </div>
                  <div className="subject-art">
                    {s.symbol}
                    <span>{i === 0 ? "∑" : i === 1 ? "1/2" : "◈"}</span>
                  </div>
                  <h3>{s.name}</h3>
                  <p>{s.description}</p>
                  <div className="subject-tags">
                    {s.topics.map((t) => (
                      <span key={t}>{t}</span>
                    ))}
                  </div>
                  <div className="subject-bottom">
                    <span>
                      {rating?.assessed
                        ? `${rating.rating} rating${rating.provisional ? " · provisional" : ""}`
                        : "Discover your starting level"}
                    </span>
                    <ArrowRight size={15} />
                  </div>
                </Link>
              );
            })}
          </div>
          <SectionTitle
            title="Your game. Your pace."
            eyebrow="02 / CHOOSE YOUR CHALLENGE"
          >
            <ActionLink to="/play">All modes</ActionLink>
          </SectionTitle>
          <div className="quick-modes">
            {[
              {
                icon: Bot,
                title: "Battle a bot",
                label: "WARM UP. GO ALL OUT.",
                text: "A worthy rival, ready whenever you are.",
                mode: "bot",
                color: "purple",
              },
              {
                icon: Swords,
                title: "Challenge a friend",
                label: "GOOD FRIENDS. GREAT RIVALS.",
                text: "Share a room. Settle it in the arena.",
                mode: "friend",
                color: "cyan",
              },
              {
                icon: Target,
                title: "Find your focus",
                label: "NO PRESSURE. JUST PROGRESS.",
                text: "Practice your way to your next breakthrough.",
                mode: "solo",
                color: "lime",
              },
            ].map((m) => (
              <Link
                to={`/play?mode=${m.mode}`}
                key={m.mode}
                className={`quick-mode ${m.color}`}
              >
                <m.icon size={25} />
                <div>
                  <small>{m.label}</small>
                  <h3>{m.title}</h3>
                  <p>{m.text}</p>
                </div>
                <ArrowUpRight size={18} />
              </Link>
            ))}
          </div>
        </div>
        <aside className="home-aside">
          <div className="journey-card">
            <span className="eyebrow">
              <Sparkles size={14} /> THE SMARTER WAY TO PLAY
            </span>
            <h3>
              Every game is
              <br />a step forward.
            </h3>
            <p>
              The score is just the start. Find out what clicked, what didn’t,
              and what to try next.
            </p>
            <div className="learning-path">
              {[
                ["01", "Play a quick round", "Put your skills to the test."],
                [
                  "02",
                  "Find your aha! moment",
                  "Get step-by-step explanations.",
                ],
                [
                  "03",
                  "Make your comeback",
                  "Practice a personal recovery challenge.",
                ],
              ].map(([n, title, text]) => (
                <div key={n}>
                  <span>{n}</span>
                  <section>
                    <strong>{title}</strong>
                    <p>{text}</p>
                  </section>
                </div>
              ))}
            </div>
            <Button asChild variant="outline">
              <Link to={session ? "/progress" : "/auth"}>
                Meet your learning journey <MoveUpRight size={16} />
              </Link>
            </Button>
          </div>
          <div className="player-card">
            <div className="player-card-top">
              <span className="eyebrow">YOUR PLAYER CARD</span>
              <Zap size={16} />
            </div>
            <div className="player-card-user">
              <Avatar seed={me?.avatar_url} size="lg" />
              <div>
                <h3>{me?.display_name || "A new contender"}</h3>
                <p>
                  {me
                    ? `@${me.username}`
                    : "The next level has your name on it."}
                </p>
              </div>
            </div>
            <div className="player-card-stats">
              <div>
                <strong>{me?.level || "—"}</strong>
                <span>LEVEL</span>
              </div>
              <div>
                <strong>{me?.total_xp ?? "—"}</strong>
                <span>TOTAL XP</span>
              </div>
              <div>
                <strong>{me?.stats?.wins ?? "—"}</strong>
                <span>WINS</span>
              </div>
            </div>
            <Link to={session ? "/profile" : "/auth"}>
              {session ? "View your profile" : "Create your player profile"}{" "}
              <ArrowRight size={16} />
            </Link>
          </div>
        </aside>
      </div>
      <div className="bottom-banner">
        <span className="banner-icon">✳</span>
        <p>
          You don’t have to be the best to begin.
          <br />
          <strong>You just have to press play.</strong>
        </p>
        <Button asChild variant="ghost">
          <Link to="/play">
            Your next move <ArrowUpRight size={18} />
          </Link>
        </Button>
      </div>
    </motion.div>
  );
}
