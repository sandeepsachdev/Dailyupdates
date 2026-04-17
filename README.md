# Sydney Daily Info Dashboard

A Spring Boot web app that shows a live dashboard for Cherrybrook / Sydney commuters:

| Section | What it shows |
|---|---|
| **Weather Forecast** | Current temperature, today & tomorrow for Cherrybrook and Sydney CBD — min/max temp, condition, rain % |
| **Cherrybrook Metro Car Park** | Live available vs total spaces, availability bar |
| **Sydney Metro Disruptions** | Active service alerts from the TfNSW GTFS-RT feed |
| **Sydney Petrol Prices** | Live prices at Ampol Foodary Cherrybrook + average / min / max cents-per-litre across Sydney metro |

The page auto-refreshes every 5 minutes. Each section shows a helpful message when API credentials are not yet configured.

---

## Tech Stack

- **Java 17 + Spring Boot 3.2** — web, Thymeleaf, cache (Caffeine)
- **Open-Meteo** — free weather API (no key required)
- **Transport for NSW Open Data API** — parking occupancy + GTFS-RT metro alerts
- **NSW FuelCheck API** — real-time petrol prices (no key required)
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

Used for **petrol prices** — **no API key or registration required**.

The app calls the NSW FuelCheck API (`api.onegov.nsw.gov.au/FuelCheckApp/v1/fuel/prices`) using only a `requesttimestamp` header. Prices are cached for 30 minutes.

---

## Running Locally

```bash
# Minimum — weather and fuel prices work without any keys
./mvnw spring-boot:run

# With all features enabled
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
| `PORT` | No | `8080` | HTTP port (auto-set by Render) |
| `TFNSW_API_KEY` | Yes* | — | TfNSW Open Data API key |
| `CARPARK_FACILITY_ID` | No | auto | TfNSW car park facility ID (leave blank to auto-discover Cherrybrook) |

\* The app runs without `TFNSW_API_KEY` but will show a configuration prompt for parking and metro sections. Weather and fuel prices require no credentials.

---

## Debug Endpoints

| Path | Purpose |
|---|---|
| `/debug/parking` | Raw TfNSW car park API response |
| `/debug/fuel` | First 200 stations from FuelCheck API (useful for verifying field names / coordinates) |
