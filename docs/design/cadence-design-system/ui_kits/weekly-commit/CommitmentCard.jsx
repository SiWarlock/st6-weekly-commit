/* =========================================================================
   CommitmentCard (§5.2) — mode: draft | locked | reconciling | readOnly
   ========================================================================= */
const { useState } = React;

const RECON_CHOICES = [
  { v: "COMPLETED", label: "Completed", tone: "success" },
  { v: "PARTIALLY_COMPLETED", label: "Partial", tone: "warning" },
  { v: "BLOCKED", label: "Blocked", tone: "failure" },
  { v: "CANCELED", label: "Canceled", tone: "neutral" },
  { v: "CARRIED_FORWARD", label: "Carry forward", tone: "info" },
];

function ChessChips({ c }) {
  return (
    <div className="chips">
      <PriorityTag value={c.priority} />
      <WorkTypeTag value={c.workType} />
      <ConfidenceMeter value={c.confidence} />
      <AlignmentChip value={c.alignment} />
    </div>
  );
}

function ReconControl({ c, onOutcome }) {
  return (
    <div className="recon">
      <div className="lbl">Outcome</div>
      <div className="recon-opts">
        {RECON_CHOICES.map((o) => {
          const sel = c.outcome === o.v;
          return (
            <button key={o.v} className={"recon-opt" + (sel ? " sel " + o.tone : "")}
              onClick={() => onOutcome && onOutcome(c.id, o.v)}>
              {sel && <Icon name={OUTCOME[o.v].icon} size={13} />}{o.label}
            </button>
          );
        })}
      </div>
      {c.outcome === "CARRIED_FORWARD" ? (
        <div className="carry-note">
          <Icon name="HiArrowNarrowRight" size={14} />
          Carried forward to week of {c.carriedTo || window.NEXT_WEEK}
          <a className="lnk" href="#" onClick={(e) => e.preventDefault()}>view in next plan ›</a>
        </div>
      ) : c.outcome ? (
        <input className="wc-input" style={{ marginTop: 9 }} defaultValue={c.outcomeNote}
          placeholder="Add a note about what actually happened…" />
      ) : (
        <div className="carry-note" style={{ color: "var(--ink-muted)" }}>Set an outcome to continue.</div>
      )}
    </div>
  );
}

function DisputeStrip({ dispute, onRespond }) {
  const [soId, setSoId] = useState(null);
  return (
    <div className="dispute-strip">
      <div className="dh">
        <Icon name="HiFlag" size={15} style={{ color: "var(--tone-failure-fg)" }} />
        <span>Alignment dispute — opened by {dispute.openedBy}</span>
        <span className="grow" />
        <Badge tone={dispute.flagType === "MISALIGNED" ? "failure" : "warning"} xs>
          Flag: {dispute.flagType === "MISALIGNED" ? "Misaligned" : "Needs revision"}
        </Badge>
        <Badge tone="failure" icon="HiFlag" xs>Open</Badge>
      </div>
      <div className="body">
        <div style={{ fontSize: 11, color: "var(--ink-muted)" }}>Manager note</div>
        <p className="qt">“{dispute.managerNote}”</p>
        <div className="resp">
          <div className="lbl">Your response — revise the Supporting Outcome and/or add rationale (at least one required; this does not resolve the dispute):</div>
          <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 9, flexWrap: "wrap" }}>
            <Btn size="sm" variant="secondary" icon="HiPencilAlt" onClick={() => onRespond && onRespond(setSoId)}>
              {soId ? "Outcome re-linked" : "Re-link Supporting Outcome"}
            </Btn>
            {soId && <RcdoBreadcrumb soId={soId} />}
          </div>
          <textarea className="textarea" placeholder="Tying telemetry to the p95 latency target, not adoption." />
          <div style={{ display: "flex", alignItems: "center", gap: 12, marginTop: 10 }}>
            <span style={{ fontSize: 11, color: "var(--ink-muted)" }}>Only {dispute.openedBy.split(" ")[0]} can mark this resolved.</span>
            <span style={{ flex: 1 }} />
            <Btn size="sm" variant="primary" iconRight="HiArrowNarrowRight">Submit response</Btn>
          </div>
        </div>
      </div>
    </div>
  );
}

function CommitmentCard({ c, mode, onLink, onChange, onOutcome, onRespond, onEdit, onDelete }) {
  const locked = mode === "locked" || mode === "reconciling" || mode === "readOnly";
  const isUnplanned = c.kind === "UNPLANNED";
  const accentCls = isUnplanned ? " wc-card--accent-unplanned" : (c.dispute ? " wc-card--accent-dispute" : "");
  const missing = !c.soId;

  return (
    <div className={"commit wc-card" + accentCls}>
      <div className="hd">
        <h3 className="title">
          {locked && <Icon name="HiLockClosed" size={14} className="lock" />}
          {c.title}
          {isUnplanned && <Badge tone="accent" icon="HiPlusCircle" xs>Unplanned</Badge>}
        </h3>
        {mode === "draft" && (
          <div className="menu">
            <button title="Edit" onClick={() => onEdit && onEdit(c)}><Icon name="HiPencil" size={16} /></button>
            <button title="Delete" onClick={() => onDelete && onDelete(c)}><Icon name="HiTrash" size={16} /></button>
          </div>
        )}
      </div>
      {c.desc && <p className="desc">{c.desc}</p>}
      <ChessChips c={c} />

      {missing ? (
        <div className="alert-strip">
          <Icon name="HiExclamationCircle" size={15} />
          <span className="grow">No Supporting Outcome linked — required before you can {mode === "reconciling" ? "close" : "lock"}</span>
          <Btn size="sm" variant="danger" icon="HiArrowNarrowRight" onClick={() => onLink && onLink(c)}>Link Supporting Outcome</Btn>
        </div>
      ) : (
        <RcdoBreadcrumb soId={c.soId} onChange={mode === "draft" ? () => onLink && onLink(c) : undefined} />
      )}

      {mode === "reconciling" && <ReconControl c={c} onOutcome={onOutcome} />}
      {mode === "readOnly" && c.outcome && (
        <div className="carry-note" style={{ marginTop: 10 }}>
          <OutcomePill value={c.outcome} />
          {c.outcome === "CARRIED_FORWARD"
            ? <span>→ went to week of {c.carriedTo || window.NEXT_WEEK} <a className="lnk" href="#" onClick={(e) => e.preventDefault()}>view successor ›</a></span>
            : (c.outcomeNote && <span style={{ color: "var(--ink-secondary)", fontStyle: "italic" }}>“{c.outcomeNote}”</span>)}
        </div>
      )}
      {mode === "locked" && c.dispute && c.dispute.status === "OPEN" && (
        <DisputeStrip dispute={c.dispute} onRespond={onRespond} />
      )}
    </div>
  );
}

Object.assign(window, { CommitmentCard, ChessChips });
