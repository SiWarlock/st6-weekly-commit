import { createContext, useContext } from 'react';

export interface DemoPersona {
  id: string;
  label: string;
  role: 'IC' | 'MANAGER';
}

// Demo personas. NOTE: these ids must align with the V5 seed employee UUIDs at
// Phase 10/11 (E2E) — the demo breaks if they don't match. ST.7a expanded the IC
// set to one persona per plan lifecycle state (DRAFT/LOCKED/RECONCILING/
// RECONCILED) so the MSW mock layer can show EVERY state live by switching
// persona; the manager (Morgan) manages these four ICs as direct reports.
export const DEMO_PERSONAS: DemoPersona[] = [
  { id: 'demo-employee-ic-1', label: 'Ivy Chen (IC · draft)', role: 'IC' },
  { id: 'demo-employee-ic-2', label: 'Ravi Patel (IC · locked)', role: 'IC' },
  {
    id: 'demo-employee-ic-3',
    label: 'Lena Ortiz (IC · reconciling)',
    role: 'IC',
  },
  {
    id: 'demo-employee-ic-4',
    label: 'Tom Becker (IC · reconciled)',
    role: 'IC',
  },
  { id: 'demo-employee-mgr-1', label: 'Morgan Lee (Manager)', role: 'MANAGER' },
];

export const DEFAULT_PERSONA_ID = 'demo-employee-ic-1';

export interface DemoIdentityContextValue {
  personas: DemoPersona[];
  personaId: string;
  setPersonaId: (id: string) => void;
}

export const DemoIdentityContext =
  createContext<DemoIdentityContextValue | null>(null);

export function useDemoIdentity(): DemoIdentityContextValue {
  const ctx = useContext(DemoIdentityContext);
  if (ctx === null) {
    throw new Error(
      'useDemoIdentity must be used within a DemoIdentityProvider',
    );
  }
  return ctx;
}
