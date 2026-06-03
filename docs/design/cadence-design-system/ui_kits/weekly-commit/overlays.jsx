/* =========================================================================
   Overlays — RcdoPicker, LockModal, MarkReviewedModal, FlagModal, Toasts
   ========================================================================= */
const { useState } = React;

function RcdoPicker({ value, onSelect, onClose }) {
  const [sel, setSel] = useState(value || null);
  const [q, setQ] = useState("");
  const [open, setOpen] = useState(() => {
    const init = {};
    window.RCDO_TREE.forEach((d) => { init[d.id] = true; });
    return init;
  });
  const ql = q.trim().toLowerCase();
  const match = (s) => !ql || s.toLowerCase().includes(ql);

  return (
    <div className="modal-c">
      <div className="scrim" onClick={onClose} />
      <div className="modal lg" role="dialog" aria-modal="true" style={{ position: "relative", zIndex: 1 }}>
        <div className="mhead"><span className="h">Link a Supporting Outcome</span>
          <button className="closebtn" onClick={onClose} style={{ background: "none", border: 0, color: "var(--ink-muted)", cursor: "pointer" }}><Icon name="HiX" size={18} /></button></div>
        <div className="mbody">
          <div style={{ position: "relative", marginBottom: 12 }}>
            <span style={{ position: "absolute", left: 10, top: 9, color: "var(--ink-muted)" }}><Icon name="HiSearch" size={16} /></span>
            <input className="wc-input" style={{ paddingLeft: 32 }} placeholder="Search outcomes…"
              value={q} onChange={(e) => setQ(e.target.value)} autoFocus />
          </div>
          <div className="tree" role="tree">
            <div className="rc"><Icon name="HiSparkles" size={14} style={{ color: "var(--brand-400)" }} />{window.RALLY_CRY}</div>
            {window.RCDO_TREE.map((d) => {
              const leaves = d.outcomes.filter((o) => match(o.title) || match(o.id) || match(d.title));
              if (ql && leaves.length === 0 && !match(d.title)) return null;
              const isOpen = open[d.id] || (ql && leaves.length > 0);
              return (
                <div className="donode" key={d.id}>
                  <div className={"dohd" + (isOpen ? " open" : "")} onClick={() => setOpen({ ...open, [d.id]: !isOpen })}>
                    <Icon name="HiChevronRight" size={14} className="caret" />
                    <span style={{ fontFamily: "var(--font-mono)", fontSize: 11, color: "var(--ink-muted)" }}>{d.id}</span>{d.title}
                  </div>
                  {isOpen && (
                    <div className="leaves">
                      {(ql ? leaves : d.outcomes).map((o) => (
                        <div key={o.id} className={"leaf" + (sel === o.id ? " sel" : "")} onClick={() => setSel(o.id)} role="treeitem">
                          <span className="rad" />
                          <span><span className="soid">{o.id}</span>&nbsp; {o.title}</span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
          {sel && (
            <div className="picker-sel">Selected:&nbsp;<RcdoBreadcrumb soId={sel} /></div>
          )}
        </div>
        <div className="mfoot">
          <Btn variant="ghost" onClick={onClose}>Cancel</Btn>
          <Btn variant="primary" iconRight="HiArrowNarrowRight" disabled={!sel} onClick={() => onSelect(sel)}>Link outcome</Btn>
        </div>
      </div>
    </div>
  );
}

function LockModal({ plan, onConfirm, onClose }) {
  const p = window.PEOPLE[plan.owner];
  return (
    <div className="modal-c">
      <div className="scrim" onClick={onClose} />
      <div className="modal" role="dialog" aria-modal="true" style={{ position: "relative", zIndex: 1 }}>
        <div className="mhead"><span className="h">Lock this week's plan?</span></div>
        <div className="mbody">
          <p>Locking freezes your planned commitments as the baseline for reconciliation. You can't edit, add, or remove planned commitments after locking — this can't be undone.</p>
          <ul>
            <li>{plan.planned} planned commitments, all linked to a Supporting Outcome</li>
            <li>Your manager (Dana Okafor) can review once locked</li>
          </ul>
        </div>
        <div className="mfoot">
          <Btn variant="ghost" onClick={onClose}>Keep editing</Btn>
          <Btn variant="primary" icon="HiLockClosed" onClick={onConfirm}>Lock plan</Btn>
        </div>
      </div>
    </div>
  );
}

function MarkReviewedModal({ row, onConfirm, onClose }) {
  const disputes = row.risks.find((r) => r.kind === "dispute");
  return (
    <div className="modal-c">
      <div className="scrim" onClick={onClose} />
      <div className="modal" role="dialog" aria-modal="true" style={{ position: "relative", zIndex: 1 }}>
        <div className="mhead"><span className="h">Mark plan reviewed</span></div>
        <div className="mbody">
          <p>Add an optional summary note. The status is derived from the server — you can't pick it.</p>
          <div style={{ background: "var(--surface-sunken)", border: "1px solid var(--border)", borderRadius: 8, padding: "10px 12px", marginBottom: 12, fontSize: 13, color: "var(--ink-secondary)" }}>
            {disputes
              ? <span><b style={{ color: "var(--tone-warning-fg)" }}>1 unresolved dispute</b> → will become <b>Reviewed · disputes</b></span>
              : <span><b style={{ color: "var(--tone-success-fg)" }}>0 unresolved disputes</b> → will become <b>Reviewed</b></span>}
          </div>
          <textarea className="textarea" placeholder="Summary note (optional) — e.g. “Solid week.”" />
        </div>
        <div className="mfoot">
          <Btn variant="ghost" onClick={onClose}>Cancel</Btn>
          <Btn variant="primary" icon="HiCheckCircle" onClick={onConfirm}>Mark reviewed</Btn>
        </div>
      </div>
    </div>
  );
}

function FlagModal({ onConfirm, onClose }) {
  const [flag, setFlag] = useState("MISALIGNED");
  const [note, setNote] = useState("");
  return (
    <div className="modal-c">
      <div className="scrim" onClick={onClose} />
      <div className="modal" role="dialog" aria-modal="true" style={{ position: "relative", zIndex: 1 }}>
        <div className="mhead"><span className="h">Flag alignment</span></div>
        <div className="mbody">
          <div style={{ display: "flex", gap: 8, marginBottom: 14 }}>
            {[["MISALIGNED", "Misaligned", "failure"], ["NEEDS_REVISION", "Needs revision", "warning"]].map(([v, l, t]) => (
              <button key={v} className={"recon-opt" + (flag === v ? " sel " + t : "")} onClick={() => setFlag(v)}>
                {flag === v && <Icon name={v === "MISALIGNED" ? "HiExclamation" : "HiPencilAlt"} size={13} />}{l}
              </button>
            ))}
          </div>
          <div style={{ fontSize: 11, color: "var(--ink-muted)", marginBottom: 6 }}>A note is required to flag a commitment.</div>
          <textarea className="textarea" value={note} onChange={(e) => setNote(e.target.value)}
            placeholder="Explain the misalignment and what would resolve it…" />
        </div>
        <div className="mfoot">
          <Btn variant="ghost" onClick={onClose}>Cancel</Btn>
          <Btn variant="danger" icon="HiFlag" disabled={!note.trim()} onClick={onConfirm}>Open dispute</Btn>
        </div>
      </div>
    </div>
  );
}

function Toasts({ items }) {
  return (
    <div className="toast-wrap">
      {items.map((t) => (
        <div key={t.id} className={"toast " + (t.tone || "success")}>
          <Icon name={t.tone === "warning" ? "HiExclamationCircle" : "HiCheckCircle"} size={16} />
          {t.msg}
        </div>
      ))}
    </div>
  );
}

Object.assign(window, { RcdoPicker, LockModal, MarkReviewedModal, FlagModal, Toasts });
