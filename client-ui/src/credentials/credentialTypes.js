// Single source of truth for credential type shapes, shared by the management panel (which renders
// secret fields as password inputs to CREATE a credential) and the node inspector (which maps a node
// type to the credential type(s) it accepts). Mirrors the client-api type contract; secret values
// are never read back, so these field lists only drive the create/edit forms.

// type -> { label, fields: [{ key, label, placeholder }] }
export const CREDENTIAL_TYPES = {
  telegramApi: {
    label: 'Telegram Bot API',
    fields: [{ key: 'botToken', label: 'Bot token', placeholder: '123456:ABC-DEF...' }],
  },
  anthropicApi: {
    label: 'Anthropic API',
    fields: [{ key: 'apiKey', label: 'API key', placeholder: 'sk-ant-...' }],
  },
  slackWebhook: {
    label: 'Slack Incoming Webhook',
    fields: [
      {
        key: 'webhookUrl',
        label: 'Webhook URL',
        placeholder: 'https://hooks.slack.com/services/T.../B.../xxxx',
      },
    ],
  },
  discordWebhook: {
    label: 'Discord Webhook',
    fields: [
      {
        key: 'webhookUrl',
        label: 'Webhook URL',
        placeholder: 'https://discord.com/api/webhooks/...',
      },
    ],
  },
  httpHeaderAuth: {
    label: 'HTTP Header Auth',
    fields: [
      { key: 'headerName', label: 'Header name', placeholder: 'X-Api-Key' },
      { key: 'headerValue', label: 'Header value', placeholder: 'secret-value' },
    ],
  },
  httpBearerAuth: {
    label: 'HTTP Bearer Auth',
    fields: [{ key: 'token', label: 'Token', placeholder: 'eyJhbGci...' }],
  },
};

export const CREDENTIAL_TYPE_KEYS = Object.keys(CREDENTIAL_TYPES);

// node type -> credential type(s) it accepts. The inspector renders a <select> of the owner's
// credentials filtered to these types, plus a "None (inline)" option.
export const NODE_CREDENTIAL_TYPES = {
  telegramSend: ['telegramApi'],
  anthropicMessage: ['anthropicApi'],
  slackSend: ['slackWebhook'],
  discordSend: ['discordWebhook'],
  httpRequest: ['httpHeaderAuth', 'httpBearerAuth'],
};

export const credentialTypeLabel = (type) => CREDENTIAL_TYPES[type]?.label || type;
