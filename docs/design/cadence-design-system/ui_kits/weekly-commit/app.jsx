/* =========================================================================
   App shell (src/standalone) — demo top bar + persona switcher + WC sub-nav
   + routed content. In production the PA host owns chrome; this shell is
   tree-shaken out (§3.2).
   ========================================================================= */
const { useState } = React;

function PersonaSwitcher({ viewerId, onPick }) {
  const [open, setOpen] = useState(false);
  const v = window.PEOPLE[viewerId];
  const list = ["dana", ...window.REPORT_ORDER];
  return (
    <div className="persona">
      <button className="persona-btn" onClick={() => setOpen(!open)}>
        <Avatar person={v} size={24} />
        <span className="who"><span className="nm">{v.name}</span><span className="rl">{v.isManager ? "Manager" : "IC"}</span></span>
        <Icon name="HiSelector" size={15} style={{ color: "var(--ink-muted)" }} />
      </button>
      {open && (
        <React.Fragment>
          <div style={{ position: "fixed", inset: 0, zIndex: 55 }} onClick={() => setOpen(false)} />
          <div className="persona-menu">
            <div className="grp">Viewing as</div>
            {list.map((id) => {
              const p = window.PEOPLE[id];
              return (
                <button key={id} className={"persona-item" + (id === viewerId ? " active" : "")}
                  onClick={() => { onPick(id); setOpen(false); }}>
                  <Avatar person={p} size={24} />
                  <span className="nm">{p.name}</span>
                  <span className="rl">{p.isManager ? "Manager" : "IC"}</span>
                </button>
              );
            })}
          </div>
        </React.Fragment>
      )}
    </div>
  );
}

function App() {
  const [viewerId, setViewerId] = useState("dana");
  const [surface, setSurface] = useState("team"); // 'mine' | 'team'
  const [sub, setSub] = useState("command");       // 'command' | 'heatmap'
  const [toasts, setToasts] = useState([]);

  const viewer = window.PEOPLE[viewerId];
  const isManager = !!viewer.isManager;

  const pickViewer = (id) => {
    setViewerId(id);
    const mgr = window.PEOPLE[id].isManager;
    setSurface(mgr ? "team" : "mine");
  };

  const pushToast = (msg, tone) => {
    const id = Math.random().toString(36).slice(2);
    setToasts((t) => [...t, { id, msg, tone }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 3500);
  };

  return (
    <div>
      {/* demo top bar */}
      <div className="demo-bar">
        <div className="brand">
          <span className="mark"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><path d="M5 13l4 4L19 7" /></svg></span>
          <span className="name">ST6 Weekly Commit</span>
          <span className="tag">Demo</span>
        </div>
        <span className="spacer" />
        <span className="wk"><Icon name="HiCalendar" size={14} style={{ color: "var(--ink-muted)" }} />{window.WEEK_LABEL}</span>
        <PersonaSwitcher viewerId={viewerId} onPick={pickViewer} />
      </div>

      {/* WC sub-nav (WC-owned) */}
      <div className="subnav">
        {isManager ? (
          <div className="seg">
            <button className={surface === "mine" ? "active" : ""} onClick={() => setSurface("mine")}>My Weekly Commit</button>
            <button className={surface === "team" ? "active" : ""} onClick={() => setSurface("team")}>My Team</button>
          </div>
        ) : (
          <div className="seg single"><span className="lone">My Weekly Commit</span></div>
        )}
        {isManager && surface === "team" && (
          <div className="seg">
            <button className={sub === "command" ? "active" : ""} onClick={() => setSub("command")}>Command Center</button>
            <button className={sub === "heatmap" ? "active" : ""} onClick={() => setSub("heatmap")}>Heatmap</button>
          </div>
        )}
        <span className="spacer" />
        <button className="week-sel"><Icon name="HiCalendar" size={14} />Week of Jun 1–7 <Icon name="HiChevronDown" size={13} /></button>
      </div>

      {/* routed content */}
      {surface === "mine"
        ? <WeeklyPlanView key={viewerId} planKey={viewerId} pushToast={pushToast} />
        : sub === "command"
          ? <CommandCenter pushToast={pushToast} />
          : <HeatmapGrid pushToast={pushToast} />}

      <Toasts items={toasts} />
    </div>
  );
}

ReactDOM.createRoot(document.getElementById("root")).render(<App />);
