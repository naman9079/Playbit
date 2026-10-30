import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Trophy, Crown, ArrowLeft, ArrowRight } from "lucide-react";
import { api } from "../services/api";
import { subjects } from "../lib/catalog";
import { useSession } from "../stores/session";
import { Avatar, ErrorBox, Loading, Empty } from "../components/common";
import { Button } from "../components/ui/button";
export default function Leaderboards() {
  const [subject, setSubject] = useState("overall");
  const [page, setPage] = useState(0);
  const user = useSession((s) => s.session?.user.id);
  const {
    data: b,
    isPending,
    error,
  } = useQuery({
    queryKey: ["leaderboard", subject, page],
    queryFn: () =>
      api(
        `/leaderboards/${subject === "overall" ? "overall" : `subject/${subject}`}?page=${page}`,
      ),
  });
  return (
    <div>
      <div className="page-heading leaderboard-heading">
        <div>
          <span className="eyebrow">A LITTLE FRIENDLY COMPETITION</span>
          <h1>Meet the pace setters.</h1>
          <p>Earn your place. Celebrate the climb.</p>
        </div>
        <Trophy size={64} />
      </div>
      <div className="tabs">
        {[{ id: "overall", name: "Overall XP" }, ...subjects].map((s) => (
          <button
            className={subject === s.id ? "active" : ""}
            key={s.id}
            onClick={() => {
              setSubject(s.id);
              setPage(0);
            }}
          >
            {s.name}
          </button>
        ))}
      </div>
      <p className="muted">
        {subject === "overall"
          ? "Real players, ranked by earned XP. Bot XP is limited to 10 rewarded matches per UTC day."
          : "Subject rankings require 5 ranked human matches. Placement ratings are provisional until then."}
      </p>
      {isPending ? (
        <Loading />
      ) : error ? (
        <ErrorBox error={error} />
      ) : (
        b && (
          <>
            {page === 0 && b.entries.length > 0 && (
              <div className="podium">
                {b.entries.slice(0, 3).map((p: any, i: number) => (
                  <div className={`podium-player place-${i + 1}`} key={p.id}>
                    <span className="podium-crown">
                      {i === 0 ? <Crown size={28} /> : <span>#{i + 1}</span>}
                    </span>
                    <Avatar seed={p.avatar_url} size="lg" />
                    <h3>{p.display_name}</h3>
                    <p>@{p.username}</p>
                    <strong>
                      {subject === "overall" ? p.total_xp : p.rating}{" "}
                      <small>{subject === "overall" ? "XP" : "RATING"}</small>
                    </strong>
                  </div>
                ))}
              </div>
            )}
            <section className="panel">
              {b.me.length > 0 && (
                <div className="my-rank">
                  <Trophy size={20} />
                  <strong>Your position: #{b.me[0].rank}</strong>
                  <span>
                    {subject === "overall"
                      ? `${b.me[0].total_xp} XP`
                      : `${b.me[0].rating} rating`}
                  </span>
                </div>
              )}
              {b.entries.length ? (
                <div className="table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>Rank</th>
                        <th>Player</th>
                        <th>Level</th>
                        <th>{subject === "overall" ? "Total XP" : "Rating"}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {b.entries.map((p: any) => (
                        <tr
                          className={p.id === user ? "current-user" : ""}
                          key={p.id}
                        >
                          <td className="rank">#{p.rank}</td>
                          <td>
                            <div className="table-player">
                              <Avatar seed={p.avatar_url} />
                              <div>
                                <strong>
                                  {p.display_name}
                                  {p.id === user && (
                                    <span className="you-tag">YOU</span>
                                  )}
                                </strong>
                                <small>@{p.username}</small>
                              </div>
                            </div>
                          </td>
                          <td>LVL {p.level}</td>
                          <td className="text-purple">
                            <strong>
                              {subject === "overall" ? p.total_xp : p.rating}
                            </strong>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <Empty>
                  {page
                    ? "No more players on this page."
                    : subject === "overall"
                      ? "The leaderboard is waiting for its first contenders."
                      : "Be among the first to qualify. Complete five ranked matches in this subject."}
                </Empty>
              )}
              <div className="pagination">
                <Button
                  variant="ghost"
                  disabled={page === 0}
                  onClick={() => setPage(page - 1)}
                >
                  <ArrowLeft size={15} /> Previous
                </Button>
                <span>Page {page + 1}</span>
                <Button
                  variant="ghost"
                  disabled={b.entries.length < 20}
                  onClick={() => setPage(page + 1)}
                >
                  Next <ArrowRight size={15} />
                </Button>
              </div>
            </section>
          </>
        )
      )}
    </div>
  );
}
