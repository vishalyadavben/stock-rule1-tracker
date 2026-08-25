import React, { useEffect } from 'react';
import { useLocation } from 'react-router-dom';

export default function Learn() {
  const location = useLocation();

  useEffect(() => {
    if (location.hash) {
      const el = document.getElementById(location.hash.replace('#', ''));
      if (el) el.scrollIntoView({ behavior: 'smooth' });
    }
  }, [location]);

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

      <div className="card" id="big-five-detailed">
        <h3>The Big Five — explained simply</h3>
        <p style={{ color: '#94a3b8' }}>
          Imagine your kid opens a lemonade stand. That helps make all five of these easy to picture.
        </p>

        <h4>1. ROIC (Return on Invested Capital)</h4>
        <p>
          Your kid spends $200 on a table, lemons, sugar, and cups. That $200 is the "invested
          capital." After a week, they've made $300 total, and after paying for supplies, they
          have $100 of pure profit left. $100 profit ÷ $200 spent = 50% ROIC. That's how well the
          stand turned money into more money. A company with high ROIC is really good at doing
          this — every dollar it spends on itself comes back multiplied.
        </p>

        <h4>2. Sales growth</h4>
        <p>
          Sales is just: how much lemonade did they sell, in dollars? If they sold $300 worth
          this year and $1,000 worth next year, sales are growing fast — people want more
          lemonade. A real company's "sales" work the same way: total dollars customers paid it.
        </p>

        <h4>3. EPS growth (Earnings Per Share)</h4>
        <p>
          Say your kid's stand gets big enough that they sell little "pieces" of ownership to
          raise money — each piece is called a "share." If the stand makes $6,000 profit and
          there are 2,000 shares split between everyone who owns a piece, each share "earned"
          $3. That's EPS. If EPS climbs year after year, each tiny piece of the business is
          worth more.
        </p>

        <h4>4. Equity growth (book value)</h4>
        <p>
          Equity is what's left over if your kid sold every lemon, every table, and every cup,
          and paid off anything they owe. It's the stand's true leftover value. If equity keeps
          growing every year, it means the stand keeps building up more and more real value
          instead of spending every dollar it makes.
        </p>
        <p style={{ color: '#94a3b8' }}>
          This is the one Warren Buffett cares about most for predicting the future — a business
          that reliably grows its leftover value is one you can trust to keep doing that.
        </p>

        <h4>5. Free Cash Flow growth</h4>
        <p>
          This is the actual cash left in the cash box after paying for everything the stand
          needed — supplies, a new cooler, whatever. It's different from "profit on paper"
          because sometimes a business looks profitable but all its cash is stuck buying
          expensive equipment. Free cash flow tells you if real spendable money is piling up.
        </p>

        <h4>Why 10% for 10 years?</h4>
        <p>
          If all five of these have grown at least 10% every year, on average, for the last 10
          years — and don't seem to be slowing down — that's a strong sign the business is
          genuinely good at what it does, not just having one lucky year.
        </p>
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
