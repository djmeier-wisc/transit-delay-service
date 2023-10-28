# TRANSIT DELAY SERVICE

This service:

- Gathers RealTime data from Madison's Metro Transit
- Formats the data
- Writes it to DynamoDB
- Provides endpoints to retrieve recorded data in the following formats:
  - ChartJS (based on route and time period)
  - JSON (read data directly from DB)

### A note on time

- Data is gathered from Metro transit every 5 minutes, barring service availability and deployments
- Data is queried based on unix time in seconds.
- For Chart.JS data endpoints, the timezone returned is CST

### A note on the meaning of "Delay"

Delay is measured in seconds, and is the absolute value of the difference from schedule.

For example, if line "A" has 3 buses along the route:

- B1: 300 seconds late
- B2: 300 seconds early
- B3: On time

The average delay would be 200 seconds.

### Documentation

- Despite my best efforts, I cannot get Swagger working.
- All endpoints are accessible @ *api.my-precious-time.com*
  - This access is currently available without an API key, and without a rate limit. I *will* regret this decision.
  - Please, do not use this API in important systems. I am not cool enough to keep this running with zero downtime.
- Below are the configured endpoints to view the data, and the parameters supported.
  - GET /v1/
