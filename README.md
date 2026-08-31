# Sydney Daily Info Dashboard

A Spring Boot web app that shows a live dashboard for Cherrybrook / Sydney commuters:

| Section | What it shows |
|---|---|
| **Weather Forecast** | Current temperature, today & tomorrow for Cherrybrook and Sydney CBD — min/max temp, condition, rain % |
| **Cherrybrook Metro Car Park** | Live available vs total spaces, availability bar |
| **Sydney Metro Disruptions** | Active service alerts from the TfNSW GTFS-RT feed — each item shows its title and date, and expands to the full text on click |
| **Sydney Petrol Prices** | Live prices at Ampol Foodary Cherrybrook + average / min / max cents-per-litre across Sydney metro, plus an **E10 price-trend chart** of the last 5 recorded days |

The page auto-refreshes every 5 minutes. Each section shows a helpful message when API credentials are not yet configured.

When a PostgreSQL database is configured, the app also **records a daily snapshot** of the fuel prices (one row per day) so the E10 trend chart has history to draw. Without a database everything still works — persistence simply switches off.

---

## Tech Stack

- **Java 17 + Spring Boot 3.2** — web, Thymeleaf, cache (Caffeine)
- **Spring Data JPA + PostgreSQL** — optional daily price-history storage (auto-disabled when no database is configured)
- **Open-Meteo** — free weather API (no key required)
- **Transport for NSW Open Data API** — parking occupancy + GTFS-RT metro alerts
- **NSW FuelCheck API** — real-time petrol prices (no key required)
- **Bootstrap 5.3 + Bootstrap Icons** — responsive dashboard UI
- **Inline SVG** — dependency-free E10 price-trend chart (no chart library)

---

## API Keys You Need

### 1. Transport for NSW (TfNSW)

Used for **parking** and **metro disruptions**.

1. Register at <https://opendata.transport.nsw.gov.au>
2. Create an application and note your **API key**
3. Set environment variable: `TFNSW_API_KEY=<your key>`

The app calls `GET /v1/carpark` (all facilities) and automatically finds the entry named "Cherrybrook". No facility ID is needed by default.

If TfNSW ever adds multiple Cherrybrook entries or renames the facility, you can pin a specific numeric facility ID:
```
CARPARK_FACILITY_ID=<numeric id from TfNSW portal>
```

### 2. NSW FuelCheck

Used for **petrol prices** — **no API key or registration required**.

The app calls the NSW FuelCheck API (`api.onegov.nsw.gov.au/FuelCheckApp/v1/fuel/prices`) using only a `requesttimestamp` header. Prices are cached for 30 minutes.

---

## Price History (PostgreSQL) — optional

When a database is configured, the app stores a **daily snapshot** of the Sydney metro average prices and renders an **E10 price-trend chart** (last 5 recorded days) in the petrol section.

- **One row per day.** On each page load the app records a snapshot only if one doesn't already exist for the current Sydney calendar day (a unique `snapshot_date` constraint enforces this even under concurrent requests).
- **Schema is created automatically** on first start (`spring.jpa.hibernate.ddl-auto=update`):
  - `price_snapshot` — `id`, `snapshot_date` (unique), `recorded_at`, `region`
  - `price_snapshot_item` — `id`, `snapshot_id`, `fuel_type`, `fuel_label`, `average_price`, `min_price`, `max_price`, `station_count`
- **Fully optional.** If no database is configured, persistence auto-disables (JPA auto-configuration is skipped), the app still boots, and recording becomes a no-op. A database problem never breaks the page — recording failures are logged and swallowed.

### Configuring the database

Set a standard Spring datasource URL, **or** a single `DATABASE_URL` in the `postgresql://user:password@host:port/dbname` form (as provided by Railway/Heroku). A startup `EnvironmentPostProcessor` converts that URL into the JDBC datasource properties Spring needs, so on Railway you only wire up one variable:

```
DATABASE_URL = ${{Postgres.DATABASE_URL}}
```

(Replace `Postgres` with the name of your database service. Railway does not share variables between services automatically — this reference is the one required step.)

An example query once data has accumulated:

```sql
SELECT s.recorded_at, i.fuel_type, i.average_price
FROM price_snapshot s
JOIN price_snapshot_item i ON i.snapshot_id = s.id
ORDER BY s.snapshot_date DESC, i.fuel_type;
```

---

## Running Locally

```bash
# Minimum — weather and fuel prices work without any keys
./mvnw spring-boot:run

# With all features enabled
TFNSW_API_KEY=xxx ./mvnw spring-boot:run

# With daily price history (optional) — point at any PostgreSQL database
DATABASE_URL=postgresql://user:password@localhost:5432/dbname \
  TFNSW_API_KEY=xxx ./mvnw spring-boot:run
```

Visit <http://localhost:8080>.

---

## Docker

```bash
docker build -t sydney-info .
docker run -p 8080:8080 -e TFNSW_API_KEY=xxx sydney-info
```

The Dockerfile uses a **multi-stage build** (Maven build → JRE runtime) keeping the image small.

---

## Deploying to Render

A `render.yaml` is included for one-click deployment.

1. Push this repo to GitHub
2. In the [Render dashboard](https://render.com), click **New → Web Service** and connect your repo
3. Render will detect the `Dockerfile` automatically
4. Under **Environment**, add your secret key (marked `sync: false` so it is never stored in source control):
   - `TFNSW_API_KEY`
5. Deploy — Render injects `PORT` automatically; Spring Boot picks it up via `server.port=${PORT:8080}`

---

## Deploying to Fly.io

A `fly.toml` is included. The app is configured to deploy to the `syd` (Sydney) region.

### First-time setup

```bash
# Install flyctl if you haven't already
brew install flyctl        # macOS
# or: curl -L https://fly.io/install.sh | sh

# Authenticate
fly auth login

# Launch — Fly will detect fly.toml and the Dockerfile
fly launch --no-deploy

# Set your TfNSW API key as a secret
fly secrets set TFNSW_API_KEY=xxx

# Deploy
fly deploy
```

### Subsequent deploys

```bash
fly deploy
```

### Useful commands

```bash
fly logs          # tail live logs
fly status        # machine health
fly secrets set TFNSW_API_KEY=xxx   # update secret
```

---

## Deploying to Railway (with PostgreSQL)

1. Create a project on [Railway](https://railway.app) and deploy this repo (Railway auto-detects the `Dockerfile`).
2. In the same project, add a **PostgreSQL** database.
3. In your **app service → Variables**, add a reference to the database and (optionally) your TfNSW key:
   ```
   DATABASE_URL   = ${{Postgres.DATABASE_URL}}
   TFNSW_API_KEY  = <your key>
   ```
4. Deploy. On first boot the app connects, creates the `price_snapshot` / `price_snapshot_item` tables, and begins recording one price snapshot per day. Railway injects `PORT` automatically.

See [Price History (PostgreSQL)](#price-history-postgresql--optional) for schema and behaviour details.

---

## Caching

| Cache | TTL | Reason |
|---|---|---|
| Parking | 2 min | Occupancy changes frequently |
| Metro alerts | 5 min | Near-real-time service info |
| Weather | 30 min | Forecasts don't change minute-to-minute |
| Fuel prices | 30 min | Prices updated a few times per day |

---

## Environment Variables Reference

| Variable | Required | Default | Description |
|---|---|---|---|
| `PORT` | No | `8080` | HTTP port (auto-set by Render / Railway) |
| `TFNSW_API_KEY` | Yes* | — | TfNSW Open Data API key |
| `CARPARK_FACILITY_ID` | No | auto | TfNSW car park facility ID (leave blank to auto-discover Cherrybrook) |
| `DATABASE_URL` | No | — | PostgreSQL URL (`postgresql://…`, e.g. Railway) enabling daily price history. Standard `SPRING_DATASOURCE_*` variables also work. |

\* The app runs without `TFNSW_API_KEY` but will show a configuration prompt for parking and metro sections. Weather and fuel prices require no credentials. Without `DATABASE_URL`, price history and the E10 trend chart are simply not shown.

---

## Debug Endpoints

| Path | Purpose |
|---|---|
| `/debug/parking` | Raw TfNSW car park API response |
| `/debug/fuel` | First 200 stations from FuelCheck API (useful for verifying field names / coordinates) |

---

## How This App Was Built

This app was generated using [Claude Code](https://claude.ai/code) via the following prompts:

1. *"Create a Java Spring Boot app which can easily be deployed to Render with a Dockerfile. The app should show the current parking at Cherrybrook Metro car park. It should also show the weather forecast today and tomorrow for Cherrybrook and Sydney CBD. The forecast should include temperature range. Also show any disruptions to the Sydney Metro line. Finally it should show the current price of petrol in Sydney."*

2. *"Show current temperatures in the weather section. Move the weather section up to the top."*

3. *"Can you also display the petrol price at the Cherrybrook station."*

4. *"Only show me the price for Ampol Foodary Cherrybrook."*

5. *"Some items under disruptions don't show a date."* — parse the GTFS-RT `active_period` and show each alert's date range.

6. *"Integrate this project with a Railway PostgreSQL database. Every time the page is loaded, record the current date and prices into the database."*

7. *"Only insert a new entry into the database once a day."*

8. *"Display a graph in the petrol prices section of the price changes for E10 over the last 5 entries."*

9. *"In the Sydney Metro disruptions section only show the title and date of each disruption by default. Display all text when the section is clicked."*
