import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

// Mock the credentials API so the panel runs without a gateway.
vi.mock('../api/credentials', () => ({
  listCredentials: vi.fn(),
  createCredential: vi.fn(),
  deleteCredential: vi.fn(),
}));

import CredentialsPanel from './CredentialsPanel';
import { listCredentials, createCredential, deleteCredential } from '../api/credentials';

const VIEWS = [
  { id: 'c1', name: 'Prod TG', type: 'telegramApi', createdAt: '2026-06-16T10:00:00Z' },
  { id: 'c2', name: 'AI key', type: 'anthropicApi', createdAt: '2026-06-15T10:00:00Z' },
];

beforeEach(() => {
  vi.clearAllMocks();
  listCredentials.mockResolvedValue(VIEWS);
  createCredential.mockResolvedValue({
    id: 'c3',
    name: 'New hook',
    type: 'slackWebhook',
    createdAt: '2026-06-16T11:00:00Z',
  });
  deleteCredential.mockResolvedValue(null);
  window.confirm = vi.fn(() => true);
});

describe('CredentialsPanel', () => {
  it('lists the owner credentials by name and type, never a secret value', async () => {
    render(<CredentialsPanel open onClose={() => {}} onChanged={() => {}} />);

    expect(await screen.findByText('Prod TG')).toBeTruthy();
    expect(screen.getByText('AI key')).toBeTruthy();
    // Type label is rendered; raw secret fields never are.
    expect(screen.getByText('Telegram Bot API')).toBeTruthy();
    expect(screen.queryByText(/botToken/)).toBeNull();
    expect(listCredentials).toHaveBeenCalled();
  });

  it('renders the picked type\'s secret fields as password inputs and POSTs them on create', async () => {
    render(<CredentialsPanel open onClose={() => {}} onChanged={() => {}} />);
    await screen.findByText('Prod TG');

    fireEvent.click(screen.getByText('New credential'));

    // Default type is telegramApi -> a single botToken field, masked.
    const tokenInput = screen.getByLabelText('Bot token');
    expect(tokenInput.type).toBe('password');

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'My bot' } });
    fireEvent.change(tokenInput, { target: { value: 'super-secret' } });
    fireEvent.click(screen.getByText('Create'));

    await waitFor(() =>
      expect(createCredential).toHaveBeenCalledWith({
        name: 'My bot',
        type: 'telegramApi',
        data: { botToken: 'super-secret' },
      }),
    );
  });

  it('switches secret fields when the type changes', async () => {
    render(<CredentialsPanel open onClose={() => {}} onChanged={() => {}} />);
    await screen.findByText('Prod TG');
    fireEvent.click(screen.getByText('New credential'));

    fireEvent.change(screen.getByLabelText('Type'), { target: { value: 'httpHeaderAuth' } });
    expect(screen.getByLabelText('Header name')).toBeTruthy();
    expect(screen.getByLabelText('Header value')).toBeTruthy();
    expect(screen.queryByLabelText('Bot token')).toBeNull();
  });

  it('deletes a credential', async () => {
    const onChanged = vi.fn();
    render(<CredentialsPanel open onClose={() => {}} onChanged={onChanged} />);
    await screen.findByText('Prod TG');

    const deleteButtons = screen.getAllByText('Delete');
    fireEvent.click(deleteButtons[0]);

    await waitFor(() => expect(deleteCredential).toHaveBeenCalledWith('c1'));
    await waitFor(() => expect(screen.queryByText('Prod TG')).toBeNull());
    expect(onChanged).toHaveBeenCalled();
  });
});
