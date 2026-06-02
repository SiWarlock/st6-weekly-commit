/* =========================================================================
   HeatmapGrid (§6.4) — reports × Defining Objectives, volume + risk badges,
   plus cell drill-down Drawer. Volume (load) and risk (badges) are decoupled.
   ========================================================================= */

const DOS = window.RCDO_TREE.map((d) => ({ id: d.id, title: d.title }));

function volMeta(count) {
  if (count === 0) return { bars: 0, bg: "var(--vol-gap)", border: "1px dashed var(--vol-gap-border)" };
  if (count === 1) return { bars: 1, bg: "var(--vol-light)", border: "1px solid var(--border)" };
  if (count <= 3) return { bars: 2, bg: "var(--vol-normal)", border: "1px solid var(--border)" };
  return { bars: 3, bg: "var(--vol-heavy)", border: "1px solid var(--vol-heavy-border)" };
}
const BAR_H = [8, 11, 14];

function VolBars({ n }) {
  return (
    <span className="cell-bars">
      {[0, 1, 2].map((i) => i < n
        ? <i key={i} style={{ height: BAR_H[i] }} />
        : <i key={i} style={{ height: BAR_H[i], background: "var(--n-300)" }} />)}
    </span>
  );
}

function getDrill(person, doId) {
  const key = person + "|" + doId;
  if (window.DRILLDOWN[key]) return window.DRILLDOWN[key];
  // synthesize from the report's plan commitments mapping to this DO
  const plan = window.PLANS[person];
  const groups = {};
  if (plan) plan.commitments.forEach((c) => {
    const bc = window.breadcrumb(c.soId);
    if (bc && window.SO_INDEX[c.soId].doId === doId) {
      (groups[c.soId] = groups[c.soId] || { soId: c.soId, soTitle: bc.soTitle, commitments: [] }).commitments.push(c);
    }
  });
  return {
    person, doTitle: DOS.find((d) => d.id === doId).title,
    planState: plan ? plan.state : "DRAFT", cellRisk: null,
    groups: Object.values(groups),
  };
}

function DrilldownDrawer({ person, doId, cell, onClose, pushToast }) {
  const p = window.PEOPLE[person];
  const drill = getDrill(person, doId);
  const total = drill.groups.reduce((n, g) => n + g.commitments.length, 0);
  return (
    <React.Fragment>
      <div className="scrim" onClick={onClose} />
      <div className="drawer" role="dialog" aria-modal="true">
        <div className="dhead">
          <div className="grow">
            <div className="who"><Avatar person={p} size={28} />{p.name} · {drill.doTitle}</div>
            <div className="meta">
              <span>plan: {drill.planState.toLowerCase()}</span>
              {cell.risks.map((r) => <RiskBadge key={r} value={r} />)}
            </div>
          </div>
          <button className="closebtn" onClick={onClose}><Icon name="HiX" size={18} /></button>
        </div>
        <div className="dbody">
          {drill.groups.length === 0 ? (
            <div className="empty" style={{ padding: "36px 18px" }}>
              <span className="ic"><Icon name="HiViewGrid" size={20} /></span>
              <div className="h">No coverage under this objective</div>
              <p className="g">{p.name.split(" ")[0]} mapped no commitments to {drill.doTitle} this week.</p>
            </div>
          ) : drill.groups.map((g) => (
            <div key={g.soId} style={{ marginBottom: 16 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 7, marginBottom: 9, fontSize: 13 }}>
                <Icon name="HiChevronRight" size={14} style={{ color: "var(--ink-muted)" }} />
                <span style={{ fontFamily: "var(--font-mono)", fontSize: 11, color: "var(--ink-muted)" }}>{g.soId}</span>
                <span style={{ color: "var(--ink-secondary)" }}>{g.soTitle}</span>
              </div>
              {g.commitments.map((c) => (
                <div key={c.id} className={"rev-commit" + (c.kind === "UNPLANNED" ? "" : "")} style={c.kind === "UNPLANNED" ? { borderLeft: "2px solid var(--tone-accent-solid)" } : null}>
                  <div className="rh">
                    <div className="rt">{c.title}</div>
                    <div style={{ display: "flex", gap: 6, flex: "none" }}>
                      <PriorityTag value={c.priority} /><WorkTypeTag value={c.workType} />
                    </div>
                  </div>
                  <div className="rmeta">
                    <ConfidenceMeter value={c.confidence} />
                    {c.outcome && <React.Fragment><span style={{ color: "var(--ink-muted)" }}>·</span><OutcomePill value={c.outcome} /></React.Fragment>}
                    {c.kind === "UNPLANNED" && <Badge tone="accent" icon="HiPlusCircle" xs>Unplanned</Badge>}
                    {c.dispute && <Badge tone="failure" icon="HiFlag" xs>Dispute</Badge>}
                  </div>
                  <div className="ractions">
                    <Btn size="sm" variant="secondary" iconRight="HiArrowNarrowRight" onClick={() => pushToast("Deep-linking to command center…", "success")}>View in command center</Btn>
                    <Btn size="sm" variant="ghost" icon="HiChat" onClick={() => pushToast("Comment thread opened.", "success")}>Comment</Btn>
                  </div>
                </div>
              ))}
            </div>
          ))}
          {drill.groups.length > 0 && <div style={{ fontSize: 12, color: "var(--ink-muted)" }}>Showing 1–{total} of {total}</div>}
        </div>
      </div>
    </React.Fragment>
  );
}

function HeatCell({ person, doId, cell, onOpen }) {
  const vm = volMeta(cell.count);
  if (cell.count === 0) {
    return (
      <div className="hcell" style={{ background: vm.bg, border: vm.border, borderRadius: 0 }}>
        <div className="cell-gap"><Icon name="HiMinusCircle" size={13} />no coverage</div>
      </div>
    );
  }
  return (
    <div className="hcell" style={{ background: vm.bg, padding: 0 }}>
      <button className="cellbtn" style={{ padding: "11px 12px" }} onClick={() => onOpen(person, doId, cell)}
        aria-label={`${window.PEOPLE[person].name}, ${DOS.find((d) => d.id === doId).title}, ${cell.count} commitments`}>
        <div className="cell-top">
          <VolBars n={vm.bars} />
          <span className="cell-vol">{cell.count}</span>
          {cell.draft && <span style={{ fontSize: 10, color: "var(--ink-muted)" }}>· draft</span>}
        </div>
        {cell.risks.length > 0
          ? <div className="cell-risks">{cell.risks.map((r) => <RiskBadge key={r} value={r} />)}</div>
          : <span className="cell-none">— {cell.draft ? "draft" : "no risk"}</span>}
      </button>
    </div>
  );
}

function rowTotalText(person) {
  const cells = DOS.map((d) => window.HEATMAP[person][d.id]);
  const total = cells.reduce((n, c) => n + c.count, 0);
  const risky = cells.filter((c) => c.risks.length > 0).length;
  const overload = cells.some((c) => c.count >= 4);
  const draftOnly = cells.every((c) => c.count === 0 || c.draft);
  if (draftOnly && total <= 1) return { tone: "neutral", txt: `${total} · not locked` };
  if (risky === 0) return { tone: "success", txt: `${total} clean` };
  return { tone: overload ? "warning" : "failure", txt: `${total} · ${risky} at risk` };
}

function HeatmapGrid({ pushToast }) {
  const [drill, setDrill] = useState(null);
  const open = (person, doId, cell) => setDrill({ person, doId, cell });

  return (
    <div>
      <div className="breadcrumb">My Team <Icon name="HiChevronRight" size={13} /><span className="here">Heatmap</span></div>
      <div className="surface">
        <div className="page-head">
          <h1 className="h1">RCDO Coverage Heatmap</h1>
        </div>
        <p className="heat-meta">Rally Cry: <b>“{window.RALLY_CRY}”</b></p>

        <div className="wk-controls">
          <div className="nav">
            <button className="stepbtn"><Icon name="HiChevronLeft" size={16} /></button>
            <span className="label">Jun 1 – Jun 7, 2026</span>
            <button className="stepbtn"><Icon name="HiChevronRight" size={16} /></button>
          </div>
          <span style={{ flex: 1 }} />
          <button className="refresh" onClick={() => pushToast("Refetched.", "success")}><Icon name="HiRefresh" size={15} /></button>
        </div>

        <div className="heat-wrap">
          <div className="heat-grid">
            <div className="hcell colhead">Report</div>
            {DOS.map((d) => <div key={d.id} className="hcell colhead"><span className="doid">{d.id}</span>{d.title}</div>)}
            <div className="hcell colhead total">Row total</div>

            {window.REPORT_ORDER.map((person) => {
              const rt = rowTotalText(person);
              return (
                <React.Fragment key={person}>
                  <div className="hcell"><div className="rowhead"><Avatar person={person} size={24} />{window.PEOPLE[person].name}</div></div>
                  {DOS.map((d) => <HeatCell key={d.id} person={person} doId={d.id} cell={window.HEATMAP[person][d.id]} onOpen={open} />)}
                  <div className="hcell rowtotal"><Badge tone={rt.tone} xs>{rt.txt}</Badge></div>
                </React.Fragment>
              );
            })}
          </div>
          <div className="heat-self">
            <Avatar person="dana" size={22} />
            <span>Dana Okafor (you · self plan) — RECONCILED, excluded from the team roll-up</span>
            <a className="lnk" href="#" onClick={(e) => e.preventDefault()}>view my plan ›</a>
          </div>
        </div>

        <div className="heat-legend">
          <div className="col">
            <span className="lt">Risk badges</span>
            {["MISALIGNED", "BLOCKED", "OVERDUE_REVIEW"].map((r) => <span key={r} className="li"><RiskBadge value={r} /><span className="k">{r === "MISALIGNED" ? "strategic conflict" : r === "BLOCKED" ? "work is blocked" : "review SLA missed"}</span></span>)}
          </div>
          <div className="col">
            <span className="lt">&nbsp;</span>
            {["NEEDS_REVIEW", "CARRY_FORWARD", "UNREVIEWED"].map((r) => <span key={r} className="li"><RiskBadge value={r} /><span className="k">{r === "NEEDS_REVIEW" ? "IC self-flag" : r === "CARRY_FORWARD" ? "unfinished, moved fwd" : "locked, not reviewed"}</span></span>)}
          </div>
          <div className="col">
            <span className="lt">Volume (commitment count)</span>
            <span className="li"><span style={{ fontFamily: "var(--font-mono)" }}>0</span><span className="k">no coverage (gap)</span></span>
            <span className="li"><span style={{ fontFamily: "var(--font-mono)" }}>1</span><span className="k">light</span><span style={{ fontFamily: "var(--font-mono)", marginLeft: 8 }}>2–3</span><span className="k">normal</span></span>
            <span className="li"><span style={{ fontFamily: "var(--font-mono)" }}>4+</span><span className="k">high load · investigate</span></span>
            <span className="li" style={{ marginTop: 4, color: "var(--ink-muted)", fontSize: 11 }}>Volume = load (neutral). Risk = explicit badges. No single health score.</span>
          </div>
        </div>
      </div>
      {drill && <DrilldownDrawer {...drill} onClose={() => setDrill(null)} pushToast={pushToast} />}
    </div>
  );
}

Object.assign(window, { HeatmapGrid, DrilldownDrawer });
