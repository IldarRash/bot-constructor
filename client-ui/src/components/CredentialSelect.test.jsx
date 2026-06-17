import React, { useState } from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';

import CredentialSelect from './CredentialSelect';

const CREDS = [
  { id: 'tg1', name: 'Prod TG', type: 'telegramApi' },
  { id: 'an1', name: 'AI key', type: 'anthropicApi' },
  { id: 'hh1', name: 'Header auth', type: 'httpHeaderAuth' },
  { id: 'hb1', name: 'Bearer auth', type: 'httpBearerAuth' },
];

describe('CredentialSelect', () => {
  it('lists only credentials whose type matches the node type, plus None (inline)', () => {
    render(
      <CredentialSelect nodeType="telegramSend" credentials={CREDS} value="" onChange={() => {}} />,
    );
    expect(screen.getByText('None (inline)')).toBeTruthy();
    expect(screen.getByRole('option', { name: /Prod TG/ })).toBeTruthy();
    // Anthropic credential is not offered to a Telegram node.
    expect(screen.queryByRole('option', { name: /AI key/ })).toBeNull();
  });

  it('offers both http credential types to an httpRequest node', () => {
    render(
      <CredentialSelect nodeType="httpRequest" credentials={CREDS} value="" onChange={() => {}} />,
    );
    expect(screen.getByRole('option', { name: /Header auth/ })).toBeTruthy();
    expect(screen.getByRole('option', { name: /Bearer auth/ })).toBeTruthy();
    expect(screen.queryByRole('option', { name: /Prod TG/ })).toBeNull();
  });

  it('reports the chosen credential id on change', () => {
    const onChange = vi.fn();
    render(
      <CredentialSelect nodeType="telegramSend" credentials={CREDS} value="" onChange={onChange} />,
    );
    fireEvent.change(screen.getByLabelText('Credential'), { target: { value: 'tg1' } });
    expect(onChange).toHaveBeenCalledWith('tg1');
  });

  it('shows which credential is bound when a value is selected', () => {
    render(
      <CredentialSelect nodeType="telegramSend" credentials={CREDS} value="tg1" onChange={() => {}} />,
    );
    expect(screen.getByText(/Using/)).toBeTruthy();
    expect(screen.getByText('Prod TG')).toBeTruthy();
  });
});

// Mirrors the inspector wiring: selecting a credential sets data.credentialId and hides the inline
// secret field; selecting None shows it again.
function InspectorHarness() {
  const [data, setData] = useState({ credentialId: '', botToken: '' });
  return (
    <div>
      <CredentialSelect
        nodeType="telegramSend"
        credentials={CREDS}
        value={data.credentialId}
        onChange={(credentialId) => setData((d) => ({ ...d, credentialId }))}
      />
      {!data.credentialId && (
        <input aria-label="Bot token" type="password" value={data.botToken} readOnly />
      )}
    </div>
  );
}

describe('inspector credential binding', () => {
  it('hides the inline secret field once a credential is selected and shows it on None', () => {
    render(<InspectorHarness />);

    // Inline field visible by default.
    expect(screen.getByLabelText('Bot token')).toBeTruthy();

    // Bind a credential -> inline field hidden.
    fireEvent.change(screen.getByLabelText('Credential'), { target: { value: 'tg1' } });
    expect(screen.queryByLabelText('Bot token')).toBeNull();

    // Back to None (inline) -> inline field shown again.
    fireEvent.change(screen.getByLabelText('Credential'), { target: { value: '' } });
    expect(screen.getByLabelText('Bot token')).toBeTruthy();
  });
});
