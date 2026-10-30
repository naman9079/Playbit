import {
  Component,
  lazy,
  Suspense,
  useEffect,
  type ErrorInfo,
  type ReactNode,
} from "react";
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
import "./styles.css";
const Home = lazy(() => import("./pages/Home"));
const Auth = lazy(() => import("./pages/Auth"));
const Onboarding = lazy(() => import("./pages/Onboarding"));
const Play = lazy(() => import("./pages/Play"));
const Arena = lazy(() => import("./pages/Arena"));
const Room = lazy(() =>
  import("./pages/Arena").then((module) => ({ default: module.Room })),
);
const Results = lazy(() => import("./pages/Results"));
const Progress = lazy(() => import("./pages/Progress"));
const Profile = lazy(() => import("./pages/Profile"));
const Leaderboards = lazy(() => import("./pages/Leaderboards"));
const client = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 15000 },
    mutations: { retry: 0 },
  },
});
class AppErrorBoundary extends Component<
  { children: ReactNode },
  { error: Error | null }
> {
  state: { error: Error | null } = { error: null };
  static getDerivedStateFromError(error: Error) {
    return { error };
  }
  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("Playbit screen crashed", error, info.componentStack);
  }
  render() {
    if (this.state.error) {
      return (
        <main className="empty" role="alert">
          <h1>This screen hit a problem.</h1>
          <p>{this.state.error.message}</p>
          <button onClick={() => window.location.assign("/play")}>
            Return to Play
          </button>
        </main>
      );
    }
    return this.props.children;
  }
}
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
    <AppErrorBoundary>
      <Suspense fallback={<Loading />}>
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
      </Suspense>
    </AppErrorBoundary>
  );
}
createRoot(document.getElementById("root")!).render(
  <QueryClientProvider client={client}>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </QueryClientProvider>,
);
