import { create } from "zustand";
import type { Session } from "@supabase/supabase-js";
export const useSession = create<{
  session: Session | null;
  ready: boolean;
  setSession: (session: Session | null) => void;
}>((set) => ({
  session: null,
  ready: false,
  setSession: (session) => set({ session, ready: true }),
}));
export const usePreferences = create<{
  subject: string;
  setSubject: (subject: string) => void;
}>((set) => ({ subject: "math", setSubject: (subject) => set({ subject }) }));
