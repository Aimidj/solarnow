# SolarNow — global solar forecast + embeddable widget

A responsive, **non-commercial proof of concept**, built as plain HTML/CSS/JavaScript with no build system. The forecast uses Open-Meteo's global hourly `global_tilted_irradiance` on a user-selected panel tilt / bearing, multiplied by installed kWp and a fixed `0.82` performance ratio. This is an approximate energy yield, not a bankable PV production forecast.

## Run locally

```bash
python3 -m http.server 8000
# open http://localhost:8000
```

For automated tests:

```bash
npm test
```

## Validation status

The azimuth conversion from compass bearings to Open-Meteo’s south-zero azimuth convention has been corrected and tested. Automated core tests pass, but end-to-end production API requests have not been verified yet. The project deploys through GitHub to Vercel. Approximate IP location is resolved using Vercel geolocation request headers at /api/location. A commercial API license and server-side API proxy are required before monetization.

## Core features

- Automatically detect approximate IP-based location when a visitor first arrives, with manual international city search, share-link override and optional browser GPS.
- 3-day forecast request; daily totals for today/tomorrow and hourly bar chart.
- Controls for capacity, tilt and facing; 18% system loss assumption.
- Shareable query parameters and a configurable `embed.html` iframe widget.
- Defensive error messages, attribution, privacy page, mobile-first responsive interface.

## Free widget example

```html
<iframe src="https://YOUR-HOST/embed.html?lat=55.6761&lon=12.5683&kwp=5&tilt=35&azimuth=180&label=Copenhagen" width="100%" height="305" title="Solar energy forecast" loading="lazy" style="border:0;border-radius:12px"></iframe>
```

## What this is NOT

- No logins, payment plans, CRM, analytics, tracking or installer leads.
- No street-address autocomplete: the free geocoding provider supports cities/postcodes, not verified rooftop addresses. Users may opt in to browser coordinates.
- No production-grade panel model: snow cover, obstructions, temperature derating, shading, clipping, cleaning and aging are not modeled separately.
- **Not licensed for commercialization.** Open-Meteo Free API is strictly non-commercial. The published prototype must not show advertisements, take payments or be used by an installer for promotional/commercial activity while relying on the free endpoints.

## Commercial deployment checklist

1. Obtain commercial Open-Meteo API subscription and use their `customer-api.open-meteo.com` via server-side proxy; never expose API key in JavaScript or iframe.
2. Also check commercial terms for the geocoding API, or switch to a commercial geocoding provider.
3. Add rate limits, caching, and a CDN proxy for every API call (initial target: same forecast/capacity/tilt/orientation cached for 30-60 minutes).
4. Verify panel estimates against test locations and recorded PV output with subject-matter experts.
5. Add factual terms/privacy policy, monitoring, local consent obligations, security headers, abuse prevention and accessibility audits.
6. Validate market with 10 installer interviews, 10 free widget installs and at least 3 paid pilot commitments before implementing paid plans.

## Architecture

`index.html` main user experience; `app.js` geocoding, forecast fetch, chart, share; `solar-core.js` pure energy calculations; `embed.html`+`embed.js` standalone embeddable widget; `styles.css`; `tests/core.test.js`.

### Attribution and calculation

Open-Meteo data CC BY 4.0; source: https://open-meteo.com/. Open-Meteo's hourly radiation is preceding-hour mean W/m², so a one-hour contribution is `min(kWp, kWp × G_tilted / 1000 × 0.82) kWh`. This assumes 1 h model timestep and static performance ratio. Irradiance forecast values are not rooftop sensor readings.

## GitHub → Vercel deployment

Create a separate **private** GitHub repository named `solarnow`, then upload the files in this directory to the root of its `main` branch (not the enclosing `solarnow-mvp/` directory). Do not include your `.env` files. Next import that repository into Vercel as **SolarNow**, selecting **Other** as the framework preset, with no build command and root directory `./`. Preview deployment should be treated as a **non-commercial test** only. Avoid any paid/advertising features or promotion of the public widget until a commercial data license and server-side API proxy are installed. Vercel deployment/traffic should also be reviewed for costs before enabling public distribution.