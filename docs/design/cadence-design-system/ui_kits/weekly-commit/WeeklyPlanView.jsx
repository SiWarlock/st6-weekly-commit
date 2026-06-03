/* =========================================================================
   WeeklyPlanView (§6.1) — IC workspace. Full lifecycle of one plan.
   ========================================================================= */
const { useState } = React;

const LIFECYCLE = ["DRAFT", "LOCKED", "RECONCILING", "RECONCILED"];
const STEP_LABELS = { DRAFT: "Draft", LOCKED: "Locked", RECONCILING: "Reconciling", RECONCILED: "Reconciled" };

function PlanLifecycleBar({ plan, primary }) {
  const idx = LIFECYCLE.indexOf(plan.state);
  const p = window.PEOPLE[plan.owner];
  return (
    <div className="plan-head">
      <div className="top">
        <span className="wklabel">{window.WEEK_LABEL}</span>
        <PlanStatePill value={plan.state} />
        <span className="counts">{plan.planned} planned · {plan.unplanned} unplanned</span>
        {plan.managerReview && (
          <Badge tone={REVIEW[plan.managerReview.status].tone} icon={REVIEW[plan.managerReview.status].icon} xs>
            {REVIEW[plan.managerReview.status].label}
          </Badge>
        )}
        <span className="spacer" />
        <span className="owner"><Avatar person={p} size={22} />{p.name}</span>
      </div>
      <div className="bottom">
        <div className="stepper">
          {LIFECYCLE.map((s, i) => {
            const done = i < idx, active = i === idx;
            return (
              <React.Fragment key={s}>
                {i > 0 && <span className={"bar" + (i <= idx ? " done" : "")} />}
                <div className="node">
                  <span className={"dotn" + (done ? " done" : active ? " active" : "")}>
                    {done && <Icon name="HiCheck" size={10} strokeWidth={2.6} />}
                  </span>
                  <span className={"lbl" + (i <= idx ? " on" : "")}>{STEP_LABELS[s]}</span>
                </div>
              </React.Fragment>
            );
          })}
        </div>
        <span className="spacer" />
        {primary}
      </div>
    </div>
  );
}

function WeeklyPlanView({ planKey, pushToast }) {
  const [plan, setPlan] = useState(() => JSON.parse(JSON.stringify(window.PLANS[planKey])));
  const [picker, setPicker] = useState(null); // commitment being linked
  const [lockOpen, setLockOpen] = useState(false);

  // keep in sync when persona changes
  React.useEffect(() => { setPlan(JSON.parse(JSON.stringify(window.PLANS[planKey]))); }, [planKey]);

  const setSo = (cid, soId) => {
    setPlan((pl) => ({ ...pl, commitments: pl.commitments.map((c) => c.id === cid ? { ...c, soId } : c) }));
  };
  const setOutcome = (cid, v) => {
    setPlan((pl) => ({ ...pl, commitments: pl.commitments.map((c) => c.id === cid ? { ...c, outcome: v } : c) }));
  };

  const unlinkedPlanned = plan.commitments.filter((c) => c.kind === "PLANNED" && !c.soId).length;
  const canLock = unlinkedPlanned === 0 && plan.planned > 0;
  const allOutcomes = plan.commitments.every((c) => c.outcome);
  const unplannedLinked = plan.commitments.filter((c) => c.kind === "UNPLANNED").every((c) => c.soId);
  const canClose = allOutcomes && unplannedLinked;

  // mode per state
  const cardMode = plan.state === "DRAFT" ? "draft"
    : plan.state === "RECONCILING" ? "reconciling"
      : plan.state === "RECONCILED" ? "readOnly" : "locked";

  // primary action
  let primary = null;
  if (plan.state === "DRAFT") {
    primary = <Btn variant="primary" icon="HiLockClosed" disabled={!canLock}
      title={canLock ? undefined : `${unlinkedPlanned} of ${plan.planned} commitments isn't linked to a Supporting Outcome.`}
      onClick={() => setLockOpen(true)}>Lock plan</Btn>;
  } else if (plan.state === "LOCKED") {
    primary = (<div style={{ display: "flex", gap: 10 }}>
      <Btn variant="secondary" icon="HiPlus" onClick={() => pushToast("Add unplanned work — opens the commitment form.", "success")}>Add unplanned work</Btn>
      <Btn variant="warning" icon="HiClipboardCheck" onClick={() => { setPlan({ ...plan, state: "RECONCILING" }); pushToast("Reconciliation started.", "success"); }}>Start reconciliation</Btn>
    </div>);
  } else if (plan.state === "RECONCILING") {
    primary = (<div style={{ display: "flex", gap: 10 }}>
      <Btn variant="secondary" icon="HiPlus" onClick={() => pushToast("Add unplanned work — opens the commitment form.", "success")}>Add unplanned work</Btn>
      <Btn variant="success" icon="HiCheckCircle" disabled={!canClose}
        title={canClose ? undefined : "Set an outcome on every commitment, and link every unplanned one, before you close."}
        onClick={() => { setPlan({ ...plan, state: "RECONCILED" }); pushToast("Week closed. Plan reconciled.", "success"); }}>Close week</Btn>
    </div>);
  }

  const planned = plan.commitments.filter((c) => c.kind === "PLANNED");
  const unplanned = plan.commitments.filter((c) => c.kind === "UNPLANNED");
  const sync = window.SYNC[plan.owner];

  const renderCard = (c) => (
    <CommitmentCard key={c.id} c={c} mode={cardMode}
      onLink={(cc) => setPicker(cc)} onOutcome={setOutcome}
      onRespond={(setLocalSo) => setPicker({ ...c, _respond: setLocalSo })}
      onDelete={() => pushToast("Commitment deleted.", "success")} />
  );

  return (
    <div>
      <div className="breadcrumb">My Weekly Commit <Icon name="HiChevronRight" size={13} /><span className="here">{window.WEEK_LABEL}</span></div>
      <div className="surface surface--reading">
        <PlanLifecycleBar plan={plan} primary={primary} />

        {plan.state === "DRAFT" && plan.commitments.length === 0 ? (
          <div className="empty">
            <span className="ic"><Icon name="HiCalendar" size={22} /></span>
            <div className="h">Your week is ready to plan</div>
            <p className="g">Add the commitments you're making this week. Each one links to a Supporting Outcome so your work ladders up to the Rally Cry.</p>
            <Btn variant="primary" icon="HiPlus">Add your first commitment</Btn>
          </div>
        ) : plan.state === "RECONCILING" ? (
          <div>
            <div className="section-h"><span className="t">Planned · locked baseline</span><span className="ln" /></div>
            <div className="commit-list">{planned.map(renderCard)}</div>
            <div className="section-h"><span className="t">Unplanned · added after lock</span><span className="ln" />
              <Btn size="sm" variant="ghost" icon="HiPlus">Add unplanned work</Btn></div>
            <div className="commit-list">{unplanned.map(renderCard)}</div>
          </div>
        ) : (
          <div>
            {plan.state === "DRAFT" && (
              <div className="section-h"><span className="t">Commitments</span><span className="ln" />
                <Btn size="sm" variant="ghost" icon="HiPlus" onClick={() => pushToast("Add commitment — opens the form.", "success")}>Add commitment</Btn></div>
            )}
            <div className="commit-list">{plan.commitments.map(renderCard)}</div>
          </div>
        )}

        {sync && (
          <div style={{ marginTop: 16 }}>
            {sync.status === "FAILED" ? (
              <div className="alert-strip" style={{ background: "var(--tone-warning-bg)", borderColor: "var(--tone-warning-border)", color: "var(--tone-warning-fg)", alignItems: "flex-start" }}>
                <Icon name="HiExclamationCircle" size={16} />
                <div className="grow">
                  <div style={{ fontWeight: 500, color: "var(--ink-primary)" }}>Calendar sync failed</div>
                  <div style={{ fontSize: 12, color: "var(--ink-secondary)", marginTop: 2 }}>{sync.safeMessage} This does not affect your locked plan.</div>
                  <div style={{ fontSize: 11, fontFamily: "var(--font-mono)", color: "var(--ink-muted)", marginTop: 4 }}>Attempts: {sync.attempts} · Ref: {sync.ref}</div>
                </div>
                <Btn size="sm" variant="secondary" icon="HiRefresh" onClick={() => pushToast("Retry queued… syncing.", "success")}>Retry sync</Btn>
              </div>
            ) : (
              <Badge tone="success" icon="HiCalendar">Calendar event synced</Badge>
            )}
          </div>
        )}
      </div>

      {picker && (
        <RcdoPicker value={picker.soId}
          onClose={() => setPicker(null)}
          onSelect={(soId) => {
            if (picker._respond) { picker._respond(soId); }
            else { setSo(picker.id, soId); pushToast("Supporting Outcome linked.", "success"); }
            setPicker(null);
          }} />
      )}
      {lockOpen && (
        <LockModal plan={plan} onClose={() => setLockOpen(false)}
          onConfirm={() => { setPlan({ ...plan, state: "LOCKED", managerReview: { status: "NOT_REVIEWED" } }); setLockOpen(false); pushToast("Plan locked. Baseline frozen.", "success"); }} />
      )}
    </div>
  );
}

Object.assign(window, { WeeklyPlanView, PlanLifecycleBar });
