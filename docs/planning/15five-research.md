# What is 15Five?

> Research briefing for the **ST6 Weekly Commit Module** team. Compiled via a multi-agent web-research workflow (12 facets, each finding independently fact-verified) and weighted toward the parts of 15Five most relevant to building a replacement for its weekly-planning workflow. Pricing/leadership facts current as of early 2026; see §7 for what remains unverified.

## 1. What 15Five is

15Five is a San Francisco-based HR-tech company offering a continuous (now AI-augmented) performance-management SaaS platform for HR and people-operations teams. Rather than the annual-review model, it builds a weekly rhythm of lightweight employee check-ins, OKR/goal tracking, 1-on-1s, peer recognition, engagement surveys, and manager coaching, increasingly tied together by a manager-effectiveness thesis and an HR-analytics layer. It serves 3,000+ (more recently 3,500+) customer companies and holds strong third-party ratings (G2 ~4.6/5, Capterra ~4.7/5). The product is widely praised for its check-in workflow; the most common criticism is that value collapses if managers don't actually respond.

## 2. Origin & the "15/5" methodology

The name is the methodology: an employee spends **~15 minutes** writing a weekly check-in, and a manager spends **~5 minutes** reading it. The practice traces to "5-15 reports" pioneered by Esprit founding employee Doug Tompkins and popularized by Patagonia founder Yvon Chouinard for off-the-grid team communication.

- Founded in 2011 by David Hassell, with co-founders Shane Metcalf and Nazar Ivaniv; the product launched in March 2012. (Treat 2011 as the founding year, not 2012.)
- Philosophy: "human-centered leadership" and a science-inspired "Best-Self Management" methodology rooted in positive psychology — intrinsic motivation, growth mindset, strengths, and psychological safety (most often credited to Hassell, with Metcalf closely associated).
- Recent turbulence: acquired Kona (AI manager-coaching) in Jan 2025; Hassell returned as CEO in July 2025; leadership has since changed again (see Open Questions).

## 3. Core product capabilities

### Weekly Check-ins (the flagship workflow — most relevant to us)
The Check-in is one recurring form an employee fills out and submits, composed of stacked sections: **Pulse → Objectives → Priorities → Questions → High Fives**.

- **Cadence:** weekly by default (due Fridays); also biweekly or monthly. Section-level frequency is configurable.
- **Pulse:** every check-in opens with the verbatim sentiment question *"How did you feel at work since your last Check-in?"* on a 1–5 scale with an optional comment (lockable to manager-only). Pulse ratings inherit the check-in's visibility (manager + hierarchy above + approved viewers).
- **Priorities:** the employee marks prior priorities complete, **cycles** (carries) them forward, or removes them, then sets new ones. 15Five recommends ~3 per period for focus. Priorities can be **linked to an Objective**, after which they appear on that objective's detail page — directly connecting short-term tasks to long-term goals.
- **Objectives section:** if the employee owns objectives, the check-in requires a status (**On track / Behind / At risk**) for each, and allows inline Key Result value updates. A check-in cannot be submitted without a status for every owned objective.
- **Questions** (the "heart of 15Five"): a pre-built Question Bank plus custom questions, each settable to a specific frequency and as required. Roadblock/morale prompts are standard.
- **Manager actions on a submitted check-in:** react, comment, flag answers for follow-up, **pass answers up the hierarchy**, add items to the 1-on-1 agenda, and log Wins & Challenges. This is the engine feeding weekly manager-employee dialogue.
- Inline tools: @mentions, attachments, GIFs, Markdown, per-answer private lock.

### Objectives / OKRs (most relevant to us)
A built-in OKR module inside the Perform product.

- **Three scope levels:** a person (individual), a group (team/department), or the whole company. Each objective requires an **owner** and ≥1 Key Result.
- **Hierarchical alignment:** an objective can be linked to a **parent objective** (admin-enabled), forming a parent-child relationship. A child's start/end dates must fall within the parent's, and admins can toggle whether child progress **rolls up** into the parent's progress, with weighting controls.
- **Key Results:** up to 5 per objective; measurement types are Percentage, Currency, Number, or Completed/Not completed; optional weights (equal by default).
- **Status:** On track / Behind / At risk, set from the Objectives dashboard or inside a check-in.
- **Objectives dashboard:** nested (expandable parent-child tree), flat, and chart views with color-coded status rollups (green = on track, yellow = behind, red = at risk, grey = no status); filter by owner, type, date range, state.
- Configurable cycles (monthly/quarterly/annually/custom), visibility controls, granular role-based create/edit permissions, and bulk CSV import.
- *Caveat:* reviewers find OKR depth limited versus dedicated OKR tools, and the exact progress-rollup math is not publicly documented.

### Performance Reviews ("Best-Self Review")
- A **review cycle** defines participants, review types, timeline, and question templates; repeats Monthly/Quarterly/Bi-annually/Annually or runs once (15Five intentionally promotes more frequent cadences).
- Four review types — **Self, Manager, Peer, Upward** — combinable; all four = a **Full 360**. Peer reviews are participant- or peer-initiated (~3–5 reviewers recommended).
- Question templates can include Competency Assessments, a Manager Effectiveness Assessment, and a **Private Manager Assessment** (promotion/comp readiness, hidden from the employee).
- **Performance Ratings+** converts weighted self/manager answers into a 0–100 score mapped via a rubric (default Above/At/Below Level), feeding **Calibrations** (drag-and-drop 9-box Talent Matrix) and the HR Outcomes Dashboard.
- A **"Resources for Your Review"** panel surfaces past check-ins, High Fives, Wins & Challenges, feedback, and objectives to reduce recency bias.

### Engagement Surveys ("Engage")
- A psychometrically validated engagement score across three dimensions — **Force, Feeling, Focus** — from 7 core statements on a 5-point Likert scale, contextualized by **17 named drivers** (psychological safety, meaning, autonomy, fairness, manager, etc.).
- Survey types: Total Engagement (recommended), Engagement Score pulse, Engagement+Drivers, Manager Effectiveness, eNPS (0–10 recommend question + open follow-up), topic-based, lifecycle, and custom.
- **Benchmarking** (percentile vs. similar orgs over trailing 12 months), heat maps, trend/flow views, AI open-text theming, an AI **Predictive Impact Model**, and AI-assisted **Action Plans**.

### 1-on-1s & High Fives (most relevant to us)
- **1-on-1s:** a shared, real-time agenda co-edited by manager and report — talking points (public/private), notes, and action items tracked in 15Five's Actions system. Talking points can be pulled directly from check-ins (by employee or manager), from a Talking Point Bank, or via Slack. Unfinished items **auto carry-over**; ending a meeting sends both parties a summary email. Cadence: weekly/biweekly/monthly/bimonthly.
- **High Fives:** peer recognition sent to individuals, groups, the whole company, or external emails; embedded as a prompt **inside the weekly check-in**, taggable to company values (feeding leaderboards), public or private, and surfaced as review resources.

### Manager Effectiveness
- **Transform** (launched 2021): in-house coaching, on-demand training, microlearning, and curated Learning Tracks/Journeys ("Learn, Practice, Apply").
- **Manager Effectiveness Indicator (MEI):** a dynamic 0–100 score combining Manager Effectiveness Review Cycles and Engagement Surveys (configurable weighting), with fallback to historical platform-usage data; built from research on 60,000+ managers and assessing **eight competencies**.
- **Kona by 15Five:** AI manager-effectiveness coach (acquired Jan 2025, relaunched May 2025) that joins Zoom/Google Meet and nudges via Slack to coach in the flow of work.

### Analytics
- **My Team Dashboard** (manager): single-screen roll-up of direct reports' check-ins, Pulse, 1-on-1s, and objectives, with drill-down into profiles, Wins & Challenges, and unreviewed check-ins.
- **HR Outcomes Dashboard** (HR/leadership): connects HRIS data with performance, engagement, and compensation data; tracks Engagement, Performance, Turnover (+ Manager Effectiveness as the key driver); filterable by demographics and designations; AI features (Spark AI, Predictive Impact Model, predictive turnover, AI action plans). Exports CSV/XLSX/PDF/PPTX.
- *Caveat:* reviewers find dashboards can be cluttered and custom reporting limited.

## 4. Platform & integrations

- **HRIS Connector:** no-code, Merge.dev-powered sync across 40+ systems (ADP Workforce Now, BambooHR, Workday, Dayforce/Ceridian, Paycor, UKG, Gusto, Rippling, SAP SuccessFactors), with provisioning/deprovisioning, field mapping, and daily sync. **15Five ships no built-in HRIS** — it integrates with the customer's.
- **Identity/security:** SAML 2.0 SSO (Okta, Entra/Azure AD, ADFS, OneLogin, Google), SCIM 2.0 provisioning, MFA, SOC 2 Type II, ISO 27001-compliant data centers, GDPR/PIPEDA, US data hosting, TLS in transit + encryption at rest.
- **REST Public API** (admin-enabled keys, rate-limited; keys expire after 1 year) and native iOS/Android apps.
- **Calendar — important constraint for us:** 1-on-1 calendar sync is **Google Calendar only** (and cannot create recurring 1-on-1s). There is **no native Outlook/Microsoft 365 calendar integration** — Outlook users paste the agenda link into a manually created event. Microsoft Teams integration is limited to High Fives/recognition and notifications, not calendar.

## 5. Pricing & market positioning

15Five publishes list pricing openly (billed annually):

- **Engage — $4/user/month:** engagement surveys, action planning, heat maps, benchmarking.
- **Perform — $11/user/month** (marketed "Most Popular"): everything in Engage plus check-ins, OKRs/Goals, reviews, 360s, Talent Matrix, career paths, HR Outcomes Dashboard, MEI.
- **Total Platform — $16/user/month:** everything above plus Transform microlearning and enhanced security.
- Tiers are cumulative. Paid add-ons (Kona, Coaching, Content, Compensation, Amaya AI) layer on. A 14-day free trial exists; no permanent free version.

**Positioning:** a continuous/manager-enablement performance-management platform; analyst Josh Bersin historically placed it at the front of the "manager enablement" sub-category. Sweet spot is the mid-market. Closest rival is **Lattice**; it also competes with Culture Amp, Leapsome, and Betterworks.

## 6. Relevance to our Weekly Commit Module

We're building a Weekly Commit Module: weekly commitments linked to a strategic goal hierarchy (RCDO: Rally Cries → Defining Objectives → Outcomes), plus reconciliation and manager dashboards. 15Five is the closest existing analog for the workflow, and these mappings should anchor our design:

| 15Five concept | Our equivalent | What to borrow / what's different |
|---|---|---|
| **Weekly Check-in → Priorities** (set ~3, mark complete, **cycle** forward, remove) | **Weekly Commit** | Their carry-forward ("cycle") + close-out-prior pattern is essentially our **reconciliation** primitive. Borrow the "close last period before opening the new one" UX. |
| **Priority → Objective linking** (link surfaces on objective detail page) | **Commit → RCDO link** | This is the core alignment mechanic. 15Five links a single priority to a single objective; our **RCDO hierarchy** likely needs richer multi-level linkage. |
| **Objectives parent-child alignment** with configurable **progress rollup** + weights | **RCDO hierarchy** | Directly maps to our hierarchy. Note their rollup is admin-toggleable per child and weighted; we should decide rollup semantics explicitly (their exact math is undocumented). |
| **On track / Behind / At risk** status, required at submit | Commit/goal status gating | Borrow the "can't submit without a status on every owned goal" forcing function — it guarantees data completeness for reconciliation. |
| **Manager actions on a check-in** (react, comment, flag, **pass up hierarchy**, push to 1-on-1) + **My Team Dashboard** roll-up | **Manager dashboard** | Their two-tier model (manager My Team roll-up vs. HR Outcomes cross-system view) is a good template. "Pass up the hierarchy" is a useful pattern for our reconciliation escalation. |
| Objectives **dashboard** (nested tree, color-coded rollup, filters) | Manager/leadership goal views | Borrow the expandable parent-child tree with status colors for our hierarchy visualization. |
| Weekly cadence (default Fri), section-level frequency config | Commit cadence | Default to a fixed weekly close; make cadence configurable. |

**Cautions to design around (from 15Five's known weaknesses):**
- Value collapses if managers don't respond — build review/acknowledgement into the loop, not as optional.
- Question/survey fatigue sets in over time — keep the commit form lightweight and avoid static repetition.
- OKR depth and goal-input visibility can feel limited/clunky — our differentiator should be clean hierarchy navigation and explicit reconciliation.
- No native Outlook/M365 calendar integration is a real gap — our PRD calls for Outlook Graph API integration, so **native Outlook support is a competitive opening**, not just parity.

## 7. Open questions / unverified

- **Pulse 1–5 scale labels:** exact per-point labels/meaning are not documented (only that lower = less happy).
- **Post-submission editing:** whether/how a submitted check-in can be edited is unconfirmed.
- **Progress-rollup algorithm:** how KR % and weighted child objectives roll up into a parent's numeric score is not publicly documented.
- **Exact verbatim prompts:** some standard question/priority prompt strings (e.g., the exact "What do you intend to accomplish…" wording) are inferred, not verbatim-confirmed.
- **Current leadership:** sources conflict — Hassell returned as CEO (July 2025), but one source indicates Dr. Jeff Smith as CEO (reportedly May 2026). Treat current CEO as uncertain.
- **Customer/user reach:** "3,000+ vs 3,500+ customers" and "~250,000 / 400,000 employees/users" vary by source and date; the user-reach figure is unverified.
- **Funding:** total raised is reported in a ~$94–98M range; the confirmed event is a $52M Series C (July 2022, led by Quad Partners). A claimed "$100M Series D at ~$1B valuation" is **refuted/unreliable**.
- **Layoffs:** multiple reported rounds are low-confidence (aggregator/review sourcing, no primary press release).
- **Plan-tier gating** for SSO/SCIM and some features is not fully confirmed; pricing was current as of early 2026 and is subject to change.
- **Full Potential Index / Engagement+:** an earlier (2020–21) framework co-created with Dr. Scott Barry Kaufman; unclear whether it remains the live engagement-score basis vs. the current Force/Feeling/Focus + 17-drivers model.
- **Predictive Impact Model dataset figures** (6 yrs / ~30M responses / ~600K surveys) are vendor-reported, not independently audited.
