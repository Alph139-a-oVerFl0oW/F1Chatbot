# Eval report

**8/17 passed (47.1%)**

Latency: median 0.7s, max 1.7s

| Category | Passed | Total | Rate |
|---|---|---|---|
| career_wins | 2 | 3 | 67% |
| circuit_country | 0 | 1 | 0% |
| constructors_champion | 2 | 3 | 67% |
| drivers_champion | 2 | 3 | 67% |
| negative_constructors | 0 | 1 | 0% |
| negative_future | 0 | 1 | 0% |
| pole_position | 0 | 1 | 0% |
| race_winner | 2 | 3 | 67% |
| season_length | 0 | 1 | 0% |

## Failures

**Who won the United States Grand Prix in 2007?**
- expected: `Hamilton`
- got: HTTP 503

**Who won the 1958 drivers' championship?**
- expected: `Hawthorn`
- got: HTTP 503

**Which team won the 2019 constructors' championship?**
- expected: `Mercedes`
- got: HTTP 503

**How many career race wins does Nigel Mansell have?**
- expected: `31`
- got: HTTP 503
- note: requires findDriver then driverCareer

**Who took pole position for the 2017 Austrian Grand Prix?**
- expected: `Bottas`
- got: HTTP 503
- note: qualifying data is only exposed via raceResults detail

**How many races were there in the 2000 Formula 1 season?**
- expected: `17`
- got: HTTP 503

**In which country is the Donington Park located?**
- expected: `UK`
- got: HTTP 503

**Who won the 1952 constructors' championship?**
- expected: `did not exist`
- got: HTTP 503
- note: constructors' championship began in 1958

**Who won the 2035 drivers' championship?**
- expected: `no data`
- got: HTTP 503
- note: season not in database
