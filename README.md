# Rule #1 Investing Tracker

A personal web app (for you + friends) to track investments, run the "Rule #1" Big Five
fundamentals, generate a Sticker Price, check off the qualitative Four Ms, and get a 1-10
business quality score — with full history preserved after you exit a position.

## Stack
- **Backend**: Java 17, Spring Boot 3, Spring Security (JWT), Spring Data JPA
- **Database**: MySQL 8
- **Frontend**: React (Vite), Recharts for graphs
- **Market data**: Alpha Vantage (swappable — see `StockDataService.java`)

Architecture is a **modular monolith**, not microservices — see "Why not microservices" below.

---

## 1. Prerequisites

- Java 17 (`java -version`)
- Maven 3.9+ (or use the included `mvnw` if you add one)
- Node.js 20+
- Docker + Docker Compose (easiest path) OR a local MySQL 8 install
- A free Alpha Vantage API key: https://www.alphavantage.co/support/#api-key
  (25 requests/day on the free tier — see blind spots below on why that matters)

---

## 2. Fastest path: run everything with Docker

```bash
cd rule1-tracker
export STOCK_API_KEY=your_alpha_vantage_key_here
docker compose up --build
```

This starts:
- MySQL on `localhost:3306` (schema auto-loaded from `database/schema.sql`)
- Backend on `localhost:8080`
- Frontend on `localhost:5173`

Open `http://localhost:5173`, register an account, and you're in.

---

## 3. Running it manually (no Docker) — useful while developing

**Database:**
```bash
mysql -u root -p < database/schema.sql
```

**Backend:**
```bash
cd backend
export DB_HOST=localhost DB_USER=root DB_PASSWORD=yourpassword
export JWT_SECRET=some-long-random-string
export STOCK_API_KEY=your_alpha_vantage_key
mvn spring-boot:run
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev
```

Visit `http://localhost:5173`.

---

## 4. Using it day to day

1. **Register / log in.**
2. **Dashboard → "Record a buy"**: enter a ticker, quantity, buy price. This auto-adds the
   stock to the master list and pulls its current price.
3. **Click into a stock** (`/stock/AAPL`):
   - **"Refresh from API"** pulls 10ish years of Sales/EPS/Equity/FCF/ROIC and charts them.
   - If a ticker's fundamentals aren't well covered by Alpha Vantage, use the
     `POST /api/stocks/{ticker}/big-five/manual` endpoint (or extend the UI — see blind
     spots) to type numbers in by hand, exactly as your notes intended ("both" manual and API).
   - **Sticker Price calculator**: enter current EPS, your estimated growth rate (the notes
     say: prioritize historical **equity** growth rate, cross-check against analyst estimates,
     use the more conservative number), future PE (defaults to 2x growth rate — call
     `/api/sticker-price/default-pe` to get the suggestion), and your minimum acceptable return.
   - **Four Ms checklist**: check off each item and jot free-text notes — this is exactly the
     checklist Phil Town describes (Meaning / Moat / Management / Margin of Safety / Debt),
     seeded into the DB from `schema.sql`, editable anytime.
   - **Overall score**: auto-computed 1-10 once you have Big Five history, checklist progress,
     and at least one Sticker Price calculation. Weighting: 6 pts for Big Five 10-yr pass/fail
     (ROIC, Sales, EPS, Equity, FCF), 3 pts for checklist completion, 1 bonus point if the
     current price is at/below your Margin-of-Safety price. Fully transparent — the breakdown
     JSON is shown on the page, not hidden math.
4. **Sell a position**: call `POST /api/investments/sell` with `{lotId, quantity, sellPrice,
   sellDate}` (wire this into a UI button — it's a 10-minute frontend addition, deliberately
   left as your first customization since everyone's sell-flow preferences differ).
5. **History page**: every exit (full or partial) is permanent — nothing is deleted when you
   fully exit a stock, by design (`investment_exits` table).

---

## 5. Deployment recommendation

Don't reach for Kubernetes or a full AWS microservices setup for this — it's you and a few
friends, not a SaaS company. Two solid options:

**Option A — simplest (recommended to start):**
- Backend: **Railway** or **Render** — both deploy a Spring Boot Docker image straight from
  GitHub in a few clicks, and both offer a managed MySQL add-on.
- Frontend: **Vercel** or **Netlify** — free tier, auto-deploys React on every push.
- Total cost: often $0–10/month for this scale.

**Option B — AWS, if you want it there specifically:**
- RDS for MySQL (db.t3.micro is plenty)
- Elastic Beanstalk or a small EC2 instance for the Spring Boot jar
- S3 + CloudFront for the React static build
- More setup, more moving parts, more monthly cost — only worth it if you already know AWS
  or want the experience.

Either way: **put the JWT secret and API keys in environment variables / a secrets manager,
never commit them**, and turn on HTTPS (Railway/Render/Vercel do this for you automatically).

---

## 6. Blind spots you didn't mention (worth deciding on before/while you build this out)

1. **API rate limits will bite you fast.** Alpha Vantage free tier = 25 requests/day total,
   shared across ALL your users' tickers. With a handful of friends each tracking 10 stocks,
   you'll exhaust it in minutes on a "refresh everything" click. Mitigations: cache
   aggressively (the schema already stores fetched data, don't refetch same-day), use a paid
   tier (~$50/mo unlocks much higher limits), or switch to Financial Modeling Prep, which has
   a more generous free fundamentals tier.
2. **"Real-time" price is not truly real-time on free APIs** — expect 15-min delays. Fine for
   Rule #1 investing (you're not day-trading), but set that expectation with your friends.
3. **Multi-currency isn't handled.** If anyone wants to track non-US stocks, you'll need FX
   conversion for portfolio totals — the schema has a `currency` field on `stocks` as a
   starting point, but no conversion logic yet.
4. **No corporate-action handling** (stock splits, dividends, spin-offs). A 2:1 split will
   silently wreck your buy-price/quantity math unless you manually adjust the lot. Worth a
   `corporate_actions` table down the line.
5. **Dividends aren't tracked as cash flow to you** — only price appreciation. If dividend
   income matters to you, add a `dividends_received` table tied to lots.
6. **Manual vs. API data conflicts.** Right now both a MANUAL and an API row can exist for the
   same stock+year (the `source` column keeps them separate) — you'll want a UI toggle to pick
   which one "wins" for calculations rather than always defaulting to API.
7. **No audit trail on checklist edits** — only the latest response is kept per item. If you
   want to see how your conviction on a stock changed over time, you'd need to version
   `checklist_responses` instead of upserting.
8. **Concurrent editing across friends isn't handled** — right now each user's data is fully
   isolated (correct for personal portfolios), but if you ever want a *shared* watchlist with
   group notes/scores, that's a different data model (shared ownership, not per-user rows).
9. **No password reset / email verification flow** — fine for a handful of trusted friends,
   not fine if this ever gets wider usage.
10. **No automated tests included in this scaffold.** Given this handles money-adjacent data,
    add unit tests for `CalculationService` (the CAGR/Sticker Price math) before trusting it —
    those are pure functions and cheap to test thoroughly.
11. **The 1-10 score is a simple transparent weighted formula, not "the" Rule #1 answer.**
    Phil Town's method is ultimately qualitative judgment (per the notes: "if you feel yourself
    making a big guess... this isn't a business to own"). Treat the score as a nudge/summary,
    not a verdict — the breakdown is shown precisely so you don't over-trust a single number.

---

## 7. Why a monolith, not microservices

You mentioned microservices "if needed." For this app's scale (you + friends), a single
Spring Boot service is:
- Easier to deploy (one Docker image, one place logs live)
- Easier to debug (no distributed tracing needed)
- Cheaper to host (one service instead of N)

The codebase is already split into clean packages (`controller`, `service`, `repository`,
`entity`) by domain (auth, stocks, investments, checklist, scoring) — if this ever grows into
something with real multi-team, multi-scale needs, splitting `StockDataService` and the
calculation engine into their own service is a clean, low-risk extraction because they don't
share database transactions with the investment/user data.
