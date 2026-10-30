import { useEffect } from "react";
import { createRoot } from "react-dom/client";
import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
  Outlet,
  useLocation,
  Link,
} from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { supabase } from "./lib/supabase";
import { useSession } from "./stores/session";
import Shell from "./components/Shell";
import { Loading } from "./components/common";
import Home from "./pages/Home";
import Auth from "./pages/Auth";
import Onboarding from "./pages/Onboarding";
import Play from "./pages/Play";
import Arena, { Room } from "./pages/Arena";
import Results from "./pages/Results";
import Progress from "./pages/Progress";
import Profile from "./pages/Profile";
import Leaderboards from "./pages/Leaderboards";
import "./styles.css";
const client = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 15000 },
    mutations: { retry: 0 },
  },
});
function Protected() {
  const { session, ready } = useSession();
  const location = useLocation();
  if (!ready) return <Loading />;
  return session ? (
    <Outlet />
  ) : (
    <Navigate
      to={`/auth?next=${encodeURIComponent(location.pathname + location.search)}`}
      replace
    />
  );
}
function App() {
  const setSession = useSession((s) => s.setSession);
  const location = useLocation();
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [location.pathname]);
  useEffect(() => {
    if (!supabase) {
      setSession(null);
      return;
    }
    let live = true;
    void supabase.auth.getSession().then(({ data }) => {
      if (live) setSession(data.session);
    });
    const { data } = supabase.auth.onAuthStateChange((_event, session) => {
      setSession(session);
      if (!session) client.clear();
    });
    return () => {
      live = false;
      data.subscription.unsubscribe();
    };
  }, [setSession]);
  return (
    <Routes>
      <Route element={<Shell />}>
        <Route index element={<Home />} />
        <Route path="auth" element={<Auth />} />
        <Route element={<Protected />}>
          <Route path="onboarding" element={<Onboarding />} />
          <Route path="play" element={<Play />} />
          <Route path="subjects" element={<Play />} />
          <Route path="room/:id" element={<Room />} />
          <Route path="arena/:id" element={<Arena />} />
          <Route path="diagnostic/:id" element={<Arena />} />
          <Route path="recovery/:id" element={<Arena />} />
          <Route path="results/:id" element={<Results />} />
          <Route path="reports/:id" element={<Results />} />
          <Route path="progress" element={<Progress />} />
          <Route path="profile" element={<Profile />} />
          <Route path="leaderboards" element={<Leaderboards />} />
        </Route>
        <Route
          path="*"
          element={
            <div className="empty">
              <h1>Out of bounds.</h1>
              <Link to="/">Back to the playground →</Link>
            </div>
          }
        />
      </Route>
    </Routes>
  );
}
createRoot(document.getElementById("root")!).render(
  <QueryClientProvider client={client}>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </QueryClientProvider>,
);
