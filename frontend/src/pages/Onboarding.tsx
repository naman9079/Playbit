import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowRight, Check } from "lucide-react";
import { subjects as catalog } from "../lib/catalog";
import { api } from "../services/api";
import { Avatar, ErrorBox } from "../components/common";
import { Button } from "../components/ui/button";
export default function Onboarding() {
  const navigate = useNavigate();
  const cache = useQueryClient();
  const [username, setUsername] = useState("");
  const [displayName, setName] = useState("");
  const [avatar, setAvatar] = useState(0);
  const [subjects, setSubjects] = useState(["math"]);
  const [proficiency, setProficiency] = useState("beginner");
  const [language, setLanguage] = useState("English");
  const save = useMutation({
    mutationFn: () =>
      api("/me", "PUT", {
        username,
        displayName,
        avatar,
        subjects,
        proficiency,
        language,
      }),
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: ["me"] });
      navigate("/play");
    },
  });
  return (
    <div className="narrow-page">
      <div className="page-heading">
        <span className="eyebrow">FIRST, MAKE IT YOURS</span>
        <h1>Meet your player one.</h1>
        <p>A few details. A whole lot of potential.</p>
      </div>
      <form
        className="panel onboarding"
        onSubmit={(e) => {
          e.preventDefault();
          save.mutate();
        }}
      >
        <label>Choose your avatar</label>
        <div className="avatar-picker">
          {Array.from({ length: 8 }, (_, i) => (
            <button
              type="button"
              key={i}
              aria-label={`Avatar ${i + 1}`}
              aria-pressed={avatar === i}
              className={avatar === i ? "selected" : ""}
              onClick={() => setAvatar(i)}
            >
              <Avatar seed={i} />
            </button>
          ))}
        </div>
        <div className="form-grid">
          <label>
            Display name
            <input
              required
              maxLength={40}
              value={displayName}
              onChange={(e) => setName(e.target.value)}
              placeholder="What should we call you?"
            />
          </label>
          <label>
            Username
            <input
              required
              pattern="[a-zA-Z0-9_]{3,20}"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="your_player_name"
            />
          </label>
        </div>
        <label>Choose your subjects</label>
        <div className="selection-grid">
          {catalog.map((s) => (
            <button
              type="button"
              key={s.id}
              aria-pressed={subjects.includes(s.id)}
              className={`selection-card ${subjects.includes(s.id) ? "selected" : ""}`}
              onClick={() =>
                setSubjects((prev) =>
                  prev.includes(s.id)
                    ? prev.filter((id) => id !== s.id)
                    : [...prev, s.id],
                )
              }
            >
              <s.icon size={25} />
              <strong>{s.name}</strong>
              {subjects.includes(s.id) && <Check size={16} />}
            </button>
          ))}
        </div>
        <div className="form-grid">
          <label>
            Your starting confidence
            <select
              value={proficiency}
              onChange={(e) => setProficiency(e.target.value)}
            >
              <option value="beginner">Beginner — building foundations</option>
              <option value="intermediate">
                Intermediate — finding my flow
              </option>
              <option value="advanced">Advanced — ready for a challenge</option>
            </select>
          </label>
          <label>
            Preferred language
            <select
              value={language}
              onChange={(e) => setLanguage(e.target.value)}
            >
              <option>English</option>
              <option>Hindi</option>
            </select>
            <small>
              Question bank and verified explanations currently use English.
            </small>
          </label>
        </div>
        <p className="muted">
          Next: a five-question placement in each selected subject. Your in-game
          rating is a starting point, not a measure of your potential.
        </p>
        <ErrorBox error={save.error} />
        <Button disabled={!subjects.length || save.isPending}>
          Build my player profile <ArrowRight size={18} />
        </Button>
      </form>
    </div>
  );
}
