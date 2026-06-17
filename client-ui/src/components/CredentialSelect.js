import React from 'react';
import { NODE_CREDENTIAL_TYPES, credentialTypeLabel } from '../credentials/credentialTypes';

// Credential <select> for a connector / httpRequest inspector. Lists the owner's credentials whose
// type matches `nodeType` (per NODE_CREDENTIAL_TYPES), plus a "None (inline)" option. Selecting one
// sets data.credentialId via onChange(id); selecting "None" clears it (''). The caller hides the
// inline secret field(s) whenever a credentialId is set.
export default function CredentialSelect({ nodeType, credentials, value, onChange }) {
  const acceptedTypes = NODE_CREDENTIAL_TYPES[nodeType] || [];
  const options = (credentials || []).filter((c) => acceptedTypes.includes(c.type));

  // A bound credential that no longer exists (deleted, or another session) — keep the id selectable
  // so we don't silently drop the binding, but flag it.
  const bound = options.find((c) => c.id === value);
  const danglingId = value && !bound ? value : null;

  return (
    <div className="cred-select">
      <label className="field-label" htmlFor={`cred-sel-${nodeType}`}>
        Credential
      </label>
      <select
        id={`cred-sel-${nodeType}`}
        className="field-select"
        value={value || ''}
        onChange={(e) => onChange(e.target.value)}
      >
        <option value="">None (inline)</option>
        {options.map((c) => (
          <option key={c.id} value={c.id}>
            {c.name} · {credentialTypeLabel(c.type)}
          </option>
        ))}
        {danglingId && (
          <option value={danglingId}>(bound credential — unavailable)</option>
        )}
      </select>
      {bound && (
        <p className="cred-select__bound">
          Using <strong>{bound.name}</strong> — the inline secret below is hidden while this
          credential is bound.
        </p>
      )}
    </div>
  );
}
