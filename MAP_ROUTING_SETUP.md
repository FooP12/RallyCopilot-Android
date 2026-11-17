# MapLibre + OSRM setup

The project uses:
- MapLibre Native Android SDK (`org.maplibre.gl:android-sdk:13.6.1`)
- CARTO Dark Matter style (`https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json`)
- OSRM public demo routing endpoint (`https://router.project-osrm.org/route/v1/driving/`)

No API key is needed for this prototype.

## How to test
1. Install the app on an Android phone with internet access.
2. Open the **Заезд** tab and grant location permission.
3. Wait for the GPS position to appear and the map to center on it.
4. Long-press a point on the map to set the destination.
5. The app requests a driving route and draws it in lime green. It displays distance/ETA and speaks the first maneuver.
6. Use **СБРОС** to clear the destination and route.

## Service limitations
The public OSRM demo is for reasonable non-commercial use, is best-effort, and has a published limit of no more than one request per second. It is not a production routing backend. For a reliable release, deploy OSRM yourself or use a routing service whose terms and quotas fit the app.

The public CARTO basemap endpoint is used for prototype testing. Check the provider's current terms before broad distribution; use a supported tile provider or host your own tiles for production.

## What's not implemented yet
- Search by address/place name (geocoding)
- Continuous route-progress tracking and distance-to-next-turn announcements
- Automatic rerouting
- Offline maps
- Rewards, shop, and leaderboard backend
