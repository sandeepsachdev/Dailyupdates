# Sydney Daily Info Dashboard

A Spring Boot web app that shows a live dashboard for Cherrybrook / Sydney commuters:

| Section | What it shows |
|---|---|
| **Cherrybrook Metro Car Park** | Live available vs total spaces, availability bar |
| **Weather Forecast** | Today & tomorrow for Cherrybrook and Sydney CBD — temp range, condition, rain % |
| **Sydney Metro Disruptions** | Active service alerts from the TfNSW GTFS-RT feed |
| **Sydney Petrol Prices** | Average / min / max cents-per-litre for U91, E10, Diesel and premium fuels across Sydney |

The page auto-refreshes every 5 minutes. Each section shows a helpful message when API credentials are not yet configured.

---

## Tech Stack

- **Java 17 + Spring Boot 3.2** — web, Thymeleaf, cache (Caffeine)
- **Open-Meteo** — free weather API (no key required)
- **Transport for NSW Open Data API** — parking occupancy + GTFS-RT metro alerts
- **NSW FuelCheck API** — real-time petrol prices (OAuth2 client credentials)
- **Bootstrap 5.3 + Bootstrap Icons** — responsive dashboard UI

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

Used for **petrol prices**.

1. Register at <https://api.onegov.nsw.gov.au>
2. Create an application — you will receive a **Client ID**, **Client Secret** and **API Key**
3. Set these environment variables:
   ```
   FUELCHECK_CLIENT_ID=<client id>
   FUELCHECK_CLIENT_SECRET=<client secret>
   FUELCHECK_API_KEY=<api key>
   ```

The app uses the OAuth2 client-credentials flow to obtain a Bearer token automatically (cached for 50 minutes).

---

## Running Locally

```bash
# Minimum — weather works without any keys
./mvnw spring-boot:run

# With all features enabled
TFNSW_API_KEY=xxx \
FUELCHECK_CLIENT_ID=yyy \
FUELCHECK_CLIENT_SECRET=zzz \
FUELCHECK_API_KEY=www \
./mvnw spring-boot:run
```

Visit <http://localhost:8080>.

---

## Docker

```bash
docker build -t sydney-info .
docker run -p 8080:8080 \
  -e TFNSW_API_KEY=xxx \
  -e FUELCHECK_CLIENT_ID=yyy \
  -e FUELCHECK_CLIENT_SECRET=zzz \
  -e FUELCHECK_API_KEY=www \
  sydney-info
```

The Dockerfile uses a **multi-stage build** (Maven build → JRE runtime) keeping the image small.

---

## Deploying to Render

A `render.yaml` is included for one-click deployment.

1. Push this repo to GitHub
2. In the [Render dashboard](https://render.com), click **New → Web Service** and connect your repo
3. Render will detect the `Dockerfile` automatically
4. Under **Environment**, add your secret keys (the `render.yaml` marks them as `sync: false` so they are never stored in source control):
   - `TFNSW_API_KEY`
   - `FUELCHECK_CLIENT_ID`
   - `FUELCHECK_CLIENT_SECRET`
   - `FUELCHECK_API_KEY`
5. Deploy — Render injects `PORT` automatically; Spring Boot picks it up via `server.port=${PORT:8080}`

---

## Caching

| Cache | TTL | Reason |
|---|---|---|
| Parking | 2 min | Occupancy changes frequently |
| Metro alerts | 5 min | Near-real-time service info |
| Weather | 30 min | Forecasts don't change minute-to-minute |
| Fuel prices | 30 min | Prices updated a few times per day |
| FuelCheck OAuth token | 50 min | Token expiry is ~60 min |

---

## Environment Variables Reference

| Variable | Required | Default | Description |
|---|---|---|---|
| `PORT` | No | `8080` | HTTP port (auto-set by Render) |
| `TFNSW_API_KEY` | Yes* | — | TfNSW Open Data API key |
| `CARPARK_FACILITY_ID` | No | `MACs100034` | TfNSW car park facility ID |
| `FUELCHECK_CLIENT_ID` | Yes* | — | NSW FuelCheck OAuth2 client ID |
| `FUELCHECK_CLIENT_SECRET` | Yes* | — | NSW FuelCheck OAuth2 client secret |
| `FUELCHECK_API_KEY` | Yes* | — | NSW FuelCheck API key |

\* The app runs without these keys but will show a configuration prompt for the affected section.
