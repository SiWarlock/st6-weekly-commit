/* =========================================================================
   CommandCenter (§6.3) — manager roll-up table + at-a-glance + filters
   + locked-plan review Drawer.
   ========================================================================= */
const { useState } = React;

function riskChips(row) {
  const out = [];
  out.push(<CountChip key="p">{row.planned}P</CountChip>);
  out.push(<CountChip key="u">{row.unplanned}U</CountChip>);
  row.risks.forEach((r, i) => {
    const k = r.kind;
    if (k === "misaligned") out.push(<Badge key={i} tone="accent" icon="HiExclamation" xs>{r.n} misaligned</Badge>);
    else if (k === "needsReview") out.push(<Badge key={i} tone="info" icon="HiQuestionMarkCircle" xs>{r.n} needs-review</Badge>);
    else if (k === "blocked") out.push(<Badge key={i} tone="failure" icon="HiBan" ring xs>{r.n} blocked</Badge>);
    else if (k === "carry") out.push(<Badge key={i} tone="warning" icon="HiArrowNarrowRight" ring xs>{r.n} carry-fwd</Badge>);
    else if (k === "dispute") out.push(<Badge key={i} tone="failure" icon="HiFlag" xs>{r.n} dispute</Badge>);
    else if (k === "resolved") out.push(<Badge key={i} tone="neutral" icon="HiCheckCircle" xs>{r.n} resolved</Badge>);
    else if (k === "unlinked") out.push(<Badge key={i} tone="warning" icon="HiExclamationCircle" xs>{r.n} unlinked</Badge>);
  });
  return out;
}

function ReviewCell({ row }) {
  if (row.review === "NONE") return <div><span style={{ color: "var(--ink-muted)" }}>— not locked</span></div>;
  const value = row.overdue ? "OVERDUE" : row.review;
  return (
    <div>
      <ReviewPill value={value} xs />
      <div className="cell-sub">{row.reviewMeta}</div>
    </div>
  );
}

function ReviewDrawer({ row, onClose, pushToast }) {
  const [markOpen, setMarkOpen] = useState(false);
  const [flagOpen, setFlagOpen] = useState(false);
  const p = window.PEOPLE[row.person];
  const plan = window.PLANS[row.person];
  const isDraft = row.planState === "DRAFT";
  const reviewVal = row.review === "NONE" ? null : (row.overdue ? "OVERDUE" : row.review);

  return (
    <React.Fragment>
      <div className="scrim" onClick={onClose} />
      <div className="drawer" role="dialog" aria-modal="true">
        <div className="dhead">
          <div className="grow">
            <div className="who"><Avatar person={p} size={28} />{p.name} — {window.WEEK_LABEL}</div>
            <div className="meta">
              <PlanStatePill value={row.planState} xs />
              {reviewVal && <ReviewPill value={reviewVal} xs />}
              {plan && plan.locked && <span>Locked {plan.locked}</span>}
            </div>
          </div>
          <button className="closebtn" onClick={onClose}><Icon name="HiX" size={18} /></button>
        </div>

        {!isDraft && (row.review === "NOT_REVIEWED" || row.overdue || row.review === "REVIEWED_WITH_DISPUTES") && (
          <div className="dbar">
            <Btn variant="primary" icon="HiCheckCircle" onClick={() => setMarkOpen(true)}>Mark reviewed</Btn>
            <span style={{ fontSize: 12, color: "var(--ink-muted)" }}>Status is derived from the unresolved-dispute count.</span>
          </div>
        )}

        <div className="dbody">
          {isDraft ? (
            <div className="empty" style={{ padding: "36px 18px" }}>
              <span className="ic"><Icon name="HiOutlineEye" size={20} /></span>
              <div className="h">Review opens once {p.name.split(" ")[0]} locks this plan</div>
              <p className="g">You can read the draft and comment, but there's no baseline to review yet.</p>
            </div>
          ) : (
            <React.Fragment>
              <div style={{ fontSize: 12, textTransform: "uppercase", letterSpacing: ".05em", color: "var(--ink-muted)", marginBottom: 10 }}>
                Commitments ({plan ? plan.planned : 0} planned)
              </div>
              {plan && plan.commitments.map((c) => {
                const disputed = c.dispute && c.dispute.status === "OPEN";
                return (
                  <div key={c.id} className={"rev-commit" + (disputed ? " disputed" : "")}>
                    <div className="rh">
                      <div className="rt">{c.title}</div>
                      <div style={{ display: "flex", gap: 6, flex: "none" }}>
                        <PriorityTag value={c.priority} /><WorkTypeTag value={c.workType} />
                      </div>
                    </div>
                    <div className="rmeta">
                      <RcdoBreadcrumb soId={c.soId} compact />
                    </div>
                    <div className="rmeta">
                      <AlignmentChip value={c.alignment} /><span style={{ color: "var(--ink-muted)" }}>·</span>
                      <ConfidenceMeter value={c.confidence} />
                    </div>
                    {disputed && (
                      <div className="alert-strip" style={{ marginTop: 8 }}>
                        <Icon name="HiFlag" size={14} /><span className="grow">Open dispute — {c.dispute.flagType === "MISALIGNED" ? "Misaligned" : "Needs revision"}</span>
                        <Btn size="sm" variant="danger" onClick={() => pushToast("Opening dispute panel…", "success")}>View dispute</Btn>
                      </div>
                    )}
                    {c.dispute && c.dispute.status === "RESOLVED" && (
                      <div style={{ marginTop: 8 }}><Badge tone="success" icon="HiCheckCircle" xs>Dispute resolved</Badge></div>
                    )}
                    <div className="ractions">
                      {!c.dispute && <Btn size="sm" variant="ghost" icon="HiFlag" onClick={() => setFlagOpen(c.id)}>Flag alignment</Btn>}
                      <Btn size="sm" variant="ghost" icon="HiChat" onClick={() => pushToast("Comment thread opened.", "success")}>Comment</Btn>
                    </div>
                    {disputed && (
                      <div className="mgr-note">
                        <div className="lbl">Manager note (your post-lock-mutable field)</div>
                        <input className="wc-input" placeholder="Add a note for this commitment…" />
                      </div>
                    )}
                  </div>
                );
              })}
              <div className="comments">
                <div style={{ fontSize: 12, textTransform: "uppercase", letterSpacing: ".05em", color: "var(--ink-muted)", marginBottom: 8 }}>Plan comments</div>
                <div className="comment">
                  <Avatar person="dana" size={26} />
                  <div className="body"><div className="by"><b>Dana Okafor</b><span className="tm">Jun 1, 3:14 PM</span></div>
                    <div className="txt">Thanks for locking early. Let's tighten the reliability linkage on the telemetry item.</div></div>
                </div>
                <div style={{ marginTop: 10 }}>
                  <textarea className="textarea" placeholder="Write a comment…" />
                  <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 8 }}><Btn size="sm" variant="primary">Post</Btn></div>
                </div>
              </div>
            </React.Fragment>
          )}
        </div>
      </div>
      {markOpen && <MarkReviewedModal row={row} onClose={() => setMarkOpen(false)}
        onConfirm={() => { setMarkOpen(false); onClose(); pushToast("Plan marked reviewed.", "success"); }} />}
      {flagOpen && <FlagModal onClose={() => setFlagOpen(false)}
        onConfirm={() => { setFlagOpen(false); pushToast("Dispute opened. The report can now respond.", "success"); }} />}
    </React.Fragment>
  );
}

function CommandCenter({ pushToast }) {
  const [drawer, setDrawer] = useState(null);
  const rows = window.COMMAND_ROWS;

  return (
    <div>
      <div className="breadcrumb">My Team <Icon name="HiChevronRight" size={13} /><span className="here">Command Center</span></div>
      <div className="surface">
        <div className="page-head">
          <h1 className="h1">Alignment Command Center</h1>
          <p className="sub">Your direct reports' weekly alignment at a glance.</p>
        </div>

        <div className="wk-controls">
          <div className="nav">
            <button className="stepbtn"><Icon name="HiChevronLeft" size={16} /></button>
            <span className="label">Jun 1 – Jun 7, 2026</span>
            <button className="stepbtn"><Icon name="HiChevronDown" size={16} /></button>
            <button className="stepbtn"><Icon name="HiChevronRight" size={16} /></button>
          </div>
          <span className="spacer" style={{ flex: 1 }} />
          <span className="fresh">Updated 2 min ago</span>
          <button className="refresh" onClick={() => pushToast("Refetched.", "success")}><Icon name="HiRefresh" size={15} /></button>
        </div>

        <div className="glance">
          <span className="lead">At a glance</span>
          <span className="gitem"><b>{window.GLANCE.reports}</b> reports</span><span className="sep" />
          <span className="gitem" style={{ color: "var(--tone-failure-fg)" }}><Icon name="HiClock" size={13} /><b>{window.GLANCE.overdue}</b> review overdue</span><span className="sep" />
          <span className="gitem" style={{ color: "var(--tone-warning-fg)" }}><Icon name="HiFlag" size={13} /><b>{window.GLANCE.disputes}</b> open disputes</span><span className="sep" />
          <span className="gitem"><b>{window.GLANCE.reconciling}</b> reconciling</span><span className="sep" />
          <span className="gitem"><b>{window.GLANCE.draft}</b> not locked</span><span className="sep" />
          <span className="gitem" style={{ color: "var(--tone-success-fg)" }}><Icon name="HiCheckCircle" size={13} /><b>{window.GLANCE.reviewedClean}</b> reviewed clean</span>
        </div>

        <div className="filters">
          <div className="frow">
            <span className="ftitle">Filters</span>
            {["Person", "Plan state", "Review state", "Defining objective", "Priority", "Work type"].map((f) => (
              <button key={f} className="fdrop">{f}<Icon name="HiChevronDown" size={13} /></button>
            ))}
            <button className="clear">Clear all</button>
          </div>
          <div className="fchips">
            <span className="fchip">Review: Overdue <button><Icon name="HiX" size={12} /></button></span>
            <span className="showing">Showing 6 of 6 reports</span>
          </div>
        </div>

        <div className="tbl-wrap">
          <div className="tbl-cap"><span className="t">Direct reports — {window.WEEK_LABEL}</span><span className="spacer" /><span className="sort">Sort: Week ▼ · Name ▲</span></div>
          <table className="wc-tbl">
            <thead><tr><th>Report</th><th>Plan state</th><th>Review status</th><th>Reconcile</th><th>Risk chips</th><th></th></tr></thead>
            <tbody>
              {rows.map((row) => {
                const p = window.PEOPLE[row.person];
                return (
                  <tr key={row.person} onClick={() => setDrawer(row)}>
                    <td>
                      <div className="cell-report">
                        <Avatar person={p} size={26} />
                        <div><span className="nm">{p.name}</span></div>
                        {row.syncFailed && <span className="syncflag" title="Outlook sync failed"><Icon name="HiExclamationCircle" size={14} /></span>}
                      </div>
                    </td>
                    <td><PlanStatePill value={row.planState} xs /></td>
                    <td><ReviewCell row={row} /></td>
                    <td>{row.reconcile ? <span style={{ fontSize: 12, color: "var(--ink-secondary)" }}>{row.reconcile}</span> : <span style={{ color: "var(--ink-muted)" }}>—</span>}</td>
                    <td><div className="risk-cell">{riskChips(row)}</div></td>
                    <td className="actcol" onClick={(e) => e.stopPropagation()}>
                      {row.action
                        ? <Btn size="sm" variant={row.actionTone === "neutral" ? "secondary" : "secondary"} iconRight="HiChevronRight" onClick={() => setDrawer(row)}>{row.action}</Btn>
                        : <span style={{ color: "var(--ink-muted)" }}>—</span>}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <div className="legend-row"><span>P = planned · U = unplanned</span><span className="spacer" /><span>25 / page</span></div>
        </div>
      </div>
      {drawer && <ReviewDrawer row={drawer} onClose={() => setDrawer(null)} pushToast={pushToast} />}
    </div>
  );
}

Object.assign(window, { CommandCenter, ReviewDrawer });
