-- ============================================================
-- Banka 2025 — Seed Data
-- ============================================================
-- Lozinke za testiranje:
--   Admin korisnici (users tabela):    Admin12345
--   Obicni klijenti (users tabela):    Klijent12345
--   Zaposleni (employees tabela):      Zaposleni12
-- ============================================================

-- Sacekaj da Hibernate kreira tabele (ddl-auto=update)
-- Ovaj fajl se izvrsava posle kreiranja baze

-- ============================================================
-- USERS (klijenti i admini koji se loguju kroz /auth/login)
-- ============================================================

INSERT INTO users (first_name, last_name, email, password, username, phone, address, active, role)
VALUES
  -- ADMIN korisnici
  ('Marko', 'Petrović', 'marko.petrovic@banka.rs',
   '$2b$10$2o//nneiTVurujS8ou5Snu3qNbF3Q20CbPnLc9ag2q0YIO1R3SyZG',
   'marko.petrovic', '+381 63 111 2233', 'Knez Mihailova 15, Beograd', 1, 'ADMIN'),

  ('Jelena', 'Đorđević', 'jelena.djordjevic@banka.rs',
   '$2b$10$2o//nneiTVurujS8ou5Snu3qNbF3Q20CbPnLc9ag2q0YIO1R3SyZG',
   'jelena.djordjevic', '+381 64 222 3344', 'Bulevar Oslobođenja 42, Novi Sad', 1, 'ADMIN'),

  -- Obicni klijenti
  ('Stefan', 'Jovanović', 'stefan.jovanovic@gmail.com',
   '$2b$10$FUjcSzK7CZKeX53YVU4JjeOIXLt5axbipO85OlQqw5Dopg47zfgRG',
   'stefan.jovanovic', '+381 65 333 4455', 'Cara Dušana 8, Niš', 1, 'CLIENT'),

  ('Milica', 'Nikolić', 'milica.nikolic@gmail.com',
   '$2b$10$FUjcSzK7CZKeX53YVU4JjeOIXLt5axbipO85OlQqw5Dopg47zfgRG',
   'milica.nikolic', '+381 66 444 5566', 'Vojvode Stepe 23, Beograd', 1, 'CLIENT'),

  ('Lazar', 'Ilić', 'lazar.ilic@yahoo.com',
   '$2b$10$FUjcSzK7CZKeX53YVU4JjeOIXLt5axbipO85OlQqw5Dopg47zfgRG',
   'lazar.ilic', '+381 60 555 6677', 'Bulevar Kralja Petra 71, Kragujevac', 1, 'CLIENT'),

  ('Ana', 'Stojanović', 'ana.stojanovic@hotmail.com',
   '$2b$10$FUjcSzK7CZKeX53YVU4JjeOIXLt5axbipO85OlQqw5Dopg47zfgRG',
   'ana.stojanovic', '+381 69 666 7788', 'Đorđa Stanojevića 12, Beograd', 1, 'CLIENT'),

  -- Neaktivan klijent (za testiranje)
  ('Nemanja', 'Savić', 'nemanja.savic@gmail.com',
   '$2b$10$FUjcSzK7CZKeX53YVU4JjeOIXLt5axbipO85OlQqw5Dopg47zfgRG',
   'nemanja.savic', '+381 62 777 8899', 'Terazije 5, Beograd', 0, 'CLIENT')
ON DUPLICATE KEY UPDATE email = email;


-- ============================================================
-- EMPLOYEES (zaposleni u banci)
-- ============================================================

INSERT INTO employees (first_name, last_name, date_of_birth, gender, email, phone, address, username, password, salt_password, position, department, active)
VALUES
  ('Nikola', 'Milenković', '1988-03-15', 'M', 'nikola.milenkovic@banka.rs',
   '+381 63 100 2000', 'Nemanjina 4, Beograd', 'nikola.milenkovic',
   '$2b$10$lqAByD7N8elcbkNzut14L.dsZTHrWGL5r3qrp9KvzPw58.AzE4eHG',
   'c2VlZF9zYWx0XzAwMDFfXw==',
   'Team Lead', 'IT', 1),

  ('Tamara', 'Pavlović', '1992-07-22', 'F', 'tamara.pavlovic@banka.rs',
   '+381 64 200 3000', 'Kneza Miloša 32, Beograd', 'tamara.pavlovic',
   '$2b$10$727ZuuF8vHqGZyMrUTagCOZzhnvcV6Egf9198l5wEzyo07quVYkwq',
   'c2VlZF9zYWx0XzAwMDJfXw==',
   'Software Developer', 'IT', 1),

  ('Đorđe', 'Janković', '1985-11-03', 'M', 'djordje.jankovic@banka.rs',
   '+381 65 300 4000', 'Bulevar Mihajla Pupina 10, Novi Sad', 'djordje.jankovic',
   '$2b$10$xV3rnn442L9OG/tW6cz1TeR.hHwCamR/bO9Am3PFcrkMqG9PMiiYe',
   'c2VlZF9zYWx0XzAwMDNfXw==',
   'HR Manager', 'HR', 1),

  ('Maja', 'Ristić', '1995-01-18', 'F', 'maja.ristic@banka.rs',
   '+381 66 400 5000', 'Trg Republike 3, Beograd', 'maja.ristic',
   '$2b$10$00rB0B.rYUHcyAJc61hh2e3TjxI7zpQ.BUIR2ZchOjyaVE0AXkQYG',
   'c2VlZF9zYWx0XzAwMDRfXw==',
   'Accountant', 'Finance', 1),

  ('Vuk', 'Obradović', '1990-09-07', 'M', 'vuk.obradovic@banka.rs',
   '+381 60 500 6000', 'Železnička 15, Niš', 'vuk.obradovic',
   '$2b$10$g8WmJQ5QRHkJy59X5wxYf.Cfn5K9904fSiLY5QHUvCKfgOBLsDlAS',
   'c2VlZF9zYWx0XzAwMDVfXw==',
   'Supervisor', 'Operations', 0)
ON DUPLICATE KEY UPDATE email = email;

-- ============================================================
-- CURRENCIES (valute koje banka podrzava)
-- ============================================================
INSERT INTO currencies (id, code, name, symbol, country, description, active) VALUES
(1, 'EUR', 'Euro', '€', 'European Union', 'Euro – official currency of the Eurozone', true),
(2, 'CHF', 'Swiss Franc', 'CHF', 'Switzerland', 'Swiss Franc – currency of Switzerland', true),
(3, 'USD', 'US Dollar', '$', 'United States', 'US Dollar – currency of the United States', true),
(4, 'GBP', 'British Pound', '£', 'United Kingdom', 'British Pound – currency of the UK', true),
(5, 'JPY', 'Japanese Yen', '¥', 'Japan', 'Japanese Yen – currency of Japan', true),
(6, 'CAD', 'Canadian Dollar', '$', 'Canada', 'Canadian Dollar – currency of Canada', true),
(7, 'AUD', 'Australian Dollar', '$', 'Australia', 'Australian Dollar – currency of Australia', true),
(8, 'RSD', 'Serbian Dinar', 'RSD', 'Serbia', 'Serbian Dinar – currency of Serbia', true);

-- ============================================================
-- EMPLOYEE PERMISSIONS
-- ============================================================

-- Nikola Milenković — Team Lead (sve permisije)
INSERT INTO employee_permissions (employee_id, permission)
SELECT e.id, p.permission
FROM employees e
CROSS JOIN (
  SELECT 'ADMIN' AS permission UNION ALL
  SELECT 'TRADE_STOCKS' UNION ALL
  SELECT 'VIEW_STOCKS' UNION ALL
  SELECT 'CREATE_CONTRACTS' UNION ALL
  SELECT 'CREATE_INSURANCE' UNION ALL
  SELECT 'SUPERVISOR' UNION ALL
  SELECT 'AGENT'
) p
WHERE e.email = 'nikola.milenkovic@banka.rs'
AND NOT EXISTS (
  SELECT 1 FROM employee_permissions ep WHERE ep.employee_id = e.id AND ep.permission = p.permission
);

-- Tamara Pavlović — Developer (stocks + contracts)
INSERT INTO employee_permissions (employee_id, permission)
SELECT e.id, p.permission
FROM employees e
CROSS JOIN (
  SELECT 'VIEW_STOCKS' AS permission UNION ALL
  SELECT 'TRADE_STOCKS' UNION ALL
  SELECT 'CREATE_CONTRACTS'
) p
WHERE e.email = 'tamara.pavlovic@banka.rs'
AND NOT EXISTS (
  SELECT 1 FROM employee_permissions ep WHERE ep.employee_id = e.id AND ep.permission = p.permission
);

-- Đorđe Janković — HR Manager (supervisor + agent)
INSERT INTO employee_permissions (employee_id, permission)
SELECT e.id, p.permission
FROM employees e
CROSS JOIN (
  SELECT 'SUPERVISOR' AS permission UNION ALL
  SELECT 'AGENT'
) p
WHERE e.email = 'djordje.jankovic@banka.rs'
AND NOT EXISTS (
  SELECT 1 FROM employee_permissions ep WHERE ep.employee_id = e.id AND ep.permission = p.permission
);

-- Maja Ristić — Accountant (insurance + contracts + view stocks)
INSERT INTO employee_permissions (employee_id, permission)
SELECT e.id, p.permission
FROM employees e
CROSS JOIN (
  SELECT 'CREATE_INSURANCE' AS permission UNION ALL
  SELECT 'CREATE_CONTRACTS' UNION ALL
  SELECT 'VIEW_STOCKS'
) p
WHERE e.email = 'maja.ristic@banka.rs'
AND NOT EXISTS (
  SELECT 1 FROM employee_permissions ep WHERE ep.employee_id = e.id AND ep.permission = p.permission
);

-- Vuk Obradović — Supervisor (neaktivan, ima supervisor + view stocks)
INSERT INTO employee_permissions (employee_id, permission)
SELECT e.id, p.permission
FROM employees e
CROSS JOIN (
  SELECT 'SUPERVISOR' AS permission UNION ALL
  SELECT 'VIEW_STOCKS'
) p
WHERE e.email = 'vuk.obradovic@banka.rs'
AND NOT EXISTS (
  SELECT 1 FROM employee_permissions ep WHERE ep.employee_id = e.id AND ep.permission = p.permission
);


-- ============================================================
-- CLIENTS (klijenti banke — vlasnici racuna)
-- ============================================================
-- Email adrese se MORAJU poklapati sa users tabelom (role='CLIENT')
-- jer AccountServiceImpl trazi klijenta po email-u iz JWT tokena.
-- Lozinke: Klijent12345

INSERT INTO clients (first_name, last_name, date_of_birth, gender, email, phone, address,
                     password, salt_password, active, created_at)
VALUES
    ('Stefan', 'Jovanović', '1995-04-12', 'M', 'stefan.jovanovic@gmail.com',
     '+381 65 333 4455', 'Cara Dušana 8, Niš',
     '$2b$10$FUjcSzK7CZKeX53YVU4JjeOIXLt5axbipO85OlQqw5Dopg47zfgRG',
     'c2VlZF9jbGllbnRfMDFf', 1, NOW()),

    ('Milica', 'Nikolić', '1993-08-25', 'F', 'milica.nikolic@gmail.com',
     '+381 66 444 5566', 'Vojvode Stepe 23, Beograd',
     '$2b$10$FUjcSzK7CZKeX53YVU4JjeOIXLt5axbipO85OlQqw5Dopg47zfgRG',
     'c2VlZF9jbGllbnRfMDJf', 1, NOW()),

    ('Lazar', 'Ilić', '1990-12-01', 'M', 'lazar.ilic@yahoo.com',
     '+381 60 555 6677', 'Bulevar Kralja Petra 71, Kragujevac',
     '$2b$10$FUjcSzK7CZKeX53YVU4JjeOIXLt5axbipO85OlQqw5Dopg47zfgRG',
     'c2VlZF9jbGllbnRfMDNf', 1, NOW()),

    ('Ana', 'Stojanović', '1997-06-15', 'F', 'ana.stojanovic@hotmail.com',
     '+381 69 666 7788', 'Đorđa Stanojevića 12, Beograd',
     '$2b$10$FUjcSzK7CZKeX53YVU4JjeOIXLt5axbipO85OlQqw5Dopg47zfgRG',
     'c2VlZF9jbGllbnRfMDRf', 1, NOW())
    ON DUPLICATE KEY UPDATE email = email;


-- ============================================================
-- COMPANIES (firme za poslovne racune)
-- ============================================================

INSERT INTO companies (id, name, registration_number, tax_number, activity_code, address,
                       majority_owner_id, active, created_at)
VALUES
    (1, 'TechStar DOO', '12345678', '123456789', '62.01',
     'Bulevar Mihajla Pupina 10, Novi Beograd',
     NULL, 1, NOW()),
    (2, 'Green Food AD', '87654321', '987654321', '10.10',
     'Industrijska zona bb, Subotica',
     NULL, 1, NOW())
    ON DUPLICATE KEY UPDATE name = name;


-- ============================================================
-- ACCOUNTS (racuni klijenata)
-- ============================================================
-- Enum vrednosti:
--   AccountType:    CHECKING, FOREIGN, BUSINESS, MARGIN
--   AccountSubtype: PERSONAL, SAVINGS, PENSION, YOUTH, SALARY, STANDARD
--   AccountStatus:  ACTIVE, INACTIVE
--
-- client_id:   1=Stefan, 2=Milica, 3=Lazar, 4=Ana
-- employee_id: 1=Nikola, 2=Tamara, 3=Djordje, 4=Maja
-- currency_id: 1=EUR, 2=CHF, 3=USD, 4=GBP, 5=JPY, 6=CAD, 7=AUD, 8=RSD

INSERT INTO accounts (account_number, account_type, account_subtype, currency_id,
                      client_id, company_id, employee_id,
                      balance, available_balance,
                      daily_limit, monthly_limit,
                      daily_spending, monthly_spending,
                      maintenance_fee, expiration_date, status, name, created_at)
VALUES
    -- ─── Stefan Jovanović (client_id=1) — 3 aktivna racuna ─────────────────
    -- GET /accounts/my → 3 racuna, sortirano po available_balance DESC:
    --   Stednja(520000) > Glavni(178000) > Euro(2350)

    -- Stefanov tekuci standardni (RSD)
    ('222000112345678911', 'CHECKING', 'STANDARD', 8, 1, NULL, 1,
     185000.0000, 178000.0000,
     250000.0000, 1000000.0000,
     7000.0000, 45000.0000,
     255.0000, '2030-01-01', 'ACTIVE', 'Glavni račun', NOW()),

    -- Stefanov stedni (RSD)
    ('222000112345678912', 'CHECKING', 'SAVINGS', 8, 1, NULL, 1,
     520000.0000, 520000.0000,
     100000.0000, 500000.0000,
     0.0000, 0.0000,
     150.0000, '2030-06-01', 'ACTIVE', 'Štednja', NOW()),

    -- Stefanov devizni (EUR)
    ('222000121345678921', 'FOREIGN', 'PERSONAL', 1, 1, NULL, 2,
     2500.0000, 2350.0000,
     5000.0000, 20000.0000,
     150.0000, 800.0000,
     0.0000, '2030-01-01', 'ACTIVE', 'Euro račun', NOW()),

    -- ─── Milica Nikolić (client_id=2) — 1 licni + 1 poslovni ──────────────
    -- Poslovni racun: account_type=BUSINESS, company_id=1 (TechStar DOO)

    -- Milicin tekuci (RSD)
    ('222000112345678913', 'CHECKING', 'STANDARD', 8, 2, NULL, 1,
     95000.0000, 92000.0000,
     250000.0000, 1000000.0000,
     3000.0000, 28000.0000,
     255.0000, '2031-03-15', 'ACTIVE', 'Lični račun', NOW()),

    -- Milicin poslovni (RSD) — vezan za TechStar DOO
    ('222000112345678914', 'BUSINESS', 'STANDARD', 8, 2, 1, 2,
     1250000.0000, 1230000.0000,
     1000000.0000, 5000000.0000,
     20000.0000, 350000.0000,
     500.0000, '2032-01-01', 'ACTIVE', 'TechStar poslovanje', NOW()),

    -- ─── Lazar Ilić (client_id=3) — 1 tekuci + 1 devizni USD ──────────────

    -- Lazarev tekuci (RSD)
    ('222000112345678915', 'CHECKING', 'STANDARD', 8, 3, NULL, 3,
     310000.0000, 305000.0000,
     250000.0000, 1000000.0000,
     5000.0000, 62000.0000,
     255.0000, '2030-09-01', 'ACTIVE', 'Tekući', NOW()),

    -- Lazarev devizni (USD)
    ('222000121345678922', 'FOREIGN', 'PERSONAL', 3, 3, NULL, 3,
     1800.0000, 1800.0000,
     3000.0000, 15000.0000,
     0.0000, 0.0000,
     0.0000, '2031-01-01', 'ACTIVE', 'Dollar savings', NOW()),

    -- ─── Ana Stojanović (client_id=4) — 1 aktivan + 1 neaktivan ───────────
    -- GET /accounts/my → vraca SAMO 1 (ACTIVE), neaktivan se filtrira

    -- Anin neaktivan racun
    ('222000112345678916', 'CHECKING', 'STANDARD', 8, 4, NULL, 4,
     50000.0000, 50000.0000,
     250000.0000, 1000000.0000,
     0.0000, 0.0000,
     255.0000, '2028-01-01', 'INACTIVE', 'Stari račun', NOW()),

    -- Anin aktivan racun
    ('222000112345678917', 'CHECKING', 'YOUTH', 8, 4, NULL, 4,
     72000.0000, 70500.0000,
     150000.0000, 600000.0000,
     1500.0000, 18000.0000,
     0.0000, '2031-06-01', 'ACTIVE', 'Račun za mlade', NOW())

    ON DUPLICATE KEY UPDATE account_number = account_number;
