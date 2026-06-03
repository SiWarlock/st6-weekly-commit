import { useEffect, useRef, useState, type ReactNode } from 'react';
import {
  setAccessTokenProvider,
  setDemoAuthHeaderApplier,
} from '../app/authAccessor';
import {
  DEMO_PERSONAS,
  DEFAULT_PERSONA_ID,
  DemoIdentityContext,
} from './demoIdentity';

interface DemoIdentityProviderProps {
  children: ReactNode;
}

/**
 * STANDALONE-ONLY demo identity. Supplies `getAccessToken()` + the active
 * persona id and wires BOTH into the 9.1 accessor seam. Tree-shaken out of the
 * exposed remote build (REQ-I-008) — never imported by `src/remote/`.
 */
export function DemoIdentityProvider({ children }: DemoIdentityProviderProps) {
  const [personaId, setPersonaId] = useState<string>(DEFAULT_PERSONA_ID);
  // A ref keeps the seam closures reading the CURRENT persona across switches
  // without re-registering the providers on every change.
  const personaRef = useRef(personaId);
  personaRef.current = personaId;

  // Standalone is the single owner of the global accessor seam, so cleanup
  // clears it on unmount (best-effort — fine for the app-lifetime provider).
  // The applier closure OWNS the X-Demo-Employee-Id literal (REQ-I-008: it lives
  // in src/standalone/ only) and keeps the truthy guard so an empty persona
  // degrades to no header (backend DEMO_AUTH_ENABLED 403), matching pre-split.
  useEffect(() => {
    setDemoAuthHeaderApplier((headers) => {
      const id = personaRef.current;
      if (id) {
        headers.set('X-Demo-Employee-Id', id);
      }
    });
    setAccessTokenProvider(async () => `demo-token:${personaRef.current}`);
    return () => {
      setDemoAuthHeaderApplier(null);
      setAccessTokenProvider(null);
    };
  }, []);

  return (
    <DemoIdentityContext.Provider
      value={{ personas: DEMO_PERSONAS, personaId, setPersonaId }}
    >
      {children}
    </DemoIdentityContext.Provider>
  );
}
