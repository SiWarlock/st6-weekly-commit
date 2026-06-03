/**
 * Persona → demo identity map for the BDD suite.
 *
 * Demo-mode identity contract (ARCHITECTURE F.1): when `DEMO_AUTH_ENABLED=true` the request header
 * `X-Demo-Employee-Id: <employee uuid>` IS the principal. The value must be an existing
 * `employee.id`. Keys below are the Appendix-E roster first names (unambiguous — one each).
 *
 * RECONCILE (Carry-forward, origin 11.7): the `employeeId` UUIDs are placeholder-but-shaped — they
 * match the `'a0000000-0000-0000-0000-0000000000NN'` literal shape the seed migrations use, but the
 * EXACT constants live in the Flyway seed (`V5__seed_personas_and_relationships.sql`), which is out
 * of this slice's scope. Replace these with the real seed literals when the seed/Compose stack is
 * runnable. This is an authored-not-green slice: persona identity is a typed seam, not a live binding.
 */
export type PersonaKey = 'Dana' | 'Priya' | 'Marco' | 'Aisha' | 'Tomas' | 'Grace' | 'Sam';

export type ReportCode = 'M' | 'R1' | 'R2' | 'R3' | 'R4' | 'R5' | 'R6';

export interface Persona {
  readonly key: PersonaKey;
  readonly reportCode: ReportCode;
  readonly displayName: string;
  readonly email: string;
  readonly role: 'MANAGER' | 'IC';
  /** X-Demo-Employee-Id value (= employee.id). Placeholder-shaped — see RECONCILE note. */
  readonly employeeId: string;
}

export const PERSONAS: Record<PersonaKey, Persona> = {
  Dana: {
    key: 'Dana',
    reportCode: 'M',
    displayName: 'Dana Okafor',
    email: 'dana.okafor@st6demo.com',
    role: 'MANAGER',
    employeeId: 'a0000000-0000-0000-0000-0000000000d0',
  },
  Priya: {
    key: 'Priya',
    reportCode: 'R1',
    displayName: 'Priya Raman',
    email: 'priya.raman@st6demo.com',
    role: 'IC',
    employeeId: 'a0000000-0000-0000-0000-000000000001',
  },
  Marco: {
    key: 'Marco',
    reportCode: 'R2',
    displayName: 'Marco Bellini',
    email: 'marco.bellini@st6demo.com',
    role: 'IC',
    employeeId: 'a0000000-0000-0000-0000-000000000002',
  },
  Aisha: {
    key: 'Aisha',
    reportCode: 'R3',
    displayName: 'Aisha Khan',
    email: 'aisha.khan@st6demo.com',
    role: 'IC',
    employeeId: 'a0000000-0000-0000-0000-000000000003',
  },
  Tomas: {
    key: 'Tomas',
    reportCode: 'R4',
    displayName: 'Tomas Novak',
    email: 'tomas.novak@st6demo.com',
    role: 'IC',
    employeeId: 'a0000000-0000-0000-0000-000000000004',
  },
  Grace: {
    key: 'Grace',
    reportCode: 'R5',
    displayName: 'Grace Liu',
    email: 'grace.liu@st6demo.com',
    role: 'IC',
    employeeId: 'a0000000-0000-0000-0000-000000000005',
  },
  Sam: {
    key: 'Sam',
    reportCode: 'R6',
    displayName: 'Sam Carter',
    email: 'sam.carter@st6demo.com',
    role: 'IC',
    employeeId: 'a0000000-0000-0000-0000-000000000006',
  },
};

/**
 * Resolve a Gherkin persona token (e.g. `"Sam"`) to its Persona record.
 * Throws on an unknown key so a typo in a feature file fails loudly rather than silently logging in
 * as nobody.
 */
export function personaByKey(key: string): Persona {
  const persona = (PERSONAS as Record<string, Persona | undefined>)[key];
  if (persona === undefined) {
    throw new Error(
      `Unknown persona "${key}". Valid keys: ${Object.keys(PERSONAS).join(', ')} (Appendix E roster).`,
    );
  }
  return persona;
}
