import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { Avatar } from './Avatar';
import { initialsOf, toneForName } from '../lib/avatar';

// ST.8b — the initials avatar (reused by the command-center rows + the ST.8a
// identity slot). Initials derive from the name; the tone is a DETERMINISTIC
// hash→token-tone (no hex), so the same person is always the same color.

describe('ST.8b Avatar — initials + deterministic tone', () => {
  it('initials_from_name: derives up-to-two uppercase initials from the display name', () => {
    expect(initialsOf('Ivy Chen')).toBe('IC');
    expect(initialsOf('Morgan Lee')).toBe('ML');
    expect(initialsOf('Cher')).toBe('C');
    expect(initialsOf('  ada  lovelace ')).toBe('AL');
  });

  it('tone_is_deterministic: the same name always maps to the same tone; the tone is one of the six tokens', () => {
    const tones = [
      'neutral',
      'info',
      'success',
      'warning',
      'failure',
      'accent',
    ];
    const t1 = toneForName('Ivy Chen');
    const t2 = toneForName('Ivy Chen');
    expect(t1).toBe(t2);
    expect(tones).toContain(t1);
  });

  it('renders_initials_and_tone: the avatar renders the initials text + a stable data-tone for the name', () => {
    render(<Avatar name="Ivy Chen" />);
    const el = screen.getByText('IC');
    expect(el).toBeInTheDocument();
    expect(el).toHaveAttribute('data-tone', toneForName('Ivy Chen'));
  });
});
