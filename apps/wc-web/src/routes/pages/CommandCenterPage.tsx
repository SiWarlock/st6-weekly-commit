import { CommandCenter } from '../../features/manager/CommandCenter';

/**
 * `/manager/command-center` route module — the manager direct-report alignment
 * command center (9.9). Renders the real `CommandCenter` (replaces the 9.4
 * placeholder). The route is manager-gated in `AppRoutes` (REQ-UX-005).
 */
export default function CommandCenterPage() {
  return <CommandCenter />;
}
