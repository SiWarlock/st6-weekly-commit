import type { IconType } from 'react-icons';
import {
  HiPencilAlt,
  HiLockClosed,
  HiClipboardCheck,
  HiCheckCircle,
  HiMinusCircle,
  HiOutlineEye,
  HiExclamationCircle,
  HiClock,
  HiExclamation,
  HiBan,
  HiQuestionMarkCircle,
  HiArrowNarrowRight,
  HiRefresh,
  HiCloudUpload,
} from 'react-icons/hi';

/**
 * The §4.2 six-tone taxonomy — the SINGLE SOURCE OF VISUAL TRUTH for status/risk
 * rendering, ported verbatim from the Cadence `atoms.jsx` PLAN_STATE/REVIEW/RISK
 * maps. Every atom + later view consumes these; never re-map a status inline.
 * Typed `Record<string, …>` so an unknown/absent value resolves to `undefined`
 * (the atoms then render nothing — no throw).
 */
export type Tone =
  | 'neutral'
  | 'info'
  | 'success'
  | 'warning'
  | 'failure'
  | 'accent';

export interface TaxonomyEntry {
  tone: Tone;
  icon: IconType;
  label: string;
  /** Ring (outline) variant — distinguishes the 2nd of a same-color pair. */
  ring?: boolean;
}

export const PLAN_STATE_TAXONOMY: Record<string, TaxonomyEntry> = {
  DRAFT: { tone: 'neutral', icon: HiPencilAlt, label: 'Draft' },
  LOCKED: { tone: 'info', icon: HiLockClosed, label: 'Locked' },
  RECONCILING: {
    tone: 'warning',
    icon: HiClipboardCheck,
    label: 'Reconciling',
  },
  RECONCILED: { tone: 'success', icon: HiCheckCircle, label: 'Reconciled' },
  NOT_STARTED: { tone: 'neutral', icon: HiMinusCircle, label: 'Not started' },
};

export const REVIEW_STATUS_TAXONOMY: Record<string, TaxonomyEntry> = {
  NOT_REVIEWED: { tone: 'neutral', icon: HiOutlineEye, label: 'Not reviewed' },
  REVIEWED_WITH_DISPUTES: {
    tone: 'warning',
    icon: HiExclamationCircle,
    label: 'Reviewed · disputes',
  },
  REVIEWED: { tone: 'success', icon: HiCheckCircle, label: 'Reviewed' },
  // Derived (read-time) overlay — never a stored status (§3).
  OVERDUE: { tone: 'failure', icon: HiClock, label: 'Overdue' },
};

export const RISK_TAXONOMY: Record<string, TaxonomyEntry> = {
  MISALIGNED: { tone: 'failure', icon: HiExclamation, label: 'Misaligned' },
  BLOCKED: { tone: 'failure', icon: HiBan, label: 'Blocked', ring: true },
  OVERDUE_REVIEW: { tone: 'failure', icon: HiClock, label: 'Overdue review' },
  NEEDS_REVIEW: {
    tone: 'warning',
    icon: HiQuestionMarkCircle,
    label: 'Needs review',
  },
  CARRY_FORWARD: {
    tone: 'warning',
    icon: HiArrowNarrowRight,
    label: 'Carry-forward',
    ring: true,
  },
  UNREVIEWED: { tone: 'neutral', icon: HiOutlineEye, label: 'Unreviewed' },
};

/**
 * The §10 Outlook-sync status taxonomy — the SINGLE SOURCE OF VISUAL TRUTH for
 * `SyncStatus` rendering (B.1). `FAILED` is the user-visible retryable terminal
 * (failure tone); in-flight states are neutral/info. Unknown/absent → `undefined`
 * (the badge renders nothing). The `safeMessage` warning is rendered separately
 * (rule #7 — never the failure code/secrets).
 */
export const SYNC_STATUS_TAXONOMY: Record<string, TaxonomyEntry> = {
  PENDING_PUBLISH: {
    tone: 'neutral',
    icon: HiCloudUpload,
    label: 'Pending sync',
  },
  QUEUED: { tone: 'info', icon: HiClock, label: 'Queued' },
  SYNCING: { tone: 'info', icon: HiRefresh, label: 'Syncing' },
  SYNCED: { tone: 'success', icon: HiCheckCircle, label: 'Synced' },
  FAILED: { tone: 'failure', icon: HiExclamationCircle, label: 'Sync failed' },
  RETRY_REQUESTED: {
    tone: 'warning',
    icon: HiRefresh,
    label: 'Retry requested',
  },
};
