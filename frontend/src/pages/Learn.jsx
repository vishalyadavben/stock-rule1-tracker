import React from 'react';

export default function Learn() {
  return (
    <div className="container">
      <h1>Rule #1 — Quick Reference</h1>

      <div className="card">
        <h3>The Four Ms</h3>
        <p>Buy a business only if you can say YES to all four:</p>
        <ol>
          <li><b>Meaning</b> — you understand it well enough to own the whole thing, and you'd
              be comfortable holding it as your family's main support for 100 years.</li>
          <li><b>Moat</b> — it can defend itself against competitors long-term, so its past
              performance is a reasonable guide to its future.</li>
          <li><b>Management</b> — the people running it act like long-term owners, not hired hands.</li>
          <li><b>Margin of Safety</b> — you know its intrinsic value (Sticker Price) and can buy
              it well below that.</li>
        </ol>
        <p><b>The 10-10 Rule:</b> don't own a business for ten minutes unless you're willing to own it for ten years.</p>
      </div>

      <div className="card">
        <h3>The Big Five numbers</h3>
        <p>All five should average <b>10%+ per year over the last 10 years</b>, and ideally not be slowing down:</p>
        <ul>
          <li><b>ROIC</b> (Return on Invested Capital) — the most important of the five. Look at the 10-yr, 5-yr, and most recent year.</li>
          <li><b>Sales growth</b></li>
          <li><b>EPS growth</b> (earnings per share)</li>
          <li><b>Equity growth</b> (book value per share) — the single best predictor of future EPS growth, more so than past EPS growth itself.</li>
          <li><b>Free Cash Flow growth</b></li>
        </ul>
        <p><b>Debt check:</b> long-term debt should be payable off within 3 years of free cash flow.</p>
      </div>

      <div className="card">
        <h3>Sticker Price — the 4-step method</h3>
        <ol>
          <li>Grow <b>current EPS</b> at your <b>estimated growth rate</b> for 10 years → future EPS.
              (Prioritize historical <b>equity</b> growth rate for this estimate, cross-checked against
              analyst estimates — use the more conservative of the two.)</li>
          <li>Multiply future EPS by an <b>estimated future PE</b> (default: 2× the growth rate,
              or the historical average PE if that's lower) → future price.</li>
          <li>Discount that future price back over 10 years at your <b>minimum acceptable rate of
              return</b> → Sticker Price (today's fair value).</li>
          <li><b>Margin of Safety price</b> = 50% of the Sticker Price. That's your target buy price.</li>
        </ol>
        <p style={{ color: '#94a3b8' }}>
          This exact calculator lives on every stock's page — use "Auto-fill from Big Five" to
          pre-populate steps 1–2 from your saved fundamentals, or type your own estimates.
        </p>
      </div>

      <div className="card">
        <h3>Why this matters (from the notes)</h3>
        <p>
          Buy businesses, not stocks. If you don't understand what you're buying, you can't know
          what it's worth — and if you can't know what it's worth, you can't know if you're
          getting a good price. The Four Ms are layers of protection: understanding, durability,
          trustworthy management, and a cheap enough price that being wrong doesn't cost you.
        </p>
      </div>
    </div>
  );
}
