import React from 'react';

// Branded left panel shared by Login and Register. Pure presentation.
export default function AuthAside() {
  return (
    <aside className="auth-aside">
      <div className="auth-aside__top">
        <span className="brand-mark">
          <span className="brand-mark__glyph">{'</>'}</span>
          <span className="brand-mark__name">
            Bot<b>Constructor</b>
          </span>
        </span>
      </div>

      <div className="auth-aside__bottom">
        <h2 className="auth-aside__headline">
          Design conversational bots, <em>visually</em>.
        </h2>
        <p className="auth-aside__sub">
          Draw question flows on a live canvas, collaborate in real time, and test your bot
          without leaving the editor.
        </p>
        <pre className="auth-aside__code">
{'bot '}<span className="s">"support"</span>{' {\n  '}<span className="k">on</span>{' "price" → '}<span className="s">"From $9/mo"</span>{'\n  '}<span className="k">else</span>{' → fallback\n}'}
        </pre>
      </div>
    </aside>
  );
}
