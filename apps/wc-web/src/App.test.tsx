import { render } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { ThemeProvider } from './app/theme/ThemeProvider';
import App from './App';

describe('shell (F0)', () => {
  it('shell_mounts: <App/> renders without error under the providers', () => {
    const { container } = render(
      <ThemeProvider>
        <App />
      </ThemeProvider>,
    );
    // App ships a token-probe element (data-cy) used by theming smoke checks.
    expect(container.querySelector('[data-cy="token-probe"]')).not.toBeNull();
  });
});
