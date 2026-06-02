/* =========================================================================
   ST6 Weekly Commit — Icon + atom components (§5.1)
   Skin lives in components.css; these emit the right classes + map enums.
   ========================================================================= */

function Icon({ name, size = 16, strokeWidth = 1.75, className, style }) {
  const map = window.WC_ICONS || {};
  const ic = map[name] || map.HiInformationCircle;
  if (!ic) return null;
  if (ic.fill) {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor"
        className={className} style={style} aria-hidden="true"><path d={ic.d} /></svg>
    );
  }
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round"
      className={className} style={style} aria-hidden="true"><path d={ic.d} /></svg>
  );
}

/* ---------- Avatar ---------- */
function Avatar({ person, size = 26 }) {
  const p = typeof person === "string" ? window.PEOPLE[person] : person;
  if (!p) return null;
  const tone = p.tone === "brand" ? "brand" : p.tone;
  const bg = tone === "brand" ? "var(--brand-soft)" : `var(--tone-${tone}-bg)`;
  const fg = tone === "brand" ? "var(--brand-400)" : `var(--tone-${tone}-fg)`;
  return (
    <span className="wc-avatar" style={{ width: size, height: size, background: bg, color: fg, fontSize: size * 0.42 }}>
      {p.initials}
    </span>
  );
}

/* ---------- Badge / StatusPill ---------- */
function Badge({ tone = "neutral", icon, children, ring, xs, style }) {
  const cls = ["wc-badge", "is-" + tone, ring ? "wc-badge--ring" : "", xs ? "wc-badge--xs" : ""]
    .filter(Boolean).join(" ");
  return (
    <span className={cls} style={style}>
      {icon && <Icon name={icon} size={xs ? 12 : 14} />}
      {children}
    </span>
  );
}

function CountChip({ icon, tone = "neutral", children }) {
  const style = tone !== "neutral"
    ? { background: `var(--tone-${tone}-bg)`, color: `var(--tone-${tone}-fg)`, borderColor: `var(--tone-${tone}-border)` }
    : undefined;
  return <span className="wc-count" style={style}>{icon && <Icon name={icon} size={12} />}{children}</span>;
}

function Dot({ tone }) { return <span className={"wc-dot is-" + tone} />; }

/* ---------- enum → pill maps ---------- */
const PLAN_STATE = {
  DRAFT:       { tone: "neutral", icon: "HiPencilAlt",      label: "Draft" },
  LOCKED:      { tone: "info",    icon: "HiLockClosed",     label: "Locked" },
  RECONCILING: { tone: "warning", icon: "HiClipboardCheck", label: "Reconciling" },
  RECONCILED:  { tone: "success", icon: "HiCheckCircle",    label: "Reconciled" },
  NOT_STARTED: { tone: "neutral", icon: "HiMinusCircle",    label: "Not started" },
};
const REVIEW = {
  NOT_REVIEWED:           { tone: "neutral", icon: "HiOutlineEye",        label: "Not reviewed" },
  REVIEWED_WITH_DISPUTES: { tone: "warning", icon: "HiExclamationCircle", label: "Reviewed · disputes" },
  REVIEWED:               { tone: "success", icon: "HiCheckCircle",       label: "Reviewed" },
  OVERDUE:                { tone: "failure", icon: "HiClock",             label: "Overdue" },
};
const OUTCOME = {
  COMPLETED:           { tone: "success", icon: "HiCheckCircle",     label: "Completed" },
  PARTIALLY_COMPLETED: { tone: "warning", icon: "HiAdjustments",     label: "Partial" },
  BLOCKED:             { tone: "failure", icon: "HiBan",             label: "Blocked" },
  CANCELED:            { tone: "neutral", icon: "HiXCircle",         label: "Canceled" },
  CARRIED_FORWARD:     { tone: "info",    icon: "HiArrowNarrowRight",label: "Carried forward" },
};
const RISK = {
  MISALIGNED:     { tone: "failure", icon: "HiExclamation",        label: "Misaligned",     ring: false },
  BLOCKED:        { tone: "failure", icon: "HiBan",                label: "Blocked",        ring: true },
  OVERDUE_REVIEW: { tone: "failure", icon: "HiClock",              label: "Overdue review", ring: false },
  NEEDS_REVIEW:   { tone: "warning", icon: "HiQuestionMarkCircle", label: "Needs review",   ring: false },
  CARRY_FORWARD:  { tone: "warning", icon: "HiArrowNarrowRight",   label: "Carry-forward",  ring: true },
  UNREVIEWED:     { tone: "neutral", icon: "HiOutlineEye",         label: "Unreviewed",     ring: false },
};
const WORKTYPE = {
  STRATEGIC:   { tone: "info",    icon: "HiSparkles",   label: "Strategic" },
  MAINTENANCE: { tone: "neutral", icon: "HiCog",        label: "Maintenance" },
  BLOCKER:     { tone: "failure", icon: "HiBan",        label: "Blocker" },
  UNPLANNED:   { tone: "accent",  icon: "HiPlusCircle", label: "Unplanned" },
};
const ALIGNMENT = {
  ALIGNED:      { tone: "success", cls: "aligned",     label: "Aligned" },
  NEEDS_REVIEW: { tone: "warning", cls: "needsreview", label: "Needs review" },
  MISALIGNED:   { tone: "failure", cls: "misaligned",  label: "Misaligned" },
};

function PlanStatePill({ value, xs }) { const m = PLAN_STATE[value]; return <Badge tone={m.tone} icon={m.icon} xs={xs}>{m.label}</Badge>; }
function ReviewPill({ value, xs })    { const m = REVIEW[value];      return <Badge tone={m.tone} icon={m.icon} xs={xs}>{m.label}</Badge>; }
function OutcomePill({ value, xs })   { const m = OUTCOME[value];     return <Badge tone={m.tone} icon={m.icon} xs={xs}>{m.label}</Badge>; }
function RiskBadge({ value, count, xs = true }) {
  const m = RISK[value]; if (!m) return null;
  return <Badge tone={m.tone} icon={m.icon} ring={m.ring} xs={xs}>{m.label}{count ? ` ${count}` : ""}</Badge>;
}

/* ---------- chess-layer atoms ---------- */
function PriorityTag({ value }) { return <span className={"wc-pri " + value.toLowerCase()}>{value}</span>; }

function WorkTypeTag({ value }) {
  const m = WORKTYPE[value];
  return <span className={"wc-worktype is-" + m.tone}><Icon name={m.icon} size={13} />{m.label}</span>;
}

function ConfidenceMeter({ value }) {
  const cls = value.toLowerCase();
  const cap = value.charAt(0) + value.slice(1).toLowerCase();
  return (
    <span className={"wc-conf " + cls} title={`Confidence: ${cap}`}>
      <span className="wc-conf-bars"><i className="wc-conf-seg" /><i className="wc-conf-seg" /><i className="wc-conf-seg" /></span>
      <span className="wc-conf-label">{cap}</span>
    </span>
  );
}

function AlignmentChip({ value }) {
  const m = ALIGNMENT[value];
  return <span className={"wc-align " + m.cls}><span className={"wc-dot is-" + m.tone} />{m.label}</span>;
}

/* ---------- RCDO breadcrumb (read-only RC › DO › SO) ---------- */
function RcdoBreadcrumb({ soId, onChange, compact }) {
  const bc = window.breadcrumb(soId);
  if (!bc) {
    return (
      <div className="wc-rcdo wc-rcdo--missing">
        <Icon name="HiExclamationCircle" size={14} />
        <span>No Supporting Outcome linked — required before you can lock</span>
      </div>
    );
  }
  return (
    <div className="wc-rcdo">
      <span className="wc-rcdo-do">{bc.doTitle}</span>
      <Icon name="HiChevronRight" size={13} className="wc-rcdo-sep" />
      <span className="wc-rcdo-so"><b>{bc.soId}</b> · {bc.soTitle}</span>
      {onChange && <button className="wc-rcdo-change" onClick={onChange}>Change ›</button>}
    </div>
  );
}

/* ---------- Button ---------- */
function Btn({ variant = "secondary", size, icon, iconRight, children, disabled, title, onClick }) {
  const cls = ["wc-btn", "wc-btn--" + variant, size === "sm" ? "wc-btn--sm" : "", disabled ? "is-disabled" : ""]
    .filter(Boolean).join(" ");
  return (
    <button className={cls} disabled={disabled} title={title} onClick={disabled ? undefined : onClick}>
      {icon && <Icon name={icon} size={size === "sm" ? 14 : 16} />}
      {children}
      {iconRight && <Icon name={iconRight} size={size === "sm" ? 14 : 16} />}
    </button>
  );
}

Object.assign(window, {
  Icon, Avatar, Badge, CountChip, Dot,
  PlanStatePill, ReviewPill, OutcomePill, RiskBadge,
  PriorityTag, WorkTypeTag, ConfidenceMeter, AlignmentChip,
  RcdoBreadcrumb, Btn,
  PLAN_STATE, REVIEW, OUTCOME, RISK, WORKTYPE, ALIGNMENT,
});
