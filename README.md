# TRANSIT DELAY SERVICE

## Frontend
The frontend is available at https://my-precious-time.com/

This service:

- Gathers GTFS feeds from [the Mobility Database](https://mobilitydatabase.org/)
- Formats and stores the [GTFS schedule data](https://gtfs.org/documentation/schedule/reference/) in Postgres
  - For STOP_TIMES, [data is often omitted between time points](https://gtfs.org/documentation/schedule/reference/#stop_timestxt). I chose to linearly interpolate between these points when polling this data
- Gathers and stores the current [realtime data](https://www.google.com/search?q=gtfs+realtime+reference&ie=UTF-8) from agencies
  - This often involves polling static reference data, to compare expected/actual arrival times
  - Data is polled eagerly, with failures ignored until a weekly "syncup" between static and realtime data
- Provides endpoints to retrieve recorded data in the following formats:
  - [ChartJS](https://www.chartjs.org/docs/latest/charts/line.html) (based on route and time period)
  - JSON (read data directly from DB)
  - [GeoJSON](https://geojson.org/) (in the form of lines with delay between each stop)

### Tools Used

- Postgres to store GTFS static/realtime data
- Spring Webflux - this is used to handle both endpoints, and to avoid loading all of the GTFS schedule reference into memory
- ChartJS Line Charts
- GeoJSON
- AWS EC2 (to host the backend)
- AWS CodeDeploy (for automatic deployments on merging into main)
- Many other useful libraries! Check the POM.xml

### Documentation

- API documentation for endpoints is pending, although [Swagger UI is available](https://api.my-precious-time.com/webjars/swagger-ui/index.html). Please open an issue or reach out if you would like to use our APIs, or use any of our data.

### Running our service

- after setting up a .env file, you can run the project

```env
# .env file
# PostgreSQL Settings
POSTGRES_DB=
POSTGRES_USER=
POSTGRES_PASSWORD=

# Cloudflare Tunnel Settings
CLOUDFLARE_TOKEN=
```

```shell
docker compose up --build
```

### Usage

- Please reach out if you would like to reuse code, poll our data, or for commercial usage.
