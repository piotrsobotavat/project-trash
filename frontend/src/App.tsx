import { useState } from "react";
import Repositories from "./components/Repositories";
import PullRequests from "./components/PullRequests";
import "./App.css";

type Tab = "repos" | "prs";

function App() {
  const [tab, setTab] = useState<Tab>("prs");

  return (
    <div className="app">
      <div className="scanline" />
      <div className="grid-bg" />
      <header className="app-header">
        <h1>
          <span className="glow-text">⟨</span> Azure DevOps{" "}
          <span className="glow-text">Dashboard</span>{" "}
          <span className="glow-text">⟩</span>
        </h1>
        <nav>
          <button
            className={tab === "prs" ? "active" : ""}
            onClick={() => setTab("prs")}
          >
            <span className="btn-icon">◈</span> Pull Requests
          </button>
          <button
            className={tab === "repos" ? "active" : ""}
            onClick={() => setTab("repos")}
          >
            <span className="btn-icon">◇</span> Repositories
          </button>
        </nav>
      </header>

      <main>
        {tab === "repos" && <Repositories />}
        {tab === "prs" && <PullRequests />}
      </main>

      <footer className="app-footer">
        <span className="pulse-dot" /> SYSTEM ONLINE
      </footer>
    </div>
  );
}

export default App;
