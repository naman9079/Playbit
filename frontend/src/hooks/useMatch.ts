import { useEffect } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { supabase } from "../lib/supabase";
import { api } from "../services/api";
import { useSession } from "../stores/session";
export function useMatch(id: string) {
  const cache = useQueryClient();
  const user = useSession((s) => s.session?.user.id);
  const query = useQuery({
    queryKey: ["match", id],
    queryFn: () => api(`/matches/${id}`),
    refetchInterval: (q) =>
      q.state.data?.status === "completed" ||
      q.state.data?.status === "cancelled"
        ? false
        : q.state.data?.status === "active"
          ? 5000
          : 2000,
    refetchOnWindowFocus: true,
  });
  useEffect(() => {
    if (!supabase || !user) return;
    const channel = supabase
      .channel(`match:${id}`, {
        config: { private: true, presence: { key: user } },
      })
      .on(
        "postgres_changes",
        {
          event: "UPDATE",
          schema: "public",
          table: "matches",
          filter: `id=eq.${id}`,
        },
        () => {
          void cache.invalidateQueries({ queryKey: ["match", id] });
        },
      )
      .subscribe(async (status) => {
        if (status === "SUBSCRIBED")
          await channel.track({
            userId: user,
            onlineAt: new Date().toISOString(),
          });
      });
    return () => {
      void supabase?.removeChannel(channel);
    };
  }, [id, user, cache]);
  return query;
}
