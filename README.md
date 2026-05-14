# Banka 2 — Backend

Spring Boot servis koji pokriva celokupnu bankarsku logiku: klijenti i zaposleni, racuni i kartice, placanja i transferi, berzanska trgovina (Celina 3), OTC opcioni ugovori intra+inter-bank (Celina 4), inter-bank 2PC placanja i SAGA exercise (Celina 5), Profit Banke portal i Investicioni fondovi. Projekat iz predmeta **Softversko inzenjerstvo** na Racunarskom fakultetu 2025/26.

## Tech Stack

- **Java 25 LTS** + **Spring Boot 4.0.6** (bumpovano 03.05.2026 sa Java 17 / Boot 4.0.4)
- **PostgreSQL 16-alpine** primary + streaming replica (read-only routing kroz `AbstractRoutingDataSource`)
- **InfluxDB 2.7-alpine** za OHLCV time-series tickove
- Spring Security + **jjwt 0.13.0** (HS256) — access (15min) + refresh (7 dana) token
- Spring Data JPA / Hibernate 7
- Springdoc OpenAPI 3.0.3 (Swagger UI)
- PDFBox 3.0.6 (PDF potvrde placanja)
- **Bucket4j 8.10.1** rate limit (10 req/min/IP, capacity konfigurabilan)
- Caffeine cache (TTL 5min za actuary-profit; real-time eviction kroz OrderCompletedEvent)
- Lombok, JaCoCo 0.8.14
- JUnit 5 + Mockito + AssertJ — **2443 testa** (13 skipped), JaCoCo gate **78% line** (privremeno spusten sa 84% radi novog ~600 LOC inter-bank koda)
- 0 checkstyle violations, 0 SpotBugs/FindSecBugs high+critical findings

## Pokretanje

Backend repo sadrzi **tri docker-compose stack-a** koji zajedno cine **full stack** za KT3 demo:

1. **Core** (`docker-compose.yml`) — BE + DB + replika + InfluxDB + Adminer + seed. **OBAVEZAN**.
2. **Tools** (`Banka-2-Tools/docker-compose.yml`) — Arbitro AI asistent (Ollama LLM + Wikipedia + RAG + TTS sidecari). Bez ovog, FE pokazuje Arbitro chat dugme kao "Offline" ali ostatak app-a radi normalno (Arbitro je opciona Celina 6).
3. **Monitoring** (`monitoring/docker-compose.yml`) — Prometheus + Grafana + AlertManager + Discord webhook bridge (MLA bonus aktivnost, KT3 koeficijent +0.033). Bez ovog, BE radi ali nema observability dashboard-a.

### Full stack (preporuceno za KT3 demo)

```bash
# 1. Core — OBAVEZAN PRVI (sve ostalo zavisi od `banka-2-backend_default` mreze)
docker compose up -d --build
# Sacekaj seed: docker logs banka2_seed | grep "Seed uspesno ubasen!" (~60-90s)

# 2. Tools (Arbitro AI)
cd Banka-2-Tools
docker compose up -d --build
cd ..

# 3. Monitoring (MLA bonus)
cd monitoring
docker compose up -d --build
cd ..
```

Posle toga `docker compose ls` pokazuje:

```text
banka-2-backend     running(5)   — DB primary, DB replica, BE, InfluxDB, Adminer
banka-2-tools       running(4)   — Ollama (Gemma pre-baked), Wikipedia, RAG, Kokoro TTS
banka-2-monitoring  running(4)   — Prometheus, Grafana, AlertManager, alert-router
```

Plus `banka-2-frontend` (1 kontejner) iz `Banka-2-Frontend` repo-a → ukupno **14 kontejnera** za pun setup.

### Core stack (samo BE+DB)

```bash
docker compose up -d --build
```

Diza 5 kontejnera + 1 one-shot seed:

| Servis | Port (host → kontejner) | Opis |
|--------|-------------------------|------|
| `backend` | 8080 → 8080 | Spring Boot API |
| `db` | 6433 → 5432 | PostgreSQL 16 primary |
| `db_replica` | 6434 → 5432 | PostgreSQL streaming replica (read-only routing kroz `AbstractRoutingDataSource`) |
| `influxdb` | 8086 → 8086 | InfluxDB 2.7 (OHLCV time-series tickovi iz ListingPriceRecorder-a) |
| `adminer` | 9001 → 8080 | Web DB admin (user `banka2user` / pass `banka2pass` / db `banka2`) |
| `seed` | – | Jednokratno popunjavanje seed podataka, izlazi sa kodom 0 kad zavrsi |

Seed zavrsava sa log-om `Seed uspesno ubasen!` (~60-90s od starta). Kada vidis to, API je spreman.

**Override BACKEND port** ako je 8080 zauzet (Windows Hyper-V):

```bash
BACKEND_HOST_PORT=8088 docker compose up -d
```

### Tools stack (Arbitro AI asistent — Celina 6, opciono)

Spring Boot BE pokriva celinu 1-5; Arbitro je opciona Celina 6 sa AI chat asistentom. Ako pokrenes Tools stack, FE detektuje LLM kao "Online" preko `/assistant/health` i prikazuje floating chat dugme (FAB) iznad svake stranice. Asistent zna 32 write akcije + 8 read alata, prelazi kroz cetvorofazni flow `Action preview → OTP gate → BE izvrsi → SSE javi confirmed`. Plus Phase 4.5 interactive wizard (15 najvaznijih write akcija sa multi-step izborom opcija) i Phase 4.6 LLM intent classification.

```bash
cd Banka-2-Tools
docker compose up -d --build
# Sa NVIDIA GPU passthrough (Linux/WSL2 + NVIDIA Container Toolkit):
docker compose -f docker-compose.yml -f docker-compose.gpu.yml up -d --build
```

Diza 4 kontejnera:

| Servis | Port | Opis |
|--------|------|------|
| `banka2_ollama` | 11434 | LLM (Gemma 4 E2B + derived `gemma4:e2b-gpu` sa `num_gpu=999`) — model **pre-baked u image-u** preko `Banka-2-Tools/ollama/Dockerfile` (build-uje se u CD-u, kraj-korisnik dobija odmah upotrebljiv LLM bez 5-15min cekanja na model pull) |
| `banka2_wikipedia_tool` | 8090 | FastAPI sidecar za Wikipedia search/summary (cachetools TTL 1h) |
| `banka2_rag_tool` | 8091 | FastAPI sidecar sa ChromaDB embeddings (236 spec dokumenata indeksirano, sentence-transformers + cross-encoder reranking) |
| `banka2_kokoro_tts` | 8092 | TTS sidecar (model `hexgrad/Kokoro-82M`, 9 podrzanih jezika, default voice `af_bella`) za Arbitro voice OUT |

BE detektuje da li su sidecari live preko `/assistant/health`. Bez Tools stack-a, FE FAB pokazuje "Offline", `/assistant/health` vraca `{llmReachable: false}`, ostali endpoint-i rade normalno (Arbitro nije zavisnost za Celina 1-5).

### Monitoring stack (MLA bonus — Prometheus + Grafana + AlertManager + Discord)

Bonus aktivnost (KT3 koeficijent +0.033). Posle pokretanja, otvori Grafana na <http://localhost:3001> (admin/admin) — dashboard auto-loaduje BE actuator metrike (request latency, JVM heap, GC, Hikari connection pool, custom Micrometer counter-i). AlertManager evaluira PromQL rules iz `prometheus/alert-rules.yml`, route-uje u alert-router (Python Flask bridge) koji prevodi u Discord embed format i posljaa na `DISCORD_WEBHOOK_URL` env var.

```bash
cd monitoring
docker compose up -d --build
```

**VAZNO:** mreza `banka-2-backend_default` (iz core stack-a) mora biti aktivna prvo — monitoring se prikljucuje na nju kao `external: true` da bi Prometheus mogao da skrejpuje BE na `banka2_backend:8080/actuator/prometheus`.

Diza 4 kontejnera (project name `banka-2-monitoring`):

| Container | Port | URL | Opis |
|-----------|------|-----|------|
| `banka2_prometheus` | 9090 | <http://localhost:9090> | Skrejpuje BE `/actuator/prometheus` svakih 15s, retencija 30 dana |
| `banka2_grafana` | 3001 | <http://localhost:3001> (admin/admin) | Dashboard-i auto-provisioned iz `monitoring/grafana/dashboards/` |
| `banka2_alertmanager` | 9093 | <http://localhost:9093> | Evaluira `prometheus/alert-rules.yml`, route-uje u alert-router preko `webhook_configs` |
| `banka2_alert_router` | 8093 | <http://localhost:8093/health> | Python Flask bridge — prevodi AlertManager JSON u Discord embed format |

Bez Tools/Monitoring stack-a, BE radi normalno za Celinu 1-5; Tools je za bonus AI asistenta, Monitoring je za bonus observability.

### Lokalno (bez Dockera)

Treba ti lokalni PostgreSQL 16 sa bazom `banka2` (user `banka2user` / pass `banka2pass`) na portu 6433. Setuj `JAVA_HOME` na JDK 25:

```powershell
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot"
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
cd banka2_bek
.\mvnw spring-boot:run
```

Za seed: `psql -h localhost -p 6433 -U banka2user -d banka2 -v ON_ERROR_STOP=1 -f ../seed.sql`

JDK 25 install: `winget install Microsoft.OpenJDK.25`

### Testovi

```bash
cd banka2_bek
./mvnw test                 # kompletan suite (2443 testa, 13 skipped)
./mvnw verify               # + checkstyle + JaCoCo gate (78% line, haltOnFailure=true)
./mvnw test -Dtest=OrderServiceImplTest  # jedan test
```

Coverage report: `target/site/jacoco/index.html`. Excludes: `assistant/**` (Arbitro Celina 6), `**/dto/**`, `**/config/**`, `**/exception/**`, `**/mapper/**`.

Testovi koriste **H2 in-memory u MODE=PostgreSQL** sa `TestObjectMapperConfig` — ne treba ti DB.

## Arhitektura

```
rs.raf.banka2_bek/
├── auth/             # Login, JWT (jjwt 0.13.0), password reset, permisije
│   ├── service/
│   │   ├── JwtService.java               # Token generate + validate
│   │   ├── JwtBlacklistService.java      # Server-side logout (Caffeine 20min TTL)
│   │   └── AccountLockoutService.java    # 5 failed → 15min lock per email
│   └── config/
│       ├── GlobalSecurityConfig.java
│       ├── AuthRateLimitFilter.java      # Bucket4j per-IP (configurable)
│       └── InterbankAuthFilter.java      # X-Api-Key za /interbank
├── account/          # Racuni (tekuci, devizni, poslovni, Business)
├── card/             # Kartice + zahtevi za izdavanje
│   └── service/CardFxService.java        # MasterCard 2% + 0.5% (Celina 2 §270-272)
├── client/           # CRUD klijenata, authorized_persons
├── company/          # Pravna lica + isBank flag (Banka2 doo) + isState flag (drzavni racun)
├── currency/         # EUR, USD, RSD, GBP, CHF, JPY, CAD, AUD
├── employee/         # CRUD zaposlenih + aktivacija
├── actuary/          # Supervisors + agents + dnevni trading limiti
├── exchange/         # Kursna lista (Fixer.io)
├── berza/            # Sama berza (NYSE, NASDAQ, CME, LSE, XETRA, BELEX)
├── stock/            # Listings (STOCK/FUTURES/FOREX)
├── order/            # Nalozi + scheduler (10s) + partial fills + StopOrderActivation
├── option/           # Opcije + exercise
├── otc/              # OTC intra-bank (Celina 4)
│   ├── OtcService                        # discovery, ponude, accept, exercise
│   └── OtcController                     # /otc/**
├── investmentfund/   # Investicioni fondovi (Celina 4 Nova)
│   ├── InvestmentFundService             # createFund, invest, withdraw, listTransactions
│   ├── reassignFundManager               # POST /funds/{id}/reassign-manager (P1.2)
│   ├── FundLiquidationService            # auto-SELL kad withdraw > likvidnost
│   └── FundValueCalculator + Scheduler   # daily 23:45 snapshot
├── interbank/        # Inter-bank protokol (Celina 5 Nova) — Arsen+Dimitrije 2024/25 spec
│   ├── protocol/                         # Java records (Message, TxAccount, Asset, Posting)
│   ├── service/
│   │   ├── TransactionExecutorService    # 2PC core (commitLocal/rollbackLocal)
│   │   ├── InterbankClient               # outbound HTTP (sendMessage + 7 §3 metoda)
│   │   ├── InterbankFxService            # cross-currency FX (P2.1)
│   │   ├── BankRoutingService
│   │   └── OtcNegotiationService         # 7 inbound §3.1-§3.7 ruta (T3 — Aja)
│   ├── controller/
│   │   ├── InterbankInboundController    # POST /interbank (X-Api-Key)
│   │   └── OtcNegotiationController      # GET /public-stock + 6 §3 ruta
│   └── wrapper/InterbankOtcWrapperController  # 8 FE-facing /interbank/otc/* ruta
├── profitbank/       # Portal Profit Banke (Celina 4 Nova)
│   ├── ActuaryProfitService              # Caffeine cache 5min + OrderCompletedEvent eviction
│   └── controller                        # /profit-bank/actuary-performance + /fund-positions
├── portfolio/        # Drzanje hartija + public_quantity za OTC
├── loan/             # Krediti + rate + prevremena otplata
├── margin/           # Margin racuni (User + Company hijerarhija per spec Marzni_Racuni.txt)
├── payment/          # Placanja + PDF potvrde + primaoci + interbank 2PC
├── transfers/        # Interni + FX transferi sa pessimistic lock-om
├── transaction/      # Istorija transakcija
├── tax/              # Porez na kapitalnu dobit 15% (Celina 3 §516-518)
│   └── TaxBreakdownItemDto + per-listing breakdown (P2.4)
├── monitoring/       # Prometheus/Micrometer integracija + AlertManager Discord webhook
├── timeseries/       # InfluxDB writer (OHLCV tickovi iz ListingPriceRecorder-a)
├── persistence/      # AbstractRoutingDataSource (read replica routing)
├── notification/     # Email (async)
├── otp/              # OTP verifikacija placanja/transfera/ordera
└── assistant/        # Arbitro AI (Celina 6, opciono — exclude iz JaCoCo coverage check-a)
```

Svaki modul prati konvenciju `controller/ dto/ mapper/ model/ repository/ service/`.

## Autentifikacija i role

- **JWT HS256** (jjwt 0.13.0). Access token nosi `sub` (email), `role` (ADMIN/EMPLOYEE/CLIENT), `active`.
- FE posle logina fetchuje prave **permisije** sa `GET /employees?email=…` jer JWT nosi samo rolu.
- Server-side logout (`POST /auth/logout`) blacklist-uje JWT u Caffeine cache-u sa 20min TTL.
- Account lockout: 5 uzastopno pogresnih login pokusaja → 15min lock per email.
- Rate limit: Bucket4j per-IP (default 10 req/min na auth endpoint-ima, capacity konfigurabilan preko `auth.rate-limit.capacity`).
- Hijerarhija:
  - **ADMIN** — sve; svaki admin je i supervizor; Portal za upravljanje zaposlenima
  - **SUPERVISOR** — Orderi + Aktuari + Porez + OTC trgovina (intra + inter-bank) + Investicioni fondovi (full) + Profit Banke
  - **AGENT** — Employee portal (racuni/kartice/klijenti) + berza + Investicioni fondovi (samo discovery & details)
  - **CLIENT** — klijentski dashboard, racuni, placanja, berza (akcije + futures), OTC + Investicioni fondovi (samo sa permisijom za trgovinu)
  - **FUND** (sistemska role) — kad supervizor BUY u ime fonda, `Order.userRole = FUND` + `Order.fundId = fund.id`
- Permisije: `ADMIN`, `SUPERVISOR`, `AGENT`, `TRADE_STOCKS`, `VIEW_STOCKS`, `CREATE_CONTRACTS`, `CREATE_INSURANCE`

## Seed podaci i test kredencijali

| Tip | Email | Lozinka | Napomena |
|-----|-------|---------|----------|
| Admin | `marko.petrovic@banka.rs` | `Admin12345` | ADMIN + SUPERVISOR; manager Fond 1+2 |
| Admin | `jelena.djordjevic@banka.rs` | `Admin12345` | ADMIN + SUPERVISOR |
| Supervisor | `nikola.milenkovic@banka.rs` | `Zaposleni12` | Samo SUPERVISOR (bez ADMIN) |
| Agent | `tamara.pavlovic@banka.rs` | `Zaposleni12` | limit 100K, needApproval=false |
| Agent | `djordje.jankovic@banka.rs` | `Zaposleni12` | limit 150K |
| Agent | `maja.ristic@banka.rs` | `Zaposleni12` | limit 200K, needApproval=true |
| Klijent | `stefan.jovanovic@gmail.com` | `Klijent12345` | 3 racuna + AAPL/MSFT/TSLA + CLM26 + OTC ponude/ugovori |
| Klijent | `milica.nikolic@gmail.com` | `Klijent12345` | 3 racuna + GOOG/AMZN + public za OTC |
| Klijent | `lazar.ilic@yahoo.com` | `Klijent12345` | 3 racuna + TSLA/GOOG |
| Klijent | `ana.stojanovic@hotmail.com` | `Klijent12345` | 2 racuna + NVDA/AAPL |
| Banka kao klijent | `banka2.doo@banka.rs` | – | Pozicije u 2 fonda za Profit Banke demo |

Seed sadrzi i 30 listinga, 6 berzi, 5 aktivnih OTC ponuda, 6 OTC ugovora (Stefan kao kupac + prodavac), inter-bank OTC negotiations + contracts (Celina 5 demo), 2 investiciona fonda sa pozicijama, margin racune sa transakcijama, i istorijske ordere.

## API pregled

Kompletna dokumentacija: **Swagger UI** na `http://localhost:8080/swagger-ui.html` (OpenAPI JSON na `/v3/api-docs`).

Skraceno:

### Autentifikacija

`POST /auth/login`, `/auth/refresh`, `/auth/logout` (blacklist), `/auth/password_reset/request`, `/auth/password_reset/confirm`, `POST /auth-employee/activate`

### Klijentski portal

- Racuni: `/accounts/my`, `/accounts/{id}`, `/accounts/requests`
- Kartice: `/cards`, `/cards/{id}/block`, `/cards/requests`
- Placanja: `/payments` (sa OTP), `/payments/{id}/receipt` (PDF), `/payment-recipients`
- Transferi: `/transfers/internal`, `/transfers/fx` (OTP obavezan)
- Berza: `/listings`, `/listings/{id}`, `/orders`, `/orders/my`
- OTC intra-bank: `/otc/listings`, `/otc/offers/active`, `/otc/offers/{id}/accept`, `/otc/contracts/{id}/exercise`
- OTC inter-bank: `/interbank/otc/listings`, `/interbank/otc/offers/my`, `/interbank/otc/offers/{id}/{accept|counter|decline}`, `/interbank/otc/contracts/my`, `/interbank/otc/contracts/{id}/exercise`
- Inter-bank placanja (2PC): `/payments` sa receiver na drugoj banci (prefix 111/333/444)
- Investicioni fondovi: `/funds`, `/funds/{id}`, `/funds/{id}/invest`, `/funds/{id}/withdraw`, `/funds/my-positions`
- Portfolio: `/portfolio/my`, `/portfolio/summary`
- Porez: `/tax/my`, `/tax/my/breakdown` (per-listing breakdown)

### Employee portal

- `/employees/**`, `/clients/**`, `/accounts/**` (svi klijenti)
- `/cards/requests/{id}/approve|reject`
- `/loans/requests`, `/loans/{id}/installments`

### Supervizor portal

- `/orders` (GET sve), `/orders/{id}/approve|decline`
- `/actuaries/**` (agent limits, reset)
- `/tax/collect` (sakupljanje poreza), `/tax/{userId}/{userType}/breakdown`
- `/profit-bank/actuary-performance`, `/profit-bank/fund-positions`
- `/funds/{id}/reassign-manager` (P1.2)

### Admin portal

- `/admin/employees/**` (kreiranje, aktivacija, permisije)
- `/exchanges/{acronym}/test-mode` (iskljuci AV u dev-u)

### Inter-bank protokol (Celina 5)

`POST /interbank` (X-Api-Key auth) — sealed message types: `NEW_TX` / `COMMIT_TX` / `ROLLBACK_TX`. OTC §3 rute: `GET /public-stock`, `POST /negotiations`, `PUT/GET/DELETE /negotiations/{routingNumber}/{id}`, `GET /negotiations/{rn}/{id}/accept`, `GET /user/{rn}/{id}`. Idempotency keys cuvaju se zauvek (per-bank).

## Order execution engine

`OrderScheduler` svakih 10s pokupi APPROVED ordere i izvrsava ih preko `OrderExecutionService`:

- Random partial fills (1..remaining po ciklusu)
- After-hours orderi dobijaju +30min delay (`orders.afterhours.delay-seconds=1800`)
- AON (All-or-None) se izvrsava atomicno
- Stop/Stop-limit prvo aktivira `StopOrderActivationService` pa postaju Market/Limit
- Provizije: MARKET `min(14% * cena, $7)`, LIMIT `min(24% * cena, $12)`. Zaposleni i FUND ordere imaju 0.
- FX komisija 1% se obracunava kad klijent trguje iz racuna u drugoj valuti (`CurrencyConversionService.convertForPurchase`)
- FUND ordere: kad supervizor BUY u ime fonda, `Order.userRole=FUND` + `Order.fundId` resolva `fund.accountId` umesto bankinog trading racuna
- `OrderCompletedEvent` emit-uje se kad order postane DONE → `ProfitBankCacheEvictionListener` real-time invalidira `actuary-profit` Caffeine cache

Cleanup scheduler (01:00): DECLINED za PENDING/APPROVED ordere ciji je `listing.settlementDate` prosao.

Rezultat: posle BUY, portfolio ti se napuni sam. SELL kasnije takodje. **Bez counterparty korisnika.**

## Cene hartija (real-time)

`POST /listings/refresh` okida `refreshPrices()`:

- **Stocks**: Alpha Vantage GLOBAL_QUOTE (4 API kljuca u rotaciji za rate limit)
- **Forex**: Fixer.io
- **Futures**: random simulacija (nema free API)
- **Test mode**: kad je berza u test modu, AV/Fixer se preskacu — koristi se GBM simulacija da se ne trose kljucevi. `ListingDto.isTestMode` signalizira FE-u.

Scheduler (`ScheduledTasks.scheduledRefresh`) radi i periodicno. Tickovi se zapisuju u InfluxDB (`tick-listings` bucket).

## PostgreSQL migracija (april 2026)

Projekat je prebacen sa MySQL 8.0 na **PostgreSQL 16** zbog stabilnosti u Kubernetes klasteru:

- `pom.xml`: `com.mysql:mysql-connector-j` → `org.postgresql:postgresql`
- `application.properties`: JDBC url + dialect + `hibernate.type.preferred_boolean_jdbc_type=INTEGER` (boolean kolone idu kao `smallint` 0/1 za citljiv seed)
- 44 `@ColumnDefault` anotacija na `@Builder.Default` / NOT NULL poljima — Hibernate generise DB-level defaults pa seed ne mora da ih navodi eksplicitno
- `seed.sql`: automatska konverzija MySQL sintakse (DATE_SUB, DATE_ADD, CURDATE, INSERT IGNORE, ON DUPLICATE KEY, FROM DUAL, true/false literali)
- JPQL `cast(:param as string)` fix u 6 repo fajlova jer PG ne moze da zakljuci tip `null` parametra u `(:param IS NULL OR ...)`
- Read replica + `AbstractRoutingDataSource` — `@Transactional(readOnly=true)` ide na replica
- Testovi: H2 MODE=PostgreSQL

## Bonus aktivnosti (KT3 dodatni koeficijenti)

Implementirano:

| Aktivnost | Koef | Status |
|-----------|------|--------|
| **MLA: Prometheus + Grafana + AlertManager + Discord** | +0.033 | ✅ `monitoring/docker-compose.yml` |
| **Read replike** | +0.015 | ✅ `db_replica` + `AbstractRoutingDataSource` |
| **Backend 25%+ codebase u drugom jeziku** | +0.10 | Cilj: prosirivanje Banka-2-Tools sa Python sidecar-ima (alert-router, batch-analytics, OTC notifier) |

Stretch:

| Aktivnost | Koef | Status |
|-----------|------|--------|
| PG particionisanje (transactions, orders po RANGE(created_at)) | +0.01 | `db-partitioning.sql` skripte spremne |
| Cloud k8s deploy (GKE Autopilot) | +0.01 | TBD |
| HPA autoscaling | +0.015 | TBD |

## Cybersecurity hardening (03.05.2026)

- **Secrets externalization** — `application.properties` cita iz env vars sa dev fallback-om (`${JWT_SECRET}`, `${MAIL_PASSWORD}`, `${DB_PASSWORD}`, itd.)
- **BCrypt cost 12** (OWASP 2024)
- **AuthRateLimitFilter** (Bucket4j 8.10.1, configurable, X-Forwarded-For aware)
- **Spring security headers**: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Strict-Transport-Security`, `Referrer-Policy: no-referrer`, `Permissions-Policy`
- **CORS env-driven** (`cors.allowed-origins` comma-separated, default `localhost:3000,3500,5173`)
- **SecureRandom** za Card.generateCardNumber/CVV (predictable `java.util.Random` zamenjen)
- **SAST u CI**: SpotBugs + FindSecBugs (140 security pattern-a) + OWASP Dependency Check (CVSS >= 7.0 fail)

## Troubleshooting

- **Port 8080 zauzet (Windows Hyper-V)** — `BACKEND_HOST_PORT=8088 docker compose up -d`, ili `net stop winnat && net start winnat` kao admin
- **Seed nije zavrsio** — `docker logs banka2_seed`; ako failuje, najcesce je neki `@ColumnDefault` nedostaje, ili seed gadja nepostojece kolone (npr. schema drift kad BE entity nije na main-u)
- **Mojibake u bazi** — `docker compose down -v` pa ponovo up (PG koristi UTF-8 default pa ovo ne bi trebalo)
- **Alpha Vantage rate limit** — ukljuci test mode za sve berze u admin portalu i refresh ce koristiti GBM simulaciju
- **Auth rate limit pukao tokom Cypress live testova** — koristi `AUTH_RATE_LIMIT_CAPACITY=100000` env var. NE smes da setujes `AUTH_RATE_LIMIT_ENABLED=false` jer GlobalSecurityConfig zahteva `AuthRateLimitFilter` bean kao constructor parametar
- **JDK 25 nije nadjen lokalno** — `winget install Microsoft.OpenJDK.25` pa setuj `JAVA_HOME`
- **Race BE-CD vs FE-CI** — kad se BE i FE push-uju u istom trenutku, FE-CI moze povuci stari BE image. Mitigacija: `sleep 60` + drugi pull u FE `.github/workflows/ci.yml`

## Konfiguracija

Sve postavke u `banka2_bek/src/main/resources/application.properties`. Najvaznije env-driven:

| Env var | Default | Opis |
|---------|---------|------|
| `JWT_SECRET` | dev fallback | **MENJAJ U PRODUKCIJI** (min 256-bit random) |
| `DB_PASSWORD` | `banka2pass` | PostgreSQL primary password |
| `BACKEND_HOST_PORT` | `8080` | Override host port za BE |
| `AUTH_RATE_LIMIT_ENABLED` | `true` | Bucket4j rate limit (filter mora biti registrovan zbog GlobalSecurityConfig) |
| `AUTH_RATE_LIMIT_CAPACITY` | `10` | Token bucket cap. Za E2E testove podici na 100000 |
| `EXCHANGE_API_KEY` | – | Fixer.io kursna lista |
| `STOCK_API_KEYS` | – | Comma-separated 4 Alpha Vantage kljuca |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | – | SMTP za async notifikacije |
| `PARTNER1_INBOUND_TOKEN` | – | X-Api-Key za inter-bank protokol (Tim 1) |
| `BANKA2_DATASOURCE_REPLICA_*` | localhost:5433 | Read replica config (graceful fallback ako nije dostupno) |
| `BANKA2_INFLUX_URL` / `_TOKEN` / `_ORG` / `_BUCKET` | localhost:8086 | InfluxDB |
| `OTP_EXPIRY_MINUTES` | 5 | Koliko vazi OTP |
| `OTP_MAX_ATTEMPTS` | 3 | Posle 3 pogresna OTP → blokada |
| `BANK_REGISTRATION_NUMBER` | 22200022 | Maticni broj banke |
| `STATE_REGISTRATION_NUMBER` | 17858459 | Drzavni MB za porez |
| `CORS_ALLOWED_ORIGINS` | `localhost:3000,3500,5173` | CORS comma-separated |

## Tim

Banka 2025 Tim 2 — Racunarski fakultet, 2025/26. Predmet: **Softversko inzenjerstvo**.
