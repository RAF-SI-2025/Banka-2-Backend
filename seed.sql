-- ============================================================
-- Banka 2025 — Seed Data
-- ============================================================
-- Lozinke za testiranje:
--   Admin korisnici (users tabela):    Admin12345
--   Obicni klijenti (users tabela):    Klijent12345
--   Zaposleni (employees tabela):      Zaposleni12
-- ============================================================

-- (MySQL sql_mode reset uklonjen — PostgreSQL)

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
   'nemanja.savic', '+381 62 777 8899', 'Terazije 5, Beograd', 0, 'CLIENT');


-- ============================================================
-- EMPLOYEES (zaposleni u banci)
-- ============================================================

INSERT INTO employees (first_name, last_name, date_of_birth, gender, email, phone, address, username, password, salt_password, position, department, active)
VALUES
  -- Admini (takodje zaposleni - "svaki admin je i supervizor")
  ('Marko', 'Petrović', '1985-05-20', 'M', 'marko.petrovic@banka.rs',
   '+381 63 111 2233', 'Knez Mihailova 15, Beograd', 'marko.petrovic',
   '$2b$10$YdFawauXoIBNqlhpBkFpKuDeMqGXlFR6g6T.sj3yXVFOdFDSmZzPG',
   'c2VlZF9zYWx0X2FkbTFfXw==',
   'Direktor', 'Uprava', 1),

  ('Jelena', 'Đorđević', '1987-11-12', 'F', 'jelena.djordjevic@banka.rs',
   '+381 63 222 3344', 'Bulevar Kralja Aleksandra 73, Beograd', 'jelena.djordjevic',
   '$2b$10$YdFawauXoIBNqlhpBkFpKuDeMqGXlFR6g6T.sj3yXVFOdFDSmZzPG',
   'c2VlZF9zYWx0X2FkbTJfXw==',
   'Zamenik direktora', 'Uprava', 1),

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
   'Supervisor', 'Operations', 0);

-- ============================================================
-- CURRENCIES (valute koje banka podrzava)
-- ============================================================
INSERT INTO currencies (id, code, name, symbol, country, description, active) VALUES
(1, 'EUR', 'Euro', '€', 'European Union', 'Euro – official currency of the Eurozone', 1),
(2, 'CHF', 'Swiss Franc', 'CHF', 'Switzerland', 'Swiss Franc – currency of Switzerland', 1),
(3, 'USD', 'US Dollar', '$', 'United States', 'US Dollar – currency of the United States', 1),
(4, 'GBP', 'British Pound', '£', 'United Kingdom', 'British Pound – currency of the UK', 1),
(5, 'JPY', 'Japanese Yen', '¥', 'Japan', 'Japanese Yen – currency of Japan', 1),
(6, 'CAD', 'Canadian Dollar', '$', 'Canada', 'Canadian Dollar – currency of Canada', 1),
(7, 'AUD', 'Australian Dollar', '$', 'Australia', 'Australian Dollar – currency of Australia', 1),
(8, 'RSD', 'Serbian Dinar', 'RSD', 'Serbia', 'Serbian Dinar – currency of Serbia', 1);

-- ============================================================
-- EMPLOYEE PERMISSIONS
-- ============================================================

-- Marko Petrović — Direktor / Admin (sve permisije)
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
WHERE e.email = 'marko.petrovic@banka.rs'
AND NOT EXISTS (
  SELECT 1 FROM employee_permissions ep WHERE ep.employee_id = e.id AND ep.permission = p.permission
);

-- Jelena Đorđević — Zamenik / Admin (sve permisije)
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
WHERE e.email = 'jelena.djordjevic@banka.rs'
AND NOT EXISTS (
  SELECT 1 FROM employee_permissions ep WHERE ep.employee_id = e.id AND ep.permission = p.permission
);

-- Nikola Milenković — Team Lead, Supervizor (bez ADMIN permisije)
INSERT INTO employee_permissions (employee_id, permission)
SELECT e.id, p.permission
FROM employees e
CROSS JOIN (
  SELECT 'TRADE_STOCKS' AS permission UNION ALL
  SELECT 'VIEW_STOCKS' UNION ALL
  SELECT 'CREATE_CONTRACTS' UNION ALL
  SELECT 'CREATE_INSURANCE' UNION ALL
  SELECT 'SUPERVISOR'
) p
WHERE e.email = 'nikola.milenkovic@banka.rs'
AND NOT EXISTS (
  SELECT 1 FROM employee_permissions ep WHERE ep.employee_id = e.id AND ep.permission = p.permission
);

-- Tamara Pavlović — Agent (stocks + contracts + agent)
INSERT INTO employee_permissions (employee_id, permission)
SELECT e.id, p.permission
FROM employees e
CROSS JOIN (
  SELECT 'VIEW_STOCKS' AS permission UNION ALL
  SELECT 'TRADE_STOCKS' UNION ALL
  SELECT 'CREATE_CONTRACTS' UNION ALL
  SELECT 'AGENT'
) p
WHERE e.email = 'tamara.pavlovic@banka.rs'
AND NOT EXISTS (
  SELECT 1 FROM employee_permissions ep WHERE ep.employee_id = e.id AND ep.permission = p.permission
);

-- Đorđe Janković — Agent (stocks + agent)
INSERT INTO employee_permissions (employee_id, permission)
SELECT e.id, p.permission
FROM employees e
CROSS JOIN (
  SELECT 'VIEW_STOCKS' AS permission UNION ALL
  SELECT 'TRADE_STOCKS' UNION ALL
  SELECT 'AGENT'
) p
WHERE e.email = 'djordje.jankovic@banka.rs'
AND NOT EXISTS (
  SELECT 1 FROM employee_permissions ep WHERE ep.employee_id = e.id AND ep.permission = p.permission
);

-- Maja Ristić — Agent (insurance + contracts + stocks + agent)
INSERT INTO employee_permissions (employee_id, permission)
SELECT e.id, p.permission
FROM employees e
CROSS JOIN (
  SELECT 'CREATE_INSURANCE' AS permission UNION ALL
  SELECT 'CREATE_CONTRACTS' UNION ALL
  SELECT 'VIEW_STOCKS' UNION ALL
  SELECT 'TRADE_STOCKS' UNION ALL
  SELECT 'AGENT'
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
     'c2VlZF9jbGllbnRfMDRf', 1, NOW());


-- ============================================================
-- COMPANIES (firme za poslovne racune)
-- ============================================================

INSERT INTO companies (id, name, registration_number, tax_number, activity_code, address,
                       majority_owner_id, active, is_state, is_bank, created_at)
VALUES
    (1, 'TechStar DOO', '12345678', '123456789', '62.01',
     'Bulevar Mihajla Pupina 10, Novi Beograd',
     NULL, 1, 0, 0, NOW()),
    (2, 'Green Food AD', '87654321', '987654321', '10.10',
     'Industrijska zona bb, Subotica',
     NULL, 1, 0, 0, NOW());

-- ============================================================
-- AUTHORIZED PERSONS (ovlascena lica za firme)
-- ============================================================
-- Milica (client_id=2) je ovlasceno lice za TechStar DOO (company_id=1)
INSERT INTO authorized_persons (client_id, company_id, created_at)
SELECT c.id, 1, NOW()
FROM clients c WHERE c.email = 'milica.nikolic@gmail.com'
AND NOT EXISTS (
    SELECT 1 FROM authorized_persons ap WHERE ap.client_id = c.id AND ap.company_id = 1
);


-- ============================================================
-- ACCOUNTS (racuni klijenata)
-- ============================================================
-- Enum vrednosti:
--   AccountType:    CHECKING, FOREIGN, BUSINESS, MARGIN
--   AccountSubtype: PERSONAL, SAVINGS, PENSION, YOUTH, STUDENT, UNEMPLOYED, SALARY, STANDARD
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
    ('222000112345678911', 'CHECKING', 'STANDARD', 8, 1, NULL, 1,
     185000.0000, 178000.0000,
     250000.0000, 1000000.0000,
     7000.0000, 45000.0000,
     255.0000, '2030-01-01', 'ACTIVE', 'Glavni račun', NOW()),

    ('222000112345678912', 'CHECKING', 'SAVINGS', 8, 1, NULL, 1,
     520000.0000, 520000.0000,
     100000.0000, 500000.0000,
     0.0000, 0.0000,
     150.0000, '2030-06-01', 'ACTIVE', 'Štednja', NOW()),

    ('222000121345678921', 'FOREIGN', 'PERSONAL', 1, 1, NULL, 2,
     2500.0000, 2350.0000,
     5000.0000, 20000.0000,
     150.0000, 800.0000,
     0.0000, '2030-01-01', 'ACTIVE', 'Euro račun', NOW()),

    -- ─── Milica Nikolić (client_id=2) — 1 licni + 1 poslovni ──────────────
    ('222000112345678913', 'CHECKING', 'STANDARD', 8, 2, NULL, 1,
     95000.0000, 92000.0000,
     250000.0000, 1000000.0000,
     3000.0000, 28000.0000,
     255.0000, '2031-03-15', 'ACTIVE', 'Lični račun', NOW()),

    -- Poslovni racun MORA imati samo company_id, ne client_id (Account
    -- ima @AssertTrue isOwnerValid() XOR validaciju). Milica je
    -- AuthorizedPerson preko `authorized_persons` tabele, ne direktan
    -- vlasnik. Bag prijavljen 10.05.2026 vece-3 — pre fix-a, svaki update
    -- ovog racuna je pucao sa "Racun mora imati vlasnika: ili klijenta
    -- ili kompaniju, ali ne oba".
    ('222000112345678914', 'BUSINESS', 'STANDARD', 8, NULL, 1, 2,
     1250000.0000, 1230000.0000,
     1000000.0000, 5000000.0000,
     20000.0000, 350000.0000,
     500.0000, '2032-01-01', 'ACTIVE', 'TechStar poslovanje', NOW()),

    -- Milicin devizni EUR
    ('222000121345678923', 'FOREIGN', 'PERSONAL', 1, 2, NULL, 1,
     3200.0000, 3200.0000,
     10000.0000, 50000.0000,
     0.0000, 0.0000,
     0.0000, '2031-06-01', 'ACTIVE', 'Euro devizni', NOW()),

    -- ─── Lazar Ilić (client_id=3) — 1 tekuci + 1 devizni USD + 1 devizni EUR
    ('222000112345678915', 'CHECKING', 'STANDARD', 8, 3, NULL, 3,
     310000.0000, 305000.0000,
     250000.0000, 1000000.0000,
     5000.0000, 62000.0000,
     255.0000, '2030-09-01', 'ACTIVE', 'Tekući', NOW()),

    ('222000121345678922', 'FOREIGN', 'PERSONAL', 3, 3, NULL, 3,
     1800.0000, 1800.0000,
     3000.0000, 15000.0000,
     0.0000, 0.0000,
     0.0000, '2031-01-01', 'ACTIVE', 'Dollar savings', NOW()),

    ('222000121345678924', 'FOREIGN', 'PERSONAL', 1, 3, NULL, 3,
     1500.0000, 1500.0000,
     5000.0000, 20000.0000,
     0.0000, 0.0000,
     0.0000, '2031-03-01', 'ACTIVE', 'Euro savings', NOW()),

    -- ─── Ana Stojanović (client_id=4) — 1 aktivan + 1 neaktivan + 1 devizni
    ('222000112345678916', 'CHECKING', 'STANDARD', 8, 4, NULL, 4,
     50000.0000, 50000.0000,
     250000.0000, 1000000.0000,
     0.0000, 0.0000,
     255.0000, '2028-01-01', 'INACTIVE', 'Stari račun', NOW()),

    ('222000112345678917', 'CHECKING', 'YOUTH', 8, 4, NULL, 4,
     72000.0000, 70500.0000,
     150000.0000, 600000.0000,
     1500.0000, 18000.0000,
     0.0000, '2031-06-01', 'ACTIVE', 'Račun za mlade', NOW()),

    ('222000121345678925', 'FOREIGN', 'PERSONAL', 1, 4, NULL, 4,
     800.0000, 800.0000,
     3000.0000, 15000.0000,
     0.0000, 0.0000,
     0.0000, '2031-09-01', 'ACTIVE', 'Euro račun', NOW());


-- ============================================================
-- BANK ACCOUNTS (Banka kao entitet — racuni u svim valutama)
-- ============================================================
-- Banka ima racune u svim 8 valuta za primanje provizija i isplatu kredita.
-- employee_id=1 (Nikola) kreirao, nema client_id ni company_id (bank internal).
-- NAPOMENA: Validacija zahteva client XOR company, ali bankini racuni
-- koriste company_id=NULL i client_id=NULL. Moramo privremeno koristiti
-- company za ovo ili napraviti izuzetak. Koristimo company_id=2 (Green Food)
-- kao placeholder jer je to banka u vlasnistvu... Alternativa: kreiramo posebnu
-- firmu "Banka 2025" kao company.

-- Prvo kreiramo firmu za banku (is_bank=1, is_state=0 — Celina 2 §73-78
-- "Nasa Banka = Firma" ima poseban status u sistemu)
INSERT INTO companies (id, name, registration_number, tax_number, activity_code, address,
                       majority_owner_id, active, is_state, is_bank, created_at)
VALUES
    (3, 'Banka 2025 Tim 2', '22200022', '222000222', '64.19',
     'Bulevar Kralja Aleksandra 73, Beograd',
     NULL, 1, 0, 1, NOW());

-- Drzava (Republika Srbija, is_state=1) — poseban entitet za uplatu poreza,
-- Celina 3 §47 "naša država = Firma" sa RSD racunom za porez na dobit.
INSERT INTO companies (id, name, registration_number, tax_number, activity_code, address,
                       majority_owner_id, active, is_state, is_bank, created_at)
VALUES
    (4, 'Republika Srbija', '17858459', '100002288', '84.11',
     'Nemanjina 11, Beograd',
     NULL, 1, 1, 0, NOW());

-- Bankini racuni u svim valutama
INSERT INTO accounts (account_number, account_type, account_subtype, currency_id,
                      client_id, company_id, employee_id,
                      balance, available_balance,
                      daily_limit, monthly_limit,
                      daily_spending, monthly_spending,
                      maintenance_fee, expiration_date, status, name, created_at)
VALUES
    ('222000100000000110', 'BUSINESS', 'STANDARD', 8, NULL, 3, 1,
     50000000.0000, 50000000.0000, 999999999.0000, 999999999.0000,
     0.0000, 0.0000, 0.0000, '2050-01-01', 'ACTIVE', 'Banka RSD', NOW()),

    ('222000100000000120', 'BUSINESS', 'STANDARD', 1, NULL, 3, 1,
     5000000.0000, 5000000.0000, 999999999.0000, 999999999.0000,
     0.0000, 0.0000, 0.0000, '2050-01-01', 'ACTIVE', 'Banka EUR', NOW()),

    ('222000100000000130', 'BUSINESS', 'STANDARD', 2, NULL, 3, 1,
     5000000.0000, 5000000.0000, 999999999.0000, 999999999.0000,
     0.0000, 0.0000, 0.0000, '2050-01-01', 'ACTIVE', 'Banka CHF', NOW()),

    ('222000100000000140', 'BUSINESS', 'STANDARD', 3, NULL, 3, 1,
     5000000.0000, 5000000.0000, 999999999.0000, 999999999.0000,
     0.0000, 0.0000, 0.0000, '2050-01-01', 'ACTIVE', 'Banka USD', NOW()),

    ('222000100000000150', 'BUSINESS', 'STANDARD', 4, NULL, 3, 1,
     5000000.0000, 5000000.0000, 999999999.0000, 999999999.0000,
     0.0000, 0.0000, 0.0000, '2050-01-01', 'ACTIVE', 'Banka GBP', NOW()),

    ('222000100000000160', 'BUSINESS', 'STANDARD', 5, NULL, 3, 1,
     500000000.0000, 500000000.0000, 999999999.0000, 999999999.0000,
     0.0000, 0.0000, 0.0000, '2050-01-01', 'ACTIVE', 'Banka JPY', NOW()),

    ('222000100000000170', 'BUSINESS', 'STANDARD', 6, NULL, 3, 1,
     5000000.0000, 5000000.0000, 999999999.0000, 999999999.0000,
     0.0000, 0.0000, 0.0000, '2050-01-01', 'ACTIVE', 'Banka CAD', NOW()),

    ('222000100000000180', 'BUSINESS', 'STANDARD', 7, NULL, 3, 1,
     5000000.0000, 5000000.0000, 999999999.0000, 999999999.0000,
     0.0000, 0.0000, 0.0000, '2050-01-01', 'ACTIVE', 'Banka AUD', NOW());

-- Tekuci dinarski racun drzave (za uplatu poreza)
INSERT INTO accounts (account_number, account_type, account_subtype, currency_id,
                      client_id, company_id, employee_id,
                      balance, available_balance,
                      daily_limit, monthly_limit,
                      daily_spending, monthly_spending,
                      maintenance_fee, expiration_date, status, name, created_at)
VALUES
    ('222000100000000200', 'CHECKING', 'STANDARD', 8, NULL, 4, 1,
     0.0000, 0.0000, 999999999.0000, 999999999.0000,
     0.0000, 0.0000, 0.0000, '2050-01-01', 'ACTIVE', 'Republika Srbija - poreski racun', NOW());


-- ============================================================
-- CARDS (kartice klijenata)
-- ============================================================
-- card_status: ACTIVE, BLOCKED, DEACTIVATED
-- account_id: koristimo racune iz gornjeg inserta
-- client_id:  1=Stefan, 2=Milica, 3=Lazar, 4=Ana

INSERT INTO cards (card_number, card_name, cvv, account_id, client_id,
                   card_limit, status, created_at, expiration_date)
VALUES
    -- Stefan: 1 kartica za tekuci RSD (account_id=1)
    ('4222001234567890', 'Visa Debit', '123', 1, 1,
     250000.0000, 'ACTIVE', '2026-01-15', '2030-01-15'),

    -- Stefan: 1 kartica za Euro racun (account_id=3)
    ('4222009876543210', 'Visa Debit', '456', 3, 1,
     5000.0000, 'ACTIVE', '2026-02-01', '2030-02-01'),

    -- Milica: 1 kartica za tekuci (account_id=4)
    ('4222005555666677', 'Visa Debit', '789', 4, 2,
     200000.0000, 'ACTIVE', '2026-01-20', '2030-01-20'),

    -- Milica: 1 kartica za poslovni (account_id=5) — max 1 za business
    ('4222003333444455', 'Visa Business', '321', 5, 2,
     1000000.0000, 'ACTIVE', '2026-02-10', '2030-02-10'),

    -- Lazar: 1 kartica za tekuci (account_id=7)
    ('4222007777888899', 'Visa Debit', '654', 7, 3,
     250000.0000, 'ACTIVE', '2026-03-01', '2030-03-01'),

    -- Ana: 1 kartica za youth racun (account_id=11), blokirana za testiranje
    ('4222001111222233', 'Visa Debit', '987', 11, 4,
     150000.0000, 'BLOCKED', '2026-01-01', '2030-01-01');


-- ============================================================
-- PAYMENT RECIPIENTS (sacuvani primaoci placanja)
-- ============================================================

INSERT INTO payment_recipients (client_id, name, account_number, created_at)
SELECT c.id, 'Milica Nikolić', '222000112345678913', NOW()
FROM clients c WHERE c.email = 'stefan.jovanovic@gmail.com'
AND NOT EXISTS (
    SELECT 1 FROM payment_recipients pr WHERE pr.client_id = c.id AND pr.account_number = '222000112345678913'
);

INSERT INTO payment_recipients (client_id, name, account_number, created_at)
SELECT c.id, 'Lazar Ilić', '222000112345678915', NOW()
FROM clients c WHERE c.email = 'stefan.jovanovic@gmail.com'
AND NOT EXISTS (
    SELECT 1 FROM payment_recipients pr WHERE pr.client_id = c.id AND pr.account_number = '222000112345678915'
);

INSERT INTO payment_recipients (client_id, name, account_number, created_at)
SELECT c.id, 'Stefan Jovanović', '222000112345678911', NOW()
FROM clients c WHERE c.email = 'milica.nikolic@gmail.com'
AND NOT EXISTS (
    SELECT 1 FROM payment_recipients pr WHERE pr.client_id = c.id AND pr.account_number = '222000112345678911'
);

INSERT INTO payment_recipients (client_id, name, account_number, created_at)
SELECT c.id, 'Ana Stojanović', '222000112345678917', NOW()
FROM clients c WHERE c.email = 'lazar.ilic@yahoo.com'
AND NOT EXISTS (
    SELECT 1 FROM payment_recipients pr WHERE pr.client_id = c.id AND pr.account_number = '222000112345678917'
);

-- ============================================================
-- LISTINGS (hartije od vrednosti za Celinu 3)
-- ============================================================

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, base_currency, quote_currency, liquidity,
                      contract_size, contract_unit, settlement_date)
VALUES (  'AAPL', 'Apple Inc.', 'NASDAQ', 'STOCK',
  189.8400, 190.1200, 189.5600, 54230000, 2.3400, NOW(),
  15500000000, 0.0055, NULL, NULL, NULL, 1, NULL, NULL);

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'MSFT', 'Microsoft Corp.', 'NASDAQ', 'STOCK',
  415.2600, 415.8000, 414.7200, 22100000, -1.1800, NOW(),
  7430000000, 0.0072, 1);

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'GOOG', 'Alphabet Inc.', 'NASDAQ', 'STOCK',
  173.4500, 173.9000, 173.0000, 18500000, 0.8700, NOW(),
  12200000000, 0.0000, 1);

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'TSLA', 'Tesla Inc.', 'NASDAQ', 'STOCK',
  248.9100, 249.5000, 248.3200, 72300000, -5.4300, NOW(),
  3180000000, 0.0000, 1);

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'AMZN', 'Amazon.com Inc.', 'NASDAQ', 'STOCK',
  186.3200, 186.8000, 185.8400, 35600000, 1.5600, NOW(),
  10300000000, 0.0000, 1);

-- Forex parovi
INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      base_currency, quote_currency, liquidity, contract_size)
VALUES (  'EUR/USD', 'Euro / US Dollar', 'FOREX', 'FOREX',
  1.0856, 1.0858, 1.0854, 180000000, 0.0012, NOW(),
  'EUR', 'USD', 'HIGH', 1000);

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      base_currency, quote_currency, liquidity, contract_size)
VALUES (  'GBP/USD', 'British Pound / US Dollar', 'FOREX', 'FOREX',
  1.2943, 1.2946, 1.2940, 95000000, -0.0008, NOW(),
  'GBP', 'USD', 'HIGH', 1000);

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      base_currency, quote_currency, liquidity, contract_size)
VALUES (  'USD/JPY', 'US Dollar / Japanese Yen', 'FOREX', 'FOREX',
  149.2300, 149.2600, 149.2000, 120000000, 0.4500, NOW(),
  'USD', 'JPY', 'HIGH', 1000);

-- Futures
INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      contract_size, contract_unit, settlement_date)
VALUES (  'CLM26', 'Crude Oil June 2026', 'CME', 'FUTURES',
  68.4500, 68.5200, 68.3800, 312000, -0.8700, NOW(),
  1000, 'Barrel', '2026-06-20');

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      contract_size, contract_unit, settlement_date)
VALUES (  'GCQ26', 'Gold August 2026', 'CME', 'FUTURES',
  2345.8000, 2346.5000, 2345.1000, 185000, 12.4000, NOW(),
  100, 'Troy Ounce', '2026-08-27');

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      contract_size, contract_unit, settlement_date)
VALUES (  'SIH26', 'Silver March 2026', 'CME', 'FUTURES',
  27.3500, 27.3900, 27.3100, 64000, 0.1800, NOW(),
  5000, 'Troy Ounce', '2026-03-27');

-- Dodatni stocks (10 novih)
INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'META', 'Meta Platforms Inc.', 'NASDAQ', 'STOCK',
  500.1200, 500.6200, 499.6200, 18200000, 3.4500, NOW(),
  2560000000, 0.0000, 1);

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'NVDA', 'NVIDIA Corp.', 'NASDAQ', 'STOCK',
  880.3500, 881.2300, 879.4700, 41500000, 12.6000, NOW(),
  24600000000, 0.0004, 1);

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'JPM', 'JPMorgan Chase & Co.', 'NYSE', 'STOCK',
  195.4200, 195.6200, 195.2200, 9800000, 1.2300, NOW(),
  2870000000, 0.0245, 1);

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'V', 'Visa Inc.', 'NYSE', 'STOCK',
  280.7500, 281.0300, 280.4700, 7200000, 0.8500, NOW(),
  2050000000, 0.0076, 1);

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'JNJ', 'Johnson & Johnson', 'NYSE', 'STOCK',
  155.3200, 155.4800, 155.1600, 6500000, -0.4200, NOW(),
  2410000000, 0.0302, 1);

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'WMT', 'Walmart Inc.', 'NYSE', 'STOCK',
  60.2500, 60.3100, 60.1900, 8400000, 0.3200, NOW(),
  8050000000, 0.0135, 1);

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'DIS', 'The Walt Disney Co.', 'NYSE', 'STOCK',
  115.4800, 115.6000, 115.3600, 10200000, -1.1200, NOW(),
  1830000000, 0.0000, 1);

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'NFLX', 'Netflix Inc.', 'NASDAQ', 'STOCK',
  620.8000, 621.4200, 620.1800, 5300000, 4.5600, NOW(),
  430000000, 0.0000, 1);

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'BA', 'The Boeing Co.', 'NYSE', 'STOCK',
  180.2300, 180.4100, 180.0500, 12100000, -2.3400, NOW(),
  610000000, 0.0000, 1);

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'INTC', 'Intel Corp.', 'NASDAQ', 'STOCK',
  30.1500, 30.1800, 30.1200, 28500000, -0.6500, NOW(),
  4180000000, 0.0133, 1);

-- Dodatni forex parovi (5 novih)
INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      base_currency, quote_currency, liquidity, contract_size)
VALUES (  'USD/CHF', 'US Dollar / Swiss Franc', 'FOREX', 'FOREX',
  0.8815, 0.8818, 0.8812, 72000000, -0.0023, NOW(),
  'USD', 'CHF', 'HIGH', 1000);

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      base_currency, quote_currency, liquidity, contract_size)
VALUES (  'AUD/USD', 'Australian Dollar / US Dollar', 'FOREX', 'FOREX',
  0.6524, 0.6527, 0.6521, 58000000, 0.0015, NOW(),
  'AUD', 'USD', 'MEDIUM', 1000);

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      base_currency, quote_currency, liquidity, contract_size)
VALUES (  'USD/CAD', 'US Dollar / Canadian Dollar', 'FOREX', 'FOREX',
  1.3612, 1.3615, 1.3609, 65000000, 0.0031, NOW(),
  'USD', 'CAD', 'HIGH', 1000);

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      base_currency, quote_currency, liquidity, contract_size)
VALUES (  'EUR/GBP', 'Euro / British Pound', 'FOREX', 'FOREX',
  0.8523, 0.8526, 0.8520, 42000000, -0.0009, NOW(),
  'EUR', 'GBP', 'HIGH', 1000);

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      base_currency, quote_currency, liquidity, contract_size)
VALUES (  'EUR/JPY', 'Euro / Japanese Yen', 'FOREX', 'FOREX',
  163.1200, 163.1800, 163.0600, 55000000, 0.5600, NOW(),
  'EUR', 'JPY', 'HIGH', 1000);

-- Dodatni futures (4 nova)
INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      contract_size, contract_unit, settlement_date)
VALUES (  'NGK26', 'Natural Gas May 2026', 'CME', 'FUTURES',
  2.1050, 2.1100, 2.1000, 145000, 0.0350, NOW(),
  10000, 'MMBtu', '2026-05-28');

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      contract_size, contract_unit, settlement_date)
VALUES (  'ZCN26', 'Corn July 2026', 'CME', 'FUTURES',
  450.2500, 450.7500, 449.7500, 98000, 3.5000, NOW(),
  5000, 'Bushel', '2026-07-14');

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      contract_size, contract_unit, settlement_date)
VALUES (  'ZWN26', 'Wheat July 2026', 'CME', 'FUTURES',
  580.5000, 581.0800, 579.9200, 72000, -4.2500, NOW(),
  5000, 'Bushel', '2026-07-14');

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      contract_size, contract_unit, settlement_date)
VALUES (  'HGK26', 'Copper May 2026', 'CME', 'FUTURES',
  4.2050, 4.2100, 4.2000, 54000, 0.0320, NOW(),
  25000, 'Pound', '2026-05-27');

-- ============================================================
-- LISTING DAILY PRICES (istorijski podaci za grafike)
-- ============================================================

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 185.20, 186.50, 184.10, -1.30, 48000000
FROM listings l WHERE l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 186.50, 188.20, 185.80, 1.30, 51000000
FROM listings l WHERE l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 187.10, 189.00, 186.40, 0.60, 53000000
FROM listings l WHERE l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 188.40, 190.10, 187.50, 1.30, 55000000
FROM listings l WHERE l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 187.50, 189.90, 186.80, -0.90, 52000000
FROM listings l WHERE l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, CURRENT_DATE, 189.84, 190.50, 188.20, 2.34, 54230000
FROM listings l WHERE l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = CURRENT_DATE);

-- EUR/USD istorija
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 1.0830, 1.0865, 1.0810, -0.0015, 175000000
FROM listings l WHERE l.ticker = 'EUR/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 1.0844, 1.0870, 1.0825, 0.0014, 178000000
FROM listings l WHERE l.ticker = 'EUR/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 1.0848, 1.0880, 1.0830, 0.0004, 182000000
FROM listings l WHERE l.ticker = 'EUR/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, CURRENT_DATE, 1.0856, 1.0890, 1.0840, 0.0012, 180000000
FROM listings l WHERE l.ticker = 'EUR/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = CURRENT_DATE);

-- MSFT istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 410.30, 412.50, 409.10, -2.40, 20500000
FROM listings l WHERE l.ticker = 'MSFT'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 412.80, 414.00, 410.50, 2.50, 21800000
FROM listings l WHERE l.ticker = 'MSFT'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 413.50, 415.20, 412.00, 0.70, 22300000
FROM listings l WHERE l.ticker = 'MSFT'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 414.90, 416.80, 413.60, 1.40, 21500000
FROM listings l WHERE l.ticker = 'MSFT'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 416.44, 417.20, 414.50, 1.54, 22100000
FROM listings l WHERE l.ticker = 'MSFT'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- GOOG istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 170.20, 171.80, 169.50, 1.10, 17200000
FROM listings l WHERE l.ticker = 'GOOG'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 171.50, 172.90, 170.80, 1.30, 18000000
FROM listings l WHERE l.ticker = 'GOOG'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 172.10, 173.50, 171.20, 0.60, 17800000
FROM listings l WHERE l.ticker = 'GOOG'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 172.80, 174.00, 171.90, 0.70, 18500000
FROM listings l WHERE l.ticker = 'GOOG'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 172.58, 174.20, 172.00, -0.22, 18500000
FROM listings l WHERE l.ticker = 'GOOG'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- AMZN istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 182.40, 183.80, 181.50, -1.20, 33000000
FROM listings l WHERE l.ticker = 'AMZN'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 183.50, 185.00, 182.80, 1.10, 34200000
FROM listings l WHERE l.ticker = 'AMZN'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 184.20, 185.50, 183.40, 0.70, 35000000
FROM listings l WHERE l.ticker = 'AMZN'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 185.10, 186.80, 184.30, 0.90, 35600000
FROM listings l WHERE l.ticker = 'AMZN'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 184.76, 186.20, 184.00, -0.34, 35600000
FROM listings l WHERE l.ticker = 'AMZN'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- TSLA istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 258.40, 262.00, 255.80, 6.20, 68000000
FROM listings l WHERE l.ticker = 'TSLA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 255.10, 259.50, 253.20, -3.30, 70500000
FROM listings l WHERE l.ticker = 'TSLA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 252.80, 256.00, 251.00, -2.30, 72000000
FROM listings l WHERE l.ticker = 'TSLA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 251.50, 254.80, 250.10, -1.30, 71000000
FROM listings l WHERE l.ticker = 'TSLA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 254.34, 256.20, 250.80, 2.84, 72300000
FROM listings l WHERE l.ticker = 'TSLA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- META istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 492.30, 495.00, 490.50, -2.80, 16500000
FROM listings l WHERE l.ticker = 'META'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 494.80, 497.50, 493.10, 2.50, 17200000
FROM listings l WHERE l.ticker = 'META'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 496.20, 498.80, 494.50, 1.40, 17800000
FROM listings l WHERE l.ticker = 'META'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 497.80, 500.50, 496.00, 1.60, 18200000
FROM listings l WHERE l.ticker = 'META'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 496.67, 499.80, 495.20, -1.13, 18200000
FROM listings l WHERE l.ticker = 'META'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- NVDA istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 860.50, 865.00, 855.20, -8.30, 38000000
FROM listings l WHERE l.ticker = 'NVDA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 864.20, 870.00, 858.50, 3.70, 39500000
FROM listings l WHERE l.ticker = 'NVDA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 868.00, 874.50, 863.80, 3.80, 40200000
FROM listings l WHERE l.ticker = 'NVDA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 873.40, 880.00, 868.50, 5.40, 41500000
FROM listings l WHERE l.ticker = 'NVDA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 867.75, 878.00, 865.00, -5.65, 41500000
FROM listings l WHERE l.ticker = 'NVDA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- JPM istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 192.80, 194.00, 191.50, 0.90, 9200000
FROM listings l WHERE l.ticker = 'JPM'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 193.50, 194.80, 192.60, 0.70, 9500000
FROM listings l WHERE l.ticker = 'JPM'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 194.10, 195.30, 193.20, 0.60, 9600000
FROM listings l WHERE l.ticker = 'JPM'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 194.80, 196.00, 193.90, 0.70, 9800000
FROM listings l WHERE l.ticker = 'JPM'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 194.19, 195.80, 193.50, -0.61, 9800000
FROM listings l WHERE l.ticker = 'JPM'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- V istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 278.20, 279.50, 277.00, -0.60, 6800000
FROM listings l WHERE l.ticker = 'V'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 279.10, 280.30, 278.20, 0.90, 7000000
FROM listings l WHERE l.ticker = 'V'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 279.80, 281.00, 279.00, 0.70, 7100000
FROM listings l WHERE l.ticker = 'V'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 280.50, 281.80, 279.60, 0.70, 7200000
FROM listings l WHERE l.ticker = 'V'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 279.90, 281.50, 279.20, -0.60, 7200000
FROM listings l WHERE l.ticker = 'V'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- JNJ istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 156.80, 157.50, 155.90, 1.20, 6200000
FROM listings l WHERE l.ticker = 'JNJ'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 156.30, 157.10, 155.60, -0.50, 6300000
FROM listings l WHERE l.ticker = 'JNJ'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 155.90, 156.80, 155.20, -0.40, 6400000
FROM listings l WHERE l.ticker = 'JNJ'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 155.50, 156.40, 154.80, -0.40, 6500000
FROM listings l WHERE l.ticker = 'JNJ'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 155.74, 156.50, 155.00, 0.24, 6500000
FROM listings l WHERE l.ticker = 'JNJ'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- WMT istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 59.40, 59.90, 59.00, 0.25, 7800000
FROM listings l WHERE l.ticker = 'WMT'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 59.70, 60.10, 59.30, 0.30, 8000000
FROM listings l WHERE l.ticker = 'WMT'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 59.90, 60.30, 59.50, 0.20, 8200000
FROM listings l WHERE l.ticker = 'WMT'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 60.10, 60.50, 59.70, 0.20, 8400000
FROM listings l WHERE l.ticker = 'WMT'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 59.93, 60.40, 59.60, -0.17, 8400000
FROM listings l WHERE l.ticker = 'WMT'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- DIS istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 118.20, 119.50, 117.30, 1.80, 9500000
FROM listings l WHERE l.ticker = 'DIS'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 117.50, 118.80, 116.80, -0.70, 9800000
FROM listings l WHERE l.ticker = 'DIS'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 116.80, 118.00, 116.00, -0.70, 10000000
FROM listings l WHERE l.ticker = 'DIS'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 116.10, 117.50, 115.50, -0.70, 10200000
FROM listings l WHERE l.ticker = 'DIS'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 116.60, 117.80, 115.80, 0.50, 10200000
FROM listings l WHERE l.ticker = 'DIS'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- NFLX istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 610.50, 614.00, 608.00, -3.20, 4800000
FROM listings l WHERE l.ticker = 'NFLX'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 613.80, 616.50, 611.20, 3.30, 5000000
FROM listings l WHERE l.ticker = 'NFLX'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 615.20, 618.00, 613.50, 1.40, 5100000
FROM listings l WHERE l.ticker = 'NFLX'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 617.50, 621.00, 615.80, 2.30, 5300000
FROM listings l WHERE l.ticker = 'NFLX'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 616.24, 620.00, 614.50, -1.26, 5300000
FROM listings l WHERE l.ticker = 'NFLX'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- BA istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 184.50, 186.00, 183.00, 2.10, 11000000
FROM listings l WHERE l.ticker = 'BA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 183.20, 185.50, 182.00, -1.30, 11500000
FROM listings l WHERE l.ticker = 'BA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 182.10, 183.80, 181.00, -1.10, 11800000
FROM listings l WHERE l.ticker = 'BA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 181.30, 183.00, 180.50, -0.80, 12100000
FROM listings l WHERE l.ticker = 'BA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 182.57, 183.50, 180.80, 1.27, 12100000
FROM listings l WHERE l.ticker = 'BA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- INTC istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 31.20, 31.80, 30.80, 0.40, 26000000
FROM listings l WHERE l.ticker = 'INTC'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 30.90, 31.50, 30.50, -0.30, 27000000
FROM listings l WHERE l.ticker = 'INTC'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 30.60, 31.20, 30.20, -0.30, 27500000
FROM listings l WHERE l.ticker = 'INTC'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 30.40, 31.00, 30.00, -0.20, 28500000
FROM listings l WHERE l.ticker = 'INTC'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 30.80, 31.10, 30.30, 0.40, 28500000
FROM listings l WHERE l.ticker = 'INTC'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- GBP/USD istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 1.2910, 1.2940, 1.2885, 0.0018, 90000000
FROM listings l WHERE l.ticker = 'GBP/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 1.2925, 1.2955, 1.2900, 0.0015, 92000000
FROM listings l WHERE l.ticker = 'GBP/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 1.2938, 1.2960, 1.2910, 0.0013, 93000000
FROM listings l WHERE l.ticker = 'GBP/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 1.2950, 1.2975, 1.2920, 0.0012, 94000000
FROM listings l WHERE l.ticker = 'GBP/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 1.2935, 1.2965, 1.2915, -0.0015, 95000000
FROM listings l WHERE l.ticker = 'GBP/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- USD/JPY istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 150.20, 150.80, 149.60, 0.85, 115000000
FROM listings l WHERE l.ticker = 'USD/JPY'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 150.60, 151.20, 150.00, 0.40, 117000000
FROM listings l WHERE l.ticker = 'USD/JPY'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 150.90, 151.50, 150.30, 0.30, 118000000
FROM listings l WHERE l.ticker = 'USD/JPY'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 150.50, 151.30, 150.00, -0.40, 119000000
FROM listings l WHERE l.ticker = 'USD/JPY'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 150.78, 151.40, 150.20, 0.28, 120000000
FROM listings l WHERE l.ticker = 'USD/JPY'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- USD/CHF istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 0.8860, 0.8880, 0.8840, 0.0015, 68000000
FROM listings l WHERE l.ticker = 'USD/CHF'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 0.8845, 0.8870, 0.8825, -0.0015, 69000000
FROM listings l WHERE l.ticker = 'USD/CHF'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 0.8830, 0.8855, 0.8810, -0.0015, 70000000
FROM listings l WHERE l.ticker = 'USD/CHF'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 0.8820, 0.8845, 0.8800, -0.0010, 71000000
FROM listings l WHERE l.ticker = 'USD/CHF'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 0.8808, 0.8835, 0.8790, -0.0012, 72000000
FROM listings l WHERE l.ticker = 'USD/CHF'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- AUD/USD istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 0.6490, 0.6510, 0.6470, -0.0010, 55000000
FROM listings l WHERE l.ticker = 'AUD/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 0.6500, 0.6520, 0.6480, 0.0010, 56000000
FROM listings l WHERE l.ticker = 'AUD/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 0.6508, 0.6530, 0.6490, 0.0008, 57000000
FROM listings l WHERE l.ticker = 'AUD/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 0.6515, 0.6535, 0.6495, 0.0007, 57500000
FROM listings l WHERE l.ticker = 'AUD/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 0.6509, 0.6530, 0.6490, -0.0006, 58000000
FROM listings l WHERE l.ticker = 'AUD/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- USD/CAD istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 1.3560, 1.3585, 1.3535, -0.0020, 62000000
FROM listings l WHERE l.ticker = 'USD/CAD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 1.3575, 1.3600, 1.3550, 0.0015, 63000000
FROM listings l WHERE l.ticker = 'USD/CAD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 1.3588, 1.3610, 1.3565, 0.0013, 63500000
FROM listings l WHERE l.ticker = 'USD/CAD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 1.3598, 1.3620, 1.3575, 0.0010, 64000000
FROM listings l WHERE l.ticker = 'USD/CAD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 1.3581, 1.3610, 1.3560, -0.0017, 65000000
FROM listings l WHERE l.ticker = 'USD/CAD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- EUR/GBP istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 0.8545, 0.8560, 0.8530, 0.0012, 40000000
FROM listings l WHERE l.ticker = 'EUR/GBP'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 0.8538, 0.8555, 0.8520, -0.0007, 40500000
FROM listings l WHERE l.ticker = 'EUR/GBP'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 0.8530, 0.8548, 0.8512, -0.0008, 41000000
FROM listings l WHERE l.ticker = 'EUR/GBP'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 0.8525, 0.8540, 0.8508, -0.0005, 41500000
FROM listings l WHERE l.ticker = 'EUR/GBP'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 0.8532, 0.8548, 0.8515, 0.0007, 42000000
FROM listings l WHERE l.ticker = 'EUR/GBP'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- EUR/JPY istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 162.10, 162.80, 161.50, 0.65, 52000000
FROM listings l WHERE l.ticker = 'EUR/JPY'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 162.45, 163.10, 161.80, 0.35, 53000000
FROM listings l WHERE l.ticker = 'EUR/JPY'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 162.70, 163.30, 162.10, 0.25, 53500000
FROM listings l WHERE l.ticker = 'EUR/JPY'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 162.90, 163.50, 162.30, 0.20, 54000000
FROM listings l WHERE l.ticker = 'EUR/JPY'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 162.56, 163.20, 162.00, -0.34, 55000000
FROM listings l WHERE l.ticker = 'EUR/JPY'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- CLM26 futures istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 69.80, 70.50, 69.20, 0.95, 295000
FROM listings l WHERE l.ticker = 'CLM26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 69.50, 70.20, 68.80, -0.30, 300000
FROM listings l WHERE l.ticker = 'CLM26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 69.10, 69.80, 68.50, -0.40, 305000
FROM listings l WHERE l.ticker = 'CLM26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 68.80, 69.50, 68.30, -0.30, 308000
FROM listings l WHERE l.ticker = 'CLM26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 69.32, 69.80, 68.60, 0.52, 312000
FROM listings l WHERE l.ticker = 'CLM26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- GCQ26 gold futures istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 2325.00, 2332.00, 2318.00, 8.50, 170000
FROM listings l WHERE l.ticker = 'GCQ26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 2330.50, 2338.00, 2324.00, 5.50, 175000
FROM listings l WHERE l.ticker = 'GCQ26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 2335.80, 2342.00, 2328.00, 5.30, 178000
FROM listings l WHERE l.ticker = 'GCQ26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 2340.20, 2348.00, 2333.00, 4.40, 180000
FROM listings l WHERE l.ticker = 'GCQ26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 2333.40, 2346.00, 2330.00, -6.80, 185000
FROM listings l WHERE l.ticker = 'GCQ26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- SIH26 silver futures istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 26.80, 27.10, 26.50, -0.25, 58000
FROM listings l WHERE l.ticker = 'SIH26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 26.95, 27.20, 26.70, 0.15, 59000
FROM listings l WHERE l.ticker = 'SIH26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 27.10, 27.35, 26.85, 0.15, 60000
FROM listings l WHERE l.ticker = 'SIH26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 27.20, 27.45, 26.95, 0.10, 62000
FROM listings l WHERE l.ticker = 'SIH26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 27.17, 27.40, 26.90, -0.03, 64000
FROM listings l WHERE l.ticker = 'SIH26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- NGK26 natural gas futures istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 2.0500, 2.0800, 2.0200, -0.0200, 138000
FROM listings l WHERE l.ticker = 'NGK26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 2.0650, 2.0900, 2.0400, 0.0150, 140000
FROM listings l WHERE l.ticker = 'NGK26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 2.0750, 2.1000, 2.0500, 0.0100, 141000
FROM listings l WHERE l.ticker = 'NGK26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 2.0850, 2.1100, 2.0600, 0.0100, 143000
FROM listings l WHERE l.ticker = 'NGK26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 2.0700, 2.1050, 2.0500, -0.0150, 145000
FROM listings l WHERE l.ticker = 'NGK26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- ZCN26 corn futures istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 444.50, 447.00, 442.00, 2.50, 92000
FROM listings l WHERE l.ticker = 'ZCN26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 445.80, 448.00, 443.50, 1.30, 93000
FROM listings l WHERE l.ticker = 'ZCN26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 447.20, 449.50, 445.00, 1.40, 94000
FROM listings l WHERE l.ticker = 'ZCN26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 448.80, 451.00, 446.50, 1.60, 96000
FROM listings l WHERE l.ticker = 'ZCN26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 446.75, 450.00, 445.00, -2.05, 98000
FROM listings l WHERE l.ticker = 'ZCN26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- ZWN26 wheat futures istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 588.00, 592.00, 585.00, 3.50, 68000
FROM listings l WHERE l.ticker = 'ZWN26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 586.50, 590.00, 583.50, -1.50, 69000
FROM listings l WHERE l.ticker = 'ZWN26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 584.80, 588.00, 582.00, -1.70, 70000
FROM listings l WHERE l.ticker = 'ZWN26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 583.00, 586.50, 580.50, -1.80, 71000
FROM listings l WHERE l.ticker = 'ZWN26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 584.75, 587.00, 581.50, 1.75, 72000
FROM listings l WHERE l.ticker = 'ZWN26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- HGK26 copper futures istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '5 days'), 4.1500, 4.1800, 4.1200, 0.0250, 50000
FROM listings l WHERE l.ticker = 'HGK26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '5 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '4 days'), 4.1650, 4.1900, 4.1400, 0.0150, 51000
FROM listings l WHERE l.ticker = 'HGK26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '4 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '3 days'), 4.1780, 4.2050, 4.1550, 0.0130, 52000
FROM listings l WHERE l.ticker = 'HGK26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '3 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '2 days'), 4.1900, 4.2150, 4.1650, 0.0120, 53000
FROM listings l WHERE l.ticker = 'HGK26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '2 days'));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, (CURRENT_DATE - INTERVAL '1 days'), 4.1730, 4.2100, 4.1600, -0.0170, 54000
FROM listings l WHERE l.ticker = 'HGK26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = (CURRENT_DATE - INTERVAL '1 days'));

-- ============================================================
-- ACTUARY INFO (aktuarski podaci za zaposlene)
-- ============================================================

INSERT INTO actuary_info (employee_id, actuary_type, daily_limit, used_limit, need_approval)
SELECT e.id, 'SUPERVISOR', NULL, 0, 0
FROM employees e WHERE e.email = 'nikola.milenkovic@banka.rs'
AND NOT EXISTS (SELECT 1 FROM actuary_info WHERE employee_id = e.id);

INSERT INTO actuary_info (employee_id, actuary_type, daily_limit, used_limit, need_approval)
SELECT e.id, 'AGENT', 100000, 0, 0
FROM employees e WHERE e.email = 'tamara.pavlovic@banka.rs'
AND NOT EXISTS (SELECT 1 FROM actuary_info WHERE employee_id = e.id);

INSERT INTO actuary_info (employee_id, actuary_type, daily_limit, used_limit, need_approval)
SELECT e.id, 'AGENT', 50000, 15000, 1
FROM employees e WHERE e.email = 'nemanja.savic@banka.rs'
AND NOT EXISTS (SELECT 1 FROM actuary_info WHERE employee_id = e.id);

-- ============================================================
-- PORTFOLIOS (hartije od vrednosti u vlasnistvu korisnika)
-- ============================================================
-- BITNO: user_id je clients.id (za CLIENT) ili employees.id (za EMPLOYEE).
-- Dvoje se dodatno razdvaja kolonom user_role — clients i employees
-- imaju nezavisne auto_increment sekvence koje se preklapaju.
--
-- clients.id: 1=Stefan, 2=Milica, 3=Lazar, 4=Ana
-- employees.id (samo agenti imaju portfolio): 4=Tamara, 5=Djordje, 6=Maja
-- ============================================================

-- ---------- STEFAN (client_id=1) ----------
-- AAPL 50 @ 145 (solid profit), MSFT 30 @ 380.5 (public 10 za OTC demo),
-- TSLA 20 @ 265 (u gubitku), CLM26 futures 5
INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 1, 'CLIENT', l.id, l.ticker, l.name, 'STOCK', 50, 145.0000, 0, NOW()
FROM listings l WHERE l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 1 AND p.user_role = 'CLIENT' AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 1, 'CLIENT', l.id, l.ticker, l.name, 'STOCK', 30, 380.5000, 10, NOW()
FROM listings l WHERE l.ticker = 'MSFT'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 1 AND p.user_role = 'CLIENT' AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 1, 'CLIENT', l.id, l.ticker, l.name, 'STOCK', 20, 265.0000, 0, NOW()
FROM listings l WHERE l.ticker = 'TSLA'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 1 AND p.user_role = 'CLIENT' AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 1, 'CLIENT', l.id, l.ticker, l.name, 'FUTURES', 5, 65.2000, 0, NOW()
FROM listings l WHERE l.ticker = 'CLM26'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 1 AND p.user_role = 'CLIENT' AND p.listing_id = l.id);

-- ---------- MILICA (client_id=2) ----------
-- GOOG 100 @ 155 (public 30 — glavni OTC izlog), AMZN 15 @ 172.8 (public 5)
INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 2, 'CLIENT', l.id, l.ticker, l.name, 'STOCK', 100, 155.0000, 30, NOW()
FROM listings l WHERE l.ticker = 'GOOG'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 2 AND p.user_role = 'CLIENT' AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 2, 'CLIENT', l.id, l.ticker, l.name, 'STOCK', 15, 172.8000, 5, NOW()
FROM listings l WHERE l.ticker = 'AMZN'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 2 AND p.user_role = 'CLIENT' AND p.listing_id = l.id);

-- ---------- LAZAR (client_id=3) ----------
-- TSLA 15 @ 255 (public 8 — OTC TSLA izlog), GOOG 30 @ 160 (public 0)
INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 3, 'CLIENT', l.id, l.ticker, l.name, 'STOCK', 15, 255.0000, 8, NOW()
FROM listings l WHERE l.ticker = 'TSLA'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 3 AND p.user_role = 'CLIENT' AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 3, 'CLIENT', l.id, l.ticker, l.name, 'STOCK', 30, 160.0000, 0, NOW()
FROM listings l WHERE l.ticker = 'GOOG'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 3 AND p.user_role = 'CLIENT' AND p.listing_id = l.id);

-- ---------- ANA (client_id=4) ----------
-- NVDA 5 @ 810 (public 3), AAPL 10 @ 175 (public 5 — AAPL OTC izlog)
INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 4, 'CLIENT', l.id, l.ticker, l.name, 'STOCK', 5, 810.0000, 3, NOW()
FROM listings l WHERE l.ticker = 'NVDA'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 4 AND p.user_role = 'CLIENT' AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 4, 'CLIENT', l.id, l.ticker, l.name, 'STOCK', 10, 175.0000, 5, NOW()
FROM listings l WHERE l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 4 AND p.user_role = 'CLIENT' AND p.listing_id = l.id);


-- ============================================================
-- OPTIONS TEST SEED (Issue 21 - exercise)
-- Dodati odmah posle ACTUARY INFO bloka
-- ============================================================

-- 1) CALL ITM - validna za uspešan exercise
INSERT INTO options (
    stock_listing_id,
    option_type,
    strike_price,
    implied_volatility,
    open_interest,
    settlement_date,
    contract_size,
    price,
    ask,
    bid,
    volume,
    ticker,
    created_at
)
SELECT
    l.id,
    'CALL',
    180.0000,
    0.2500,
    5,
    CURRENT_DATE + INTERVAL '15 days',
    100,
    12.5000,
    13.0000,
    12.0000,
    1200,
    'AAPL_TEST_CALL_ITM',
    NOW()
FROM listings l
WHERE l.ticker = 'AAPL'
  AND NOT EXISTS (
    SELECT 1 FROM options o WHERE o.ticker = 'AAPL_TEST_CALL_ITM'
    );

-- 2) CALL OTM - treba da vrati 400 (nije in-the-money)
INSERT INTO options (
    stock_listing_id,
    option_type,
    strike_price,
    implied_volatility,
    open_interest,
    settlement_date,
    contract_size,
    price,
    ask,
    bid,
    volume,
    ticker,
    created_at
)
SELECT
    l.id,
    'CALL',
    210.0000,
    0.2500,
    5,
    CURRENT_DATE + INTERVAL '15 days',
    100,
    4.2000,
    4.5000,
    4.0000,
    950,
    'AAPL_TEST_CALL_OTM',
    NOW()
FROM listings l
WHERE l.ticker = 'AAPL'
  AND NOT EXISTS (
    SELECT 1 FROM options o WHERE o.ticker = 'AAPL_TEST_CALL_OTM'
    );

-- 3) CALL expired - treba da vrati 400 (istekla)
INSERT INTO options (
    stock_listing_id,
    option_type,
    strike_price,
    implied_volatility,
    open_interest,
    settlement_date,
    contract_size,
    price,
    ask,
    bid,
    volume,
    ticker,
    created_at
)
SELECT
    l.id,
    'CALL',
    180.0000,
    0.2500,
    5,
    CURRENT_DATE - INTERVAL '1 days',
    100,
    10.5000,
    10.9000,
    10.1000,
    700,
    'AAPL_TEST_CALL_EXPIRED',
    NOW()
FROM listings l
WHERE l.ticker = 'AAPL'
  AND NOT EXISTS (
    SELECT 1 FROM options o WHERE o.ticker = 'AAPL_TEST_CALL_EXPIRED'
    );

-- 4) CALL ITM ali open_interest = 0 - treba da vrati 400
INSERT INTO options (
    stock_listing_id,
    option_type,
    strike_price,
    implied_volatility,
    open_interest,
    settlement_date,
    contract_size,
    price,
    ask,
    bid,
    volume,
    ticker,
    created_at
)
SELECT
    l.id,
    'CALL',
    170.0000,
    0.2500,
    0,
    CURRENT_DATE + INTERVAL '15 days',
    100,
    15.2000,
    15.7000,
    14.8000,
    300,
    'AAPL_TEST_CALL_ZERO_OI',
    NOW()
FROM listings l
WHERE l.ticker = 'AAPL'
  AND NOT EXISTS (
    SELECT 1 FROM options o WHERE o.ticker = 'AAPL_TEST_CALL_ZERO_OI'
    );

-- 5) PUT ITM - validna za uspešan exercise
INSERT INTO options (
    stock_listing_id,
    option_type,
    strike_price,
    implied_volatility,
    open_interest,
    settlement_date,
    contract_size,
    price,
    ask,
    bid,
    volume,
    ticker,
    created_at
)
SELECT
    l.id,
    'PUT',
    430.0000,
    0.2300,
    4,
    CURRENT_DATE + INTERVAL '20 days',
    100,
    14.8000,
    15.3000,
    14.2000,
    1100,
    'MSFT_TEST_PUT_ITM',
    NOW()
FROM listings l
WHERE l.ticker = 'MSFT'
  AND NOT EXISTS (
    SELECT 1 FROM options o WHERE o.ticker = 'MSFT_TEST_PUT_ITM'
    );

-- 6) PUT OTM - treba da vrati 400
INSERT INTO options (
    stock_listing_id,
    option_type,
    strike_price,
    implied_volatility,
    open_interest,
    settlement_date,
    contract_size,
    price,
    ask,
    bid,
    volume,
    ticker,
    created_at
)
SELECT
    l.id,
    'PUT',
    390.0000,
    0.2300,
    4,
    CURRENT_DATE + INTERVAL '20 days',
    100,
    5.1000,
    5.4000,
    4.9000,
    800,
    'MSFT_TEST_PUT_OTM',
    NOW()
FROM listings l
WHERE l.ticker = 'MSFT'
  AND NOT EXISTS (
    SELECT 1 FROM options o WHERE o.ticker = 'MSFT_TEST_PUT_OTM'
    );

DELETE FROM options
WHERE ticker IN (
                 'AAPL_TEST_CALL_ITM',
                 'AAPL_TEST_CALL_OTM',
                 'AAPL_TEST_CALL_EXPIRED',
                 'AAPL_TEST_CALL_ZERO_OI',
                 'MSFT_TEST_PUT_ITM',
                 'MSFT_TEST_PUT_OTM'
    );

INSERT INTO options (
    stock_listing_id,
    option_type,
    strike_price,
    implied_volatility,
    open_interest,
    settlement_date,
    contract_size,
    price,
    ask,
    bid,
    volume,
    ticker,
    created_at
)
SELECT
    l.id, 'CALL', 180.0000, 0.2500, 5,
    CURRENT_DATE + INTERVAL '15 days', 100,
    12.5000, 13.0000, 12.0000, 1200,
    'AAPL_TEST_CALL_ITM', NOW()
FROM listings l
WHERE l.ticker = 'AAPL';

INSERT INTO options (
    stock_listing_id,
    option_type,
    strike_price,
    implied_volatility,
    open_interest,
    settlement_date,
    contract_size,
    price,
    ask,
    bid,
    volume,
    ticker,
    created_at
)
SELECT
    l.id, 'CALL', 210.0000, 0.2500, 5,
    CURRENT_DATE + INTERVAL '15 days', 100,
    4.2000, 4.5000, 4.0000, 950,
    'AAPL_TEST_CALL_OTM', NOW()
FROM listings l
WHERE l.ticker = 'AAPL';

INSERT INTO options (
    stock_listing_id,
    option_type,
    strike_price,
    implied_volatility,
    open_interest,
    settlement_date,
    contract_size,
    price,
    ask,
    bid,
    volume,
    ticker,
    created_at
)
SELECT
    l.id, 'CALL', 180.0000, 0.2500, 5,
    CURRENT_DATE - INTERVAL '1 days', 100,
    10.5000, 10.9000, 10.1000, 700,
    'AAPL_TEST_CALL_EXPIRED', NOW()
FROM listings l
WHERE l.ticker = 'AAPL';

INSERT INTO options (
    stock_listing_id,
    option_type,
    strike_price,
    implied_volatility,
    open_interest,
    settlement_date,
    contract_size,
    price,
    ask,
    bid,
    volume,
    ticker,
    created_at
)
SELECT
    l.id, 'CALL', 170.0000, 0.2500, 0,
    CURRENT_DATE + INTERVAL '15 days', 100,
    15.2000, 15.7000, 14.8000, 300,
    'AAPL_TEST_CALL_ZERO_OI', NOW()
FROM listings l
WHERE l.ticker = 'AAPL';

INSERT INTO options (
    stock_listing_id,
    option_type,
    strike_price,
    implied_volatility,
    open_interest,
    settlement_date,
    contract_size,
    price,
    ask,
    bid,
    volume,
    ticker,
    created_at
)
SELECT
    l.id, 'PUT', 430.0000, 0.2300, 4,
    CURRENT_DATE + INTERVAL '20 days', 100,
    14.8000, 15.3000, 14.2000, 1100,
    'MSFT_TEST_PUT_ITM', NOW()
FROM listings l
WHERE l.ticker = 'MSFT';

INSERT INTO options (
    stock_listing_id,
    option_type,
    strike_price,
    implied_volatility,
    open_interest,
    settlement_date,
    contract_size,
    price,
    ask,
    bid,
    volume,
    ticker,
    created_at
)
SELECT
    l.id, 'PUT', 390.0000, 0.2300, 4,
    CURRENT_DATE + INTERVAL '20 days', 100,
    5.1000, 5.4000, 4.9000, 800,
    'MSFT_TEST_PUT_OTM', NOW()
FROM listings l
WHERE l.ticker = 'MSFT';

SELECT id, ticker, option_type, strike_price, open_interest, settlement_date
FROM options
WHERE ticker IN (
                 'AAPL_TEST_CALL_ITM',
                 'AAPL_TEST_CALL_OTM',
                 'AAPL_TEST_CALL_EXPIRED',
                 'AAPL_TEST_CALL_ZERO_OI',
                 'MSFT_TEST_PUT_ITM',
                 'MSFT_TEST_PUT_OTM'
    );

-- ============================================================
-- CELINA 3: Dodatni podaci - Aktuari, Orderi, Margin, Porez
-- ============================================================

-- Admini su supervizori (svaki admin = supervizor po specifikaciji)
-- employee_id: 1=Marko, 2=Jelena (novi), 3=Nikola, 4=Tamara, 5=Djordje, 6=Maja, 7=Vuk
-- Marko (employee 1) i Jelena (employee 2) dodati kao supervizori
INSERT INTO actuary_info (actuary_type, daily_limit, need_approval, used_limit, employee_id)
SELECT 'SUPERVISOR', NULL, 0, 0.00, id FROM employees WHERE email = 'marko.petrovic@banka.rs';

INSERT INTO actuary_info (actuary_type, daily_limit, need_approval, used_limit, employee_id)
SELECT 'SUPERVISOR', NULL, 0, 0.00, id FROM employees WHERE email = 'jelena.djordjevic@banka.rs';

-- Dodaj vise agenata (Djordje=agent, Maja=agent)
INSERT INTO actuary_info (actuary_type, daily_limit, need_approval, used_limit, employee_id)
SELECT 'AGENT', 150000.00, 0, 25000.00, id FROM employees WHERE email = 'djordje.jankovic@banka.rs';

INSERT INTO actuary_info (actuary_type, daily_limit, need_approval, used_limit, employee_id)
SELECT 'AGENT', 200000.00, 1, 180000.00, id FROM employees WHERE email = 'maja.ristic@banka.rs';

-- ============================================================
-- ORDERS (sample nalozi za prikaz)
-- ============================================================
-- BITNO: user_id = clients.id (za CLIENT) ili employees.id (za EMPLOYEE).
--   clients: 1=Stefan, 2=Milica, 3=Lazar, 4=Ana
--   employees (agenti): 4=Tamara, 5=Djordje; supervizor: 3=Nikola
-- listing_id 1=AAPL, 2=MSFT, 3=GOOG, 4=TSLA, 5=AMZN, 6=NVDA, 9=CLM26

INSERT INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    -- STEFAN (client 1) — solidno istorijsko poslovanje
    (1, 0, 0, 'No need for approval', 9250.0000, 1, (NOW() - INTERVAL '14 days'),
     'BUY', 1, (NOW() - INTERVAL '14 days'), NULL, 0, 'MARKET', 185.0000,
     50, 0, 'DONE', NULL, 1, 'CLIENT', 1),                       -- BUY 50 AAPL DONE
    (1, 0, 0, 'No need for approval', 11415.0000, 1, (NOW() - INTERVAL '10 days'),
     'BUY', 1, (NOW() - INTERVAL '10 days'), NULL, 0, 'MARKET', 380.5000,
     30, 0, 'DONE', NULL, 1, 'CLIENT', 2),                       -- BUY 30 MSFT DONE
    (1, 0, 0, 'No need for approval', 5300.0000, 1, (NOW() - INTERVAL '6 days'),
     'BUY', 1, (NOW() - INTERVAL '6 days'), NULL, 0, 'MARKET', 265.0000,
     20, 0, 'DONE', NULL, 1, 'CLIENT', 4),                       -- BUY 20 TSLA DONE (u gubitku)
    (1, 0, 0, 'No need for approval', 5200.0000, 1, (NOW() - INTERVAL '2 days'),
     'BUY', 0, NOW(), 170.0000, 0, 'LIMIT', 173.0000,
     30, 15, 'APPROVED', NULL, 1, 'CLIENT', 3),                  -- LIMIT BUY 30 GOOG aktivan
    (1, 0, 1, NULL, 4750.0000, 1, NOW(),
     'SELL', 0, NOW(), NULL, 0, 'MARKET', 190.0000,
     25, 25, 'PENDING', NULL, 1, 'CLIENT', 1),                   -- SELL AAPL AON PENDING (ceka supervizora)

    -- MILICA (client 2)
    (4, 0, 0, 'No need for approval', 15500.0000, 1, (NOW() - INTERVAL '8 days'),
     'BUY', 1, (NOW() - INTERVAL '8 days'), NULL, 0, 'MARKET', 155.0000,
     100, 0, 'DONE', NULL, 2, 'CLIENT', 3),                      -- BUY 100 GOOG DONE
    (4, 0, 0, 'No need for approval', 2592.0000, 1, (NOW() - INTERVAL '3 days'),
     'BUY', 1, (NOW() - INTERVAL '3 days'), NULL, 0, 'MARKET', 172.8000,
     15, 0, 'DONE', NULL, 2, 'CLIENT', 5),                       -- BUY 15 AMZN DONE

    -- LAZAR (client 3)
    (7, 0, 0, 'No need for approval', 3825.0000, 1, (NOW() - INTERVAL '12 days'),
     'BUY', 1, (NOW() - INTERVAL '12 days'), NULL, 0, 'MARKET', 255.0000,
     15, 0, 'DONE', NULL, 3, 'CLIENT', 4),                       -- BUY 15 TSLA DONE
    (7, 0, 0, 'No need for approval', 4800.0000, 1, (NOW() - INTERVAL '5 days'),
     'BUY', 1, (NOW() - INTERVAL '5 days'), NULL, 0, 'MARKET', 160.0000,
     30, 0, 'DONE', NULL, 3, 'CLIENT', 3),                       -- BUY 30 GOOG DONE

    -- ANA (client 4)
    (10, 0, 0, 'No need for approval', 4050.0000, 1, (NOW() - INTERVAL '9 days'),
     'BUY', 1, (NOW() - INTERVAL '9 days'), NULL, 0, 'MARKET', 810.0000,
     5, 0, 'DONE', NULL, 4, 'CLIENT', 6),                        -- BUY 5 NVDA DONE
    (10, 0, 0, 'No need for approval', 1750.0000, 1, (NOW() - INTERVAL '4 days'),
     'BUY', 1, (NOW() - INTERVAL '4 days'), NULL, 0, 'MARKET', 175.0000,
     10, 0, 'DONE', NULL, 4, 'CLIENT', 1),                       -- BUY 10 AAPL DONE

    -- TAMARA (employee 4, agent) — treba odobrenje za nove naloge
    (NULL, 0, 0, NULL, 12500.0000, 1, (NOW() - INTERVAL '1 hours'),
     'BUY', 0, (NOW() - INTERVAL '1 hours'), NULL, 0, 'MARKET', 250.0000,
     50, 50, 'PENDING', NULL, 4, 'EMPLOYEE', 4),                 -- BUY TSLA PENDING
    (NULL, 1, 0, 'Nikola Milenkovic', 6000.0000, 1, (NOW() - INTERVAL '2 days'),
     'SELL', 0, NOW(), 400.0000, 0, 'STOP_LIMIT', 410.0000,
     15, 10, 'APPROVED', 395.0000, 4, 'EMPLOYEE', 2),            -- STOP-LIMIT SELL MSFT APPROVED

    -- DJORDJE (employee 5, agent)
    (NULL, 0, 0, 'Nikola Milenkovic', 325000.0000, 1000, (NOW() - INTERVAL '7 days'),
     'BUY', 1, (NOW() - INTERVAL '6 days'), NULL, 0, 'MARKET', 65.0000,
     5, 0, 'DONE', NULL, 5, 'EMPLOYEE', 9);                      -- BUY 5 futures CLM26 DONE

-- ============================================================
-- MARGIN ACCOUNTS
-- ============================================================
-- client_id: 1=Stefan, 2=Milica, 3=Lazar, 4=Ana
-- account_id: 1=Stefan RSD, 4=Milica RSD, 7=Lazar RSD, 10=Ana Youth RSD

-- Margin accounts: only insert if user doesn't already have one
INSERT INTO margin_accounts (bank_participation, created_at, initial_margin, loan_value,
                                    maintenance_margin, status, user_id, account_id)
SELECT 0.4000, NOW(), 50000.0000, 20000.0000, 25000.0000, 'ACTIVE', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM margin_accounts WHERE user_id = 1);

INSERT INTO margin_accounts (bank_participation, created_at, initial_margin, loan_value,
                                    maintenance_margin, status, user_id, account_id)
SELECT 0.5000, NOW(), 25000.0000, 12500.0000, 12500.0000, 'BLOCKED', 2, 4
WHERE NOT EXISTS (SELECT 1 FROM margin_accounts WHERE user_id = 2);

INSERT INTO margin_accounts (bank_participation, created_at, initial_margin, loan_value,
                                    maintenance_margin, status, user_id, account_id)
SELECT 0.3000, NOW(), 40000.0000, 12000.0000, 20000.0000, 'ACTIVE', 3, 7
WHERE NOT EXISTS (SELECT 1 FROM margin_accounts WHERE user_id = 3);

INSERT INTO margin_accounts (bank_participation, created_at, initial_margin, loan_value,
                                    maintenance_margin, status, user_id, account_id)
SELECT 0.4500, NOW(), 30000.0000, 13500.0000, 15000.0000, 'ACTIVE', 4, 10
WHERE NOT EXISTS (SELECT 1 FROM margin_accounts WHERE user_id = 4);

-- Margin transactions: only insert if account has no transactions yet
INSERT INTO margin_transactions (amount, created_at, description, type, margin_account_id)
SELECT 50000.00, (NOW() - INTERVAL '3 days'), 'Inicijalna uplata', 'DEPOSIT', ma.id
FROM margin_accounts ma WHERE ma.user_id = 1
AND NOT EXISTS (SELECT 1 FROM margin_transactions mt WHERE mt.margin_account_id = ma.id)
LIMIT 1;

INSERT INTO margin_transactions (amount, created_at, description, type, margin_account_id)
SELECT 20000.00, (NOW() - INTERVAL '1 days'), 'Isplata', 'WITHDRAWAL', ma.id
FROM margin_accounts ma WHERE ma.user_id = 1
AND (SELECT COUNT(*) FROM margin_transactions mt WHERE mt.margin_account_id = ma.id) < 2
LIMIT 1;

-- Margin transactions for Lazar's margin account
INSERT INTO margin_transactions (amount, created_at, description, type, margin_account_id)
SELECT 40000.00, (NOW() - INTERVAL '5 days'), 'Inicijalna uplata', 'DEPOSIT', ma.id
FROM margin_accounts ma WHERE ma.user_id = 3
AND NOT EXISTS (SELECT 1 FROM margin_transactions mt WHERE mt.margin_account_id = ma.id)
LIMIT 1;

INSERT INTO margin_transactions (amount, created_at, description, type, margin_account_id)
SELECT 10000.00, (NOW() - INTERVAL '2 days'), 'Delimicna isplata', 'WITHDRAWAL', ma.id
FROM margin_accounts ma WHERE ma.user_id = 3
AND (SELECT COUNT(*) FROM margin_transactions mt WHERE mt.margin_account_id = ma.id) < 2
LIMIT 1;

-- Margin transactions for Ana's margin account
INSERT INTO margin_transactions (amount, created_at, description, type, margin_account_id)
SELECT 30000.00, (NOW() - INTERVAL '4 days'), 'Inicijalna uplata', 'DEPOSIT', ma.id
FROM margin_accounts ma WHERE ma.user_id = 4
AND NOT EXISTS (SELECT 1 FROM margin_transactions mt WHERE mt.margin_account_id = ma.id)
LIMIT 1;

-- ============================================================
-- TAX RECORDS
-- ============================================================

-- Obrisi stare tax zapise pre ponovnog ubacivanja (sprecava duplikate pri re-seed)
DELETE FROM tax_records;

-- client_id: 1=Stefan, 2=Milica, 3=Lazar, 4=Ana
-- employee_id: 1=Marko, 2=Jelena, 3=Nikola, 4=Tamara, 5=Djordje, 6=Maja
INSERT INTO tax_records (calculated_at, currency, tax_owed, tax_paid, total_profit,
                                user_id, user_name, user_type)
VALUES
    (NOW(), 'RSD', 1500.0000, 750.0000, 10000.0000, 1, 'Stefan Jovanovic', 'CLIENT'),
    (NOW(), 'RSD', 2250.0000, 2250.0000, 15000.0000, 2, 'Milica Nikolic', 'CLIENT'),
    (NOW(), 'RSD', 450.0000, 0.0000, 3000.0000, 4, 'Tamara Pavlovic', 'EMPLOYEE'),
    (NOW(), 'RSD', 1050.0000, 1050.0000, 7000.0000, 5, 'Djordje Jankovic', 'EMPLOYEE'),
    (NOW(), 'RSD', 800.0000, 0.0000, 5300.0000, 6, 'Maja Ristic', 'EMPLOYEE'),
    (NOW(), 'RSD', 300.0000, 300.0000, 2000.0000, 3, 'Nikola Milenkovic', 'EMPLOYEE'),
    (NOW(), 'RSD', 600.0000, 600.0000, 4000.0000, 1, 'Marko Petrovic', 'EMPLOYEE'),
    (NOW(), 'RSD', 375.0000, 0.0000, 2500.0000, 2, 'Jelena Djordjevic', 'EMPLOYEE'),
    (NOW(), 'RSD', 900.0000, 450.0000, 6000.0000, 3, 'Lazar Ilic', 'CLIENT'),
    (NOW(), 'RSD', 150.0000, 0.0000, 1000.0000, 4, 'Ana Stojanovic', 'CLIENT');

-- ============================================================
-- PORTFOLIOS ZA ZAPOSLENE (aktuare) — za E2E scenario testiranje
-- ============================================================
-- ---------- TAMARA (agent, employee_id=4) ----------
-- MSFT 15 @ 395, TSLA 10 @ 248
INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 4, 'EMPLOYEE', l.id, l.ticker, l.name, 'STOCK', 15, 395.0000, 0, NOW()
FROM listings l WHERE l.ticker = 'MSFT'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 4 AND p.user_role = 'EMPLOYEE' AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 4, 'EMPLOYEE', l.id, l.ticker, l.name, 'STOCK', 10, 248.0000, 0, NOW()
FROM listings l WHERE l.ticker = 'TSLA'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 4 AND p.user_role = 'EMPLOYEE' AND p.listing_id = l.id);

-- ---------- DJORDJE (agent, employee_id=5) ----------
-- CLM26 futures 5, AMZN 20 @ 178.5 (public 4 — bankin OTC izlog)
INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 5, 'EMPLOYEE', l.id, l.ticker, l.name, 'FUTURES', 5, 65.2000, 0, NOW()
FROM listings l WHERE l.ticker = 'CLM26'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 5 AND p.user_role = 'EMPLOYEE' AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 5, 'EMPLOYEE', l.id, l.ticker, l.name, 'STOCK', 20, 178.5000, 4, NOW()
FROM listings l WHERE l.ticker = 'AMZN'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 5 AND p.user_role = 'EMPLOYEE' AND p.listing_id = l.id);

-- ---------- MAJA (agent, employee_id=6) ----------
-- AAPL 20 @ 180, MSFT 10 @ 390, NVDA 8 @ 820 (public 2 — NVDA OTC od banke)
INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 6, 'EMPLOYEE', l.id, l.ticker, l.name, 'STOCK', 20, 180.0000, 0, NOW()
FROM listings l WHERE l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 6 AND p.user_role = 'EMPLOYEE' AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 6, 'EMPLOYEE', l.id, l.ticker, l.name, 'STOCK', 10, 390.0000, 0, NOW()
FROM listings l WHERE l.ticker = 'MSFT'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 6 AND p.user_role = 'EMPLOYEE' AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 6, 'EMPLOYEE', l.id, l.ticker, l.name, 'STOCK', 8, 820.0000, 2, NOW()
FROM listings l WHERE l.ticker = 'NVDA'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 6 AND p.user_role = 'EMPLOYEE' AND p.listing_id = l.id);

-- ============================================================
-- ORDERS ZA ZAPOSLENE (aktuare) — razliciti statusi za testiranje
-- ============================================================

-- Nikola (supervisor, emp_id=3) — BUY AAPL DONE
INSERT INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, 'No need for approval', 4462.5000, 1, (NOW() - INTERVAL '10 days'),
     'BUY', 1, (NOW() - INTERVAL '10 days'), NULL, 0, 'MARKET', 178.5000,
     25, 0, 'DONE', NULL, 3, 'EMPLOYEE', 1);

-- Nikola — BUY GOOG DONE
INSERT INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, 'No need for approval', 6480.0000, 1, (NOW() - INTERVAL '8 days'),
     'BUY', 1, (NOW() - INTERVAL '8 days'), NULL, 0, 'MARKET', 162.0000,
     40, 0, 'DONE', NULL, 3, 'EMPLOYEE', 3);

-- Maja (agent, emp_id=6) — BUY AAPL DONE
INSERT INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, 'Nikola Milenkovic', 3600.0000, 1, (NOW() - INTERVAL '6 days'),
     'BUY', 1, (NOW() - INTERVAL '6 days'), NULL, 0, 'MARKET', 180.0000,
     20, 0, 'DONE', NULL, 6, 'EMPLOYEE', 1);

-- Maja — BUY MSFT DONE
INSERT INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, 'Nikola Milenkovic', 3900.0000, 1, (NOW() - INTERVAL '5 days'),
     'BUY', 1, (NOW() - INTERVAL '5 days'), NULL, 0, 'MARKET', 390.0000,
     10, 0, 'DONE', NULL, 6, 'EMPLOYEE', 2);

-- Maja — BUY NVDA DONE
INSERT INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, 'Nikola Milenkovic', 6560.0000, 1, (NOW() - INTERVAL '4 days'),
     'BUY', 1, (NOW() - INTERVAL '4 days'), NULL, 0, 'MARKET', 820.0000,
     8, 0, 'DONE', NULL, 6, 'EMPLOYEE', 5);

-- Maja — LIMIT BUY GOOG PENDING (treba odobrenje jer needApproval=1)
INSERT INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, NULL, 3400.0000, 1, NOW(),
     'BUY', 0, NOW(), 170.0000, 0, 'LIMIT', 170.0000,
     20, 20, 'PENDING', NULL, 6, 'EMPLOYEE', 3);

-- Maja — SELL AAPL APPROVED (u toku izvrsavanja)
INSERT INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, 'Nikola Milenkovic', 950.0000, 1, (NOW() - INTERVAL '1 days'),
     'SELL', 0, NOW(), NULL, 0, 'MARKET', 190.0000,
     5, 3, 'APPROVED', NULL, 6, 'EMPLOYEE', 1);

-- Tamara (agent, emp_id=4) — BUY MSFT DONE
INSERT INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, 'Nikola Milenkovic', 5925.0000, 1, (NOW() - INTERVAL '9 days'),
     'BUY', 1, (NOW() - INTERVAL '9 days'), NULL, 0, 'MARKET', 395.0000,
     15, 0, 'DONE', NULL, 4, 'EMPLOYEE', 2);

-- Djordje (agent, emp_id=5) — BUY AMZN DONE
INSERT INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, 'Nikola Milenkovic', 3570.0000, 1, (NOW() - INTERVAL '7 days'),
     'BUY', 1, (NOW() - INTERVAL '7 days'), NULL, 0, 'MARKET', 178.5000,
     20, 0, 'DONE', NULL, 5, 'EMPLOYEE', 6);

-- Marko (admin/supervisor, emp_id=1) — BUY AAPL, MSFT, AMZN DONE
INSERT INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, 'No need for approval', 5280.0000, 1, (NOW() - INTERVAL '14 days'),
     'BUY', 1, (NOW() - INTERVAL '14 days'), NULL, 0, 'MARKET', 176.0000,
     30, 0, 'DONE', NULL, 1, 'EMPLOYEE', 1),
    (NULL, 0, 0, 'No need for approval', 7700.0000, 1, (NOW() - INTERVAL '12 days'),
     'BUY', 1, (NOW() - INTERVAL '12 days'), NULL, 0, 'MARKET', 385.0000,
     20, 0, 'DONE', NULL, 1, 'EMPLOYEE', 2),
    (NULL, 0, 0, 'No need for approval', 2100.0000, 1, (NOW() - INTERVAL '11 days'),
     'BUY', 1, (NOW() - INTERVAL '11 days'), NULL, 0, 'MARKET', 175.0000,
     12, 0, 'DONE', NULL, 1, 'EMPLOYEE', 6);

-- Jelena (admin/supervisor, emp_id=2) — BUY GOOG, NVDA DONE
INSERT INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, 'No need for approval', 5530.0000, 1, (NOW() - INTERVAL '13 days'),
     'BUY', 1, (NOW() - INTERVAL '13 days'), NULL, 0, 'MARKET', 158.0000,
     35, 0, 'DONE', NULL, 2, 'EMPLOYEE', 3),
    (NULL, 0, 0, 'No need for approval', 4800.0000, 1, (NOW() - INTERVAL '10 days'),
     'BUY', 1, (NOW() - INTERVAL '10 days'), NULL, 0, 'MARKET', 800.0000,
     6, 0, 'DONE', NULL, 2, 'EMPLOYEE', 5);

-- Ana (klijent) — SELL AAPL PENDING (dodatno u odnosu na glavni blok)
INSERT INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (11, 0, 1, NULL, 1900.0000, 1, NOW(),
     'SELL', 0, NOW(), NULL, 0, 'MARKET', 190.0000,
     3, 3, 'PENDING', NULL, 4, 'CLIENT', 1);

-- ============================================================
-- PHASE 0: Fund reservation backfill + bank trading racuni
-- ============================================================

-- Backfill availableBalance za postojece racune (ako je 0 ili NULL, postavi = balance)
UPDATE accounts SET available_balance = balance WHERE available_balance IS NULL OR available_balance = 0;

-- Osiguraj da je reservedAmount 0 za sve postojece racune
UPDATE accounts SET reserved_amount = 0 WHERE reserved_amount IS NULL;

-- Postavi default accountCategory = 'CLIENT' za sve postojece racune.
-- Napomena: Hibernate kreira MySQL ENUM kolonu sa abecedno sortiranim vrednostima,
-- pa je prva vrednost 'BANK_TRADING' i postaje implicitni default kad seed inserti
-- ne navedu account_category. Zato ovde forsirano postavljamo CLIENT svuda,
-- pa onda oznacavamo bankine racune kao BANK_TRADING.
UPDATE accounts SET account_category = 'CLIENT';

-- Postojeci bankini racuni (company_id=3, Banka 2025 Tim 2) se markiraju kao BANK_TRADING.
-- Izuzetak: poreski racun drzave (company_id=4) ostaje CLIENT.
UPDATE accounts SET account_category = 'BANK_TRADING' WHERE company_id = 3;

-- Namerno ne dodajemo dodatne BANK_TRADING racune za RSD/USD/EUR — postojeci
-- (222000100000000110/140/120) vec pokrivaju te valute i oznaceni su kao
-- BANK_TRADING gornjim UPDATE-om. Duplikati su obarali findBankAccountByCurrency
-- (Optional<Account> koji pada na 2 reda) pa ih ovde ne seed-ujemo.
-- Punjenje saldo bankinih racuna da FX transferi imaju kome da duguju:
UPDATE accounts SET balance = 500000000.0000, available_balance = 500000000.0000,
    daily_limit = 999999999.0000, monthly_limit = 999999999.0000
WHERE account_number = '222000100000000110';  -- RSD
UPDATE accounts SET balance = 5000000.0000, available_balance = 5000000.0000,
    daily_limit = 999999999.0000, monthly_limit = 999999999.0000
WHERE account_number = '222000100000000140';  -- USD
UPDATE accounts SET balance = 5000000.0000, available_balance = 5000000.0000,
    daily_limit = 999999999.0000, monthly_limit = 999999999.0000
WHERE account_number = '222000100000000120';  -- EUR

-- ============================================================
-- OTC (Celina 4) — aktivne ponude + sklopljeni ugovori
-- ============================================================
-- Stefan (client 1) vidi:
--   DISCOVERY (drugi nude javno): Milica GOOG 30, Milica AMZN 5, Lazar TSLA 8,
--     Ana AAPL 5, Ana NVDA 3, Djordje AMZN 4, Maja NVDA 2, Stefan sam nudi MSFT 10.
--   AKTIVNE PONUDE (kao kupac): prema Milici za GOOG (ceka se Milica).
--   AKTIVNE PONUDE (kao prodavac): Ana zeli Stefanov MSFT (ceka se Stefan).
--   CONTRACTS: Stefan kupac GOOG call od Milice (active), Stefan kupac TSLA call od Lazara (active),
--     Stefan kupac AAPL call od Djordja (exercised istorija), Stefan prodavac MSFT
--     call Ani (active) — mesavina uloga.
-- Svi iznosi u USD (valuta listinga).
--
-- listing_id: 1=AAPL, 2=MSFT, 3=GOOG, 4=TSLA, 5=AMZN, 13=NVDA
-- ============================================================

-- ---------- AKTIVNE PONUDE ----------
INSERT INTO otc_offers
    (buyer_id, buyer_role, seller_id, seller_role, listing_id, quantity,
     price_per_stock, premium, settlement_date,
     last_modified_by_id, last_modified_by_name, waiting_on_user_id, status,
     created_at, last_modified_at)
VALUES
    -- 1) Stefan hoce 10 GOOG od Milice po $172; waiting on Milica
    (1, 'CLIENT', 2, 'CLIENT', 3, 10, 172.0000, 45.0000,
     (CURRENT_DATE + INTERVAL '30 days'),
     1, 'Stefan Jovanović', 2, 'ACTIVE',
     (NOW() - INTERVAL '6 hours'), (NOW() - INTERVAL '6 hours')),

    -- 2) Ana hoce 5 MSFT od Stefana po $395; waiting on Stefan (Stefan treba da reaguje)
    (4, 'CLIENT', 1, 'CLIENT', 2, 5, 395.0000, 28.0000,
     (CURRENT_DATE + INTERVAL '21 days'),
     4, 'Ana Stojanović', 1, 'ACTIVE',
     (NOW() - INTERVAL '2 hours'), (NOW() - INTERVAL '2 hours')),

    -- 3) Lazar hoce 4 AAPL od Ane; waiting on Ana
    (3, 'CLIENT', 4, 'CLIENT', 1, 4, 195.0000, 22.0000,
     (CURRENT_DATE + INTERVAL '45 days'),
     3, 'Lazar Ilić', 4, 'ACTIVE',
     (NOW() - INTERVAL '1 days'), (NOW() - INTERVAL '1 days')),

    -- 4) Djordje (bank agent 5) nudi Stefanu AMZN pakovanje; Djordje kreirao, waiting on Stefan
    (1, 'CLIENT', 5, 'EMPLOYEE', 5, 3, 180.0000, 15.0000,
     (CURRENT_DATE + INTERVAL '60 days'),
     5, 'Đorđe Janković', 1, 'ACTIVE',
     (NOW() - INTERVAL '4 hours'), (NOW() - INTERVAL '30 minutes')),

    -- 5) Milica hoce 2 NVDA od Maje (banka); waiting on Maja
    (2, 'CLIENT', 6, 'EMPLOYEE', 13, 2, 830.0000, 40.0000,
     (CURRENT_DATE + INTERVAL '15 days'),
     2, 'Milica Nikolić', 6, 'ACTIVE',
     (NOW() - INTERVAL '8 hours'), (NOW() - INTERVAL '8 hours'));

-- ---------- SKLOPLJENI UGOVORI ----------
-- NOTE: redosled premium / settlement_date pazljivo da stoje datumi koji nisu prosli.
-- buyer stefan ima vise aktivnih, + jedan exercised + jedan expired (istorija).
INSERT INTO otc_contracts
    (source_offer_id, buyer_id, buyer_role, seller_id, seller_role, listing_id,
     quantity, strike_price, premium, settlement_date, status,
     created_at, exercised_at)
VALUES
    -- A) Stefan (kupac) drzi ACTIVE call na 5 GOOG od Milice, strike $170
    (1001, 1, 'CLIENT', 2, 'CLIENT', 3, 5, 170.0000, 22.0000,
     (CURRENT_DATE + INTERVAL '25 days'), 'ACTIVE',
     (NOW() - INTERVAL '3 days'), NULL),

    -- B) Stefan (kupac) drzi ACTIVE call na 3 TSLA od Lazara, strike $260
    (1002, 1, 'CLIENT', 3, 'CLIENT', 4, 3, 260.0000, 18.0000,
     (CURRENT_DATE + INTERVAL '18 days'), 'ACTIVE',
     (NOW() - INTERVAL '5 days'), NULL),

    -- C) Stefan (kupac) drzi ACTIVE call na 1 NVDA od Djordja (banka), strike $820
    (1003, 1, 'CLIENT', 5, 'EMPLOYEE', 13, 1, 820.0000, 35.0000,
     (CURRENT_DATE + INTERVAL '40 days'), 'ACTIVE',
     (NOW() - INTERVAL '2 days'), NULL),

    -- D) Stefan (prodavac) ima ACTIVE call na 3 MSFT prema Ani, strike $400
    (1004, 4, 'CLIENT', 1, 'CLIENT', 2, 3, 400.0000, 20.0000,
     (CURRENT_DATE + INTERVAL '35 days'), 'ACTIVE',
     (NOW() - INTERVAL '4 days'), NULL),

    -- E) Istorijski EXERCISED: Stefan je pre 15 dana iskoristio call na AAPL od Maje
    (1005, 1, 'CLIENT', 6, 'EMPLOYEE', 1, 2, 180.0000, 8.0000,
     (CURRENT_DATE - INTERVAL '3 days'), 'EXERCISED',
     (NOW() - INTERVAL '15 days'), (NOW() - INTERVAL '3 days')),

    -- F) Istorijski EXPIRED: Lazar je imao call na NVDA od Ane, istekao pre 5 dana
    (1006, 3, 'CLIENT', 4, 'CLIENT', 13, 1, 825.0000, 30.0000,
     (CURRENT_DATE - INTERVAL '5 days'), 'EXPIRED',
     (NOW() - INTERVAL '35 days'), NULL);

-- ============================================================
-- T12 — BANKA KAO KLIJENT FONDA + INVESTMENT FUND OWNER SEED
-- ============================================================
-- Spec ref:
--   Celina 4 (Nova) §4406-4435 (Napomena 1+2 — banka investira preko
--                                "vlasnik banke" klijenta)
--   Celina 5 (Nova) — OTC inter-bank entiteti su odvojen sloj (vidi
--                     interbank_otc_negotiations / interbank_otc_contracts)
--
-- Cilj:
--   Da Profit Banke portala "Pozicije u fondovima" tab prikazuje prave
--   podatke (ne prazan list). Banka se tretira kao obican klijent
--   (userRole='CLIENT') sa svojim client_id-jem; svaka pozicija banke u
--   fondu se evidentira u client_fund_positions sa userId = bankov client_id.
--
-- Idempotentnost:
--   Svi INSERT-i koriste WHERE NOT EXISTS pa se mogu ponavljati pri
--   re-seedu (Docker rebuild --no-cache + spring boot start).
--
-- IMPORTANT: ovaj blok mora ici NAKON sto Hibernate kreira:
--   - clients          (vec postoji u prethodnom seedu)
--   - investment_funds, client_fund_positions (Hibernate ddl-auto=update)
-- ============================================================

-- 1) Banka kao klijent (vlasnik banke) — Celina 4 (Nova) Napomena 1
INSERT INTO clients (first_name, last_name, date_of_birth, gender, email, phone, address,
                     password, salt_password, active, created_at)
SELECT 'Banka 2', 'd.o.o.', '2025-01-01', 'OTHER', 'banka2.doo@banka.rs',
       '+381 11 000 0000', 'Bulevar Kralja Aleksandra 73, Beograd',
       '$2b$10$FUjcSzK7CZKeX53YVU4JjeOIXLt5axbipO85OlQqw5Dopg47zfgRG',
       'c2VlZF9iYW5rYV9kb29f', 1, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM clients WHERE email = 'banka2.doo@banka.rs'
);

-- 2) Sample investment_funds — minimalan demo set tako da listBankPositions
--    ima sta da vrati. Polja su:
--      name, description, minimum_contribution, manager_employee_id,
--      account_id, created_at, active, owner_client_id, inception_date
--
--    manager_employee_id  -> 1 (Marko Petrovic, admin/supervisor)
--    account_id           -> 222000100000000110 (RSD bankin BANK_TRADING racun)
--    owner_client_id      -> nas novi "Banka 2 d.o.o." klijent
--
--    Ako T7 (createFund) bude seed-ovao svoje fondove, ovi minimalni demo
--    fondovi nece praviti konflikt jer imaju jedinstvena imena.
INSERT INTO investment_funds (name, description, minimum_contribution, manager_employee_id,
                               account_id, created_at, active, owner_client_id, inception_date)
SELECT 'Banka 2 Stable Income', 'Konzervativni fond fokusiran na obveznice i blue-chip akcije.',
       1000.0000, 1,
       (SELECT id FROM accounts WHERE account_number = '222000100000000110'),
       NOW(), 1,
       (SELECT id FROM clients WHERE email = 'banka2.doo@banka.rs'),
       (CURRENT_DATE - INTERVAL '180 days')
WHERE EXISTS (SELECT 1 FROM accounts WHERE account_number = '222000100000000110')
  AND NOT EXISTS (SELECT 1 FROM investment_funds WHERE name = 'Banka 2 Stable Income');

INSERT INTO investment_funds (name, description, minimum_contribution, manager_employee_id,
                               account_id, created_at, active, owner_client_id, inception_date)
SELECT 'Banka 2 Tech Growth', 'Agresivan fond fokusiran na IT i tech sektor.',
       5000.0000, 1,
       (SELECT id FROM accounts WHERE account_number = '222000100000000110'),
       NOW(), 1,
       (SELECT id FROM clients WHERE email = 'banka2.doo@banka.rs'),
       (CURRENT_DATE - INTERVAL '90 days')
WHERE EXISTS (SELECT 1 FROM accounts WHERE account_number = '222000100000000110')
  AND NOT EXISTS (SELECT 1 FROM investment_funds WHERE name = 'Banka 2 Tech Growth');

-- 3) Backfill owner_client_id za SVE postojece fondove (po §4406-4435):
--    Ako fond nema postavljen vlasnika (npr. T7 jos nije pozvao
--    createFund sa owner_client_id parametrom), default-ujemo na "Banka 2
--    d.o.o." klijenta. Ovo cini listBankPositions deterministicki rad
--    cak i za fondove kreirane pre uvoda owner_client_id polja.
UPDATE investment_funds
SET owner_client_id = (SELECT id FROM clients WHERE email = 'banka2.doo@banka.rs')
WHERE owner_client_id IS NULL;

-- 4) Sample bankine pozicije u fondovima — Celina 4 (Nova) Napomena 2:
--    "ClientFundPosition za banku: Klijent je klijent koji je vlasnik banke."
--    userRole = 'CLIENT', userId = banka2.doo client_id
INSERT INTO client_fund_positions (fund_id, user_id, user_role, total_invested, last_modified_at)
SELECT f.id,
       (SELECT id FROM clients WHERE email = 'banka2.doo@banka.rs'),
       'CLIENT',
       250000.0000,
       NOW()
FROM investment_funds f
WHERE f.name = 'Banka 2 Stable Income'
  AND NOT EXISTS (
      SELECT 1 FROM client_fund_positions p
      WHERE p.fund_id = f.id
        AND p.user_id = (SELECT id FROM clients WHERE email = 'banka2.doo@banka.rs')
        AND p.user_role = 'CLIENT'
  );

INSERT INTO client_fund_positions (fund_id, user_id, user_role, total_invested, last_modified_at)
SELECT f.id,
       (SELECT id FROM clients WHERE email = 'banka2.doo@banka.rs'),
       'CLIENT',
       500000.0000,
       NOW()
FROM investment_funds f
WHERE f.name = 'Banka 2 Tech Growth'
  AND NOT EXISTS (
      SELECT 1 FROM client_fund_positions p
      WHERE p.fund_id = f.id
        AND p.user_id = (SELECT id FROM clients WHERE email = 'banka2.doo@banka.rs')
        AND p.user_role = 'CLIENT'
  );

-- 5) Hartije fondova (FUND portfolio entries) — Celina 4 spec:
--    "Svaki fond ima svoje investicije (hartije od vrednosti) i likvidnost".
--    InvestmentFundService.getFundDetails cita portfolios.findByUserIdAndUserRole(fund.id, 'FUND')
--    da bi vratio holdings tabelu na FE Detaljnom prikazu fonda.

-- "Banka 2 Stable Income" — konzervativan: blue-chip akcije
INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT f.id, 'FUND', l.id, l.ticker, l.name, 'STOCK', 100, 180.5000, 0, NOW()
FROM investment_funds f, listings l
WHERE f.name = 'Banka 2 Stable Income' AND l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = f.id AND p.user_role = 'FUND' AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT f.id, 'FUND', l.id, l.ticker, l.name, 'STOCK', 50, 370.0000, 0, NOW()
FROM investment_funds f, listings l
WHERE f.name = 'Banka 2 Stable Income' AND l.ticker = 'MSFT'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = f.id AND p.user_role = 'FUND' AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT f.id, 'FUND', l.id, l.ticker, l.name, 'STOCK', 40, 155.0000, 0, NOW()
FROM investment_funds f, listings l
WHERE f.name = 'Banka 2 Stable Income' AND l.ticker = 'GOOG'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = f.id AND p.user_role = 'FUND' AND p.listing_id = l.id);

-- "Banka 2 Tech Growth" — agresivan: tech-heavy
INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT f.id, 'FUND', l.id, l.ticker, l.name, 'STOCK', 200, 185.0000, 0, NOW()
FROM investment_funds f, listings l
WHERE f.name = 'Banka 2 Tech Growth' AND l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = f.id AND p.user_role = 'FUND' AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT f.id, 'FUND', l.id, l.ticker, l.name, 'STOCK', 50, 450.0000, 0, NOW()
FROM investment_funds f, listings l
WHERE f.name = 'Banka 2 Tech Growth' AND l.ticker = 'NVDA'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = f.id AND p.user_role = 'FUND' AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT f.id, 'FUND', l.id, l.ticker, l.name, 'STOCK', 25, 310.0000, 0, NOW()
FROM investment_funds f, listings l
WHERE f.name = 'Banka 2 Tech Growth' AND l.ticker = 'META'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = f.id AND p.user_role = 'FUND' AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT f.id, 'FUND', l.id, l.ticker, l.name, 'STOCK', 40, 200.0000, 0, NOW()
FROM investment_funds f, listings l
WHERE f.name = 'Banka 2 Tech Growth' AND l.ticker = 'TSLA'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = f.id AND p.user_role = 'FUND' AND p.listing_id = l.id);

-- 6) Istorijski snapshot-i vrednosti fondova — Celina 4 spec:
--    "Performanse fonda: tabela ili grafikon (mesecni/kvartalni/godisnji prikaz)"
--    "treba da pratite performanse fonda -> belezite istorijske podatke"
--
--    FundValueSnapshotScheduler pravi dnevne snapshot-e u 23:45, ali za novi
--    stack istorija je prazna pa graf "Performanse fonda" pokazuje 1 tacku.
--    Ovde backfill-ujemo dnevne snapshot-e od inception_date do juce sa
--    realisticnim fluktuacijama oko trenutne vrednosti (sine wave + random).
--    "Banka 2 Stable Income" (konzervativan): range ~490M-510M, fluktuacija ~0.5%
--    "Banka 2 Tech Growth" (agresivan): range ~470M-530M, fluktuacija ~2%
INSERT INTO fund_value_snapshots (fund_id, snapshot_date, fund_value, liquid_amount, invested_total, profit)
SELECT f.id, gs::date,
       (500000000 + 10000000 * sin((gs::date - f.inception_date)::float / 14)
                  + 2500000 * (random() - 0.5))::decimal(19,4),
       (500000000 + 10000000 * sin((gs::date - f.inception_date)::float / 14)
                  + 2500000 * (random() - 0.5))::decimal(19,4),
       250000.0000,
       (500000000 + 10000000 * sin((gs::date - f.inception_date)::float / 14)
                  + 2500000 * (random() - 0.5) - 250000)::decimal(19,4)
FROM investment_funds f,
     LATERAL generate_series(f.inception_date, CURRENT_DATE - INTERVAL '1 day', '1 day'::interval) AS gs
WHERE f.name = 'Banka 2 Stable Income'
  AND NOT EXISTS (SELECT 1 FROM fund_value_snapshots s WHERE s.fund_id = f.id AND s.snapshot_date = gs::date);

INSERT INTO fund_value_snapshots (fund_id, snapshot_date, fund_value, liquid_amount, invested_total, profit)
SELECT f.id, gs::date,
       (500000000 + 25000000 * sin((gs::date - f.inception_date)::float / 10)
                  + 10000000 * (random() - 0.5))::decimal(19,4),
       (500000000 + 25000000 * sin((gs::date - f.inception_date)::float / 10)
                  + 10000000 * (random() - 0.5))::decimal(19,4),
       500000.0000,
       (500000000 + 25000000 * sin((gs::date - f.inception_date)::float / 10)
                  + 10000000 * (random() - 0.5) - 500000)::decimal(19,4)
FROM investment_funds f,
     LATERAL generate_series(f.inception_date, CURRENT_DATE - INTERVAL '1 day', '1 day'::interval) AS gs
WHERE f.name = 'Banka 2 Tech Growth'
  AND NOT EXISTS (SELECT 1 FROM fund_value_snapshots s WHERE s.fund_id = f.id AND s.snapshot_date = gs::date);

-- ============================================================
-- INTER-BANK HANDSHAKE TEST SEED (Celina 5 — Tim 1 / Tim 2 razmena)
-- ============================================================
-- Dodatni racuni i klijent za inter-bank protokol handshake test
-- sa Tim 1. Dodato 11.05.2026 posle dogovora sa Aleksom (Tim 1 BE).
--
-- Sta omogucava:
-- 1) Stefan + Milica + Ana dobijaju USD racune — da mogu primati
--    premium u USD od inter-bank kupca u OTC accept flow-u
--    (OtcNegotiationService.resolveLocalAccount trazi seller-ov
--    racun u valuti premium-a; bez USD racuna kupac iz Banka 1
--    ne bi mogao da plati premium za AAPL/MSFT koji su u USD-u).
-- 2) Novi klijent C-7 "Mile Interbank" sa svim 4 valutama
--    (RSD/EUR/USD/CHF) — dedicated test target za Tim 1 handshake
--    skripte. Iznosi po valuti dovoljni za sve 15 scenarija.
-- 3) Stefan dodaje 8 javnih AAPL akcija (pored postojecih 10 MSFT)
--    da Tim 1 ima vise scenarija u GET /public-stock odgovoru
--    (multi-seller per ticker).
-- ============================================================

-- 1) Stefan USD racun (account_id ce biti auto-generisan, ne
--    konflikira sa explicit-ID-evima 1-22 jer sequence se resetuje)
INSERT INTO accounts (account_number, account_type, account_subtype, currency_id,
                      client_id, company_id, employee_id,
                      balance, available_balance,
                      daily_limit, monthly_limit,
                      daily_spending, monthly_spending,
                      maintenance_fee, expiration_date, status, name, created_at)
VALUES
    -- Stefan USD: 2500 USD pocetno stanje, za primanje premium-a od Tim 1 kupca
    ('222000131234567811', 'FOREIGN', 'STANDARD', 3, 1, NULL, 1,
     2500.0000, 2500.0000, 5000.0000, 50000.0000,
     0.0000, 0.0000, 0.0000, '2030-01-01', 'ACTIVE', 'Stefan USD', NOW()),
    -- Milica USD: 1800 USD pocetno, isto pravilo
    ('222000131234567812', 'FOREIGN', 'STANDARD', 3, 2, NULL, 1,
     1800.0000, 1800.0000, 5000.0000, 50000.0000,
     0.0000, 0.0000, 0.0000, '2030-01-01', 'ACTIVE', 'Milica USD', NOW()),
    -- Ana USD: 950 USD pocetno
    ('222000131234567813', 'FOREIGN', 'STANDARD', 3, 4, NULL, 1,
     950.0000, 950.0000, 5000.0000, 50000.0000,
     0.0000, 0.0000, 0.0000, '2030-01-01', 'ACTIVE', 'Ana USD', NOW());

-- 2) Mile Interbank — dedicated handshake test klijent (C-7)
INSERT INTO clients (first_name, last_name, date_of_birth, gender, email, phone, address,
                     password, salt_password, active, created_at)
SELECT 'Mile', 'Interbank', '1992-03-20', 'M', 'mile.interbank@banka.rs',
       '+381 65 777 8899', 'Inter-Bank Test 1, Beograd',
       '$2b$10$FUjcSzK7CZKeX53YVU4JjeOIXLt5axbipO85OlQqw5Dopg47zfgRG',
       'c2VlZF9pbnRlcmJhbmtf', 1, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM clients WHERE email = 'mile.interbank@banka.rs'
);

-- 3) Mile Interbank racuni — sve 4 osnovne valute, visoki balance-ovi
--    Tim 1 koristi `222000177777777XXX` prefix za njega da bude
--    lako prepoznatljiv u test scriptama.
INSERT INTO accounts (account_number, account_type, account_subtype, currency_id,
                      client_id, company_id, employee_id,
                      balance, available_balance,
                      daily_limit, monthly_limit,
                      daily_spending, monthly_spending,
                      maintenance_fee, expiration_date, status, name, created_at)
SELECT acc.account_number, acc.account_type, acc.account_subtype, acc.currency_id,
       c.id, NULL, 1,
       acc.balance, acc.balance,
       acc.daily_limit, acc.monthly_limit,
       0.0000, 0.0000, 0.0000, '2030-01-01', 'ACTIVE', acc.name, NOW()
FROM clients c
CROSS JOIN (VALUES
    ('222000177777777811', 'CHECKING', 'STANDARD', 8, 5000000.0000, 1000000.0000, 10000000.0000, 'Mile Interbank RSD'),
    ('222000177777777821', 'FOREIGN',  'STANDARD', 1,   50000.0000,   10000.0000,   100000.0000, 'Mile Interbank EUR'),
    ('222000177777777831', 'FOREIGN',  'STANDARD', 3,   50000.0000,   10000.0000,   100000.0000, 'Mile Interbank USD'),
    ('222000177777777841', 'FOREIGN',  'STANDARD', 2,   50000.0000,   10000.0000,   100000.0000, 'Mile Interbank CHF')
) AS acc(account_number, account_type, account_subtype, currency_id, balance, daily_limit, monthly_limit, name)
WHERE c.email = 'mile.interbank@banka.rs'
  AND NOT EXISTS (
      SELECT 1 FROM accounts WHERE account_number = acc.account_number
  );

-- 4) Mile Interbank javna portfolio pozicija — AAPL 100 sa public 25.
--    Tim 1 moze da inicira /negotiations gde je Mile seller, sto je
--    cleaner test od koriscenja Stefan (koji je takodje FE test klijent
--    sa svojim postojecim ugovorima).
INSERT INTO portfolios (user_id, user_role, listing_id, listing_ticker, listing_name, listing_type,
                        quantity, average_buy_price, public_quantity, last_modified)
SELECT c.id, 'CLIENT', l.id, l.ticker, l.name, 'STOCK', 100, 175.0000, 25, NOW()
FROM clients c, listings l
WHERE c.email = 'mile.interbank@banka.rs'
  AND l.ticker = 'AAPL'
  AND NOT EXISTS (
      SELECT 1 FROM portfolios p
      WHERE p.user_id = c.id AND p.user_role = 'CLIENT' AND p.listing_id = l.id
  );

-- 5) Stefan dobija AAPL javne — pored MSFT 10 public, sad i AAPL 8 public.
--    Daje Tim 1 multi-ticker, multi-seller test scenarije (GET /public-stock
--    sad vraca AAPL od dva sellera: C-1 Stefan + C-4 Ana + C-7 Mile).
UPDATE portfolios SET public_quantity = 8, last_modified = NOW()
WHERE user_id = 1 AND user_role = 'CLIENT' AND listing_ticker = 'AAPL';

-- ============================================================
-- SEQUENCE RESET — posle eksplicitnih ID INSERT-ova, sequence
-- mora da se sinhronizuje sa MAX(id) iz svake tabele, inace
-- sledeci Hibernate auto-generated INSERT pokusava ID koji
-- vec postoji (companies/currencies imaju eksplicitne ID-eve).
-- Bag prijavljen 10.05.2026 vece-2 (C2 Sc 4).
-- ============================================================

SELECT setval('companies_id_seq', (SELECT COALESCE(MAX(id), 1) FROM companies));
SELECT setval('currencies_id_seq', (SELECT COALESCE(MAX(id), 1) FROM currencies));

-- ============================================================
-- STEDNJA (Celina 2 nadogradnja) — kamatne stope + demo deposits
-- ============================================================

-- 8 valuta x 5 rokova = 40 stopa (RSD/EUR/USD/CHF/GBP/CAD/AUD/JPY x 3/6/12/24/36 meseci)
-- RSD: 2.5-5.0%, EUR/USD: 1.5-4.0%, CHF: 1.0-2.0%, GBP: 1.75-3.25%,
-- CAD: 1.5-3.0%, AUD: 1.75-3.25%, JPY: 0.5-2.5%

INSERT INTO savings_interest_rates (currency_id, term_months, annual_rate, active, effective_from, created_at) VALUES
  ((SELECT id FROM currencies WHERE code='RSD'),  3, 2.50, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='RSD'),  6, 3.00, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='RSD'), 12, 4.00, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='RSD'), 24, 4.50, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='RSD'), 36, 5.00, 1, '2026-01-01', NOW()),

  ((SELECT id FROM currencies WHERE code='EUR'),  3, 1.50, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='EUR'),  6, 2.00, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='EUR'), 12, 2.50, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='EUR'), 24, 3.00, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='EUR'), 36, 3.50, 1, '2026-01-01', NOW()),

  ((SELECT id FROM currencies WHERE code='USD'),  3, 2.00, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='USD'),  6, 2.50, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='USD'), 12, 3.00, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='USD'), 24, 3.50, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='USD'), 36, 4.00, 1, '2026-01-01', NOW()),

  ((SELECT id FROM currencies WHERE code='CHF'),  3, 1.00, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='CHF'),  6, 1.25, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='CHF'), 12, 1.50, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='CHF'), 24, 1.75, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='CHF'), 36, 2.00, 1, '2026-01-01', NOW()),

  ((SELECT id FROM currencies WHERE code='GBP'),  3, 1.75, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='GBP'),  6, 2.00, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='GBP'), 12, 2.50, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='GBP'), 24, 3.00, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='GBP'), 36, 3.25, 1, '2026-01-01', NOW()),

  ((SELECT id FROM currencies WHERE code='CAD'),  3, 1.50, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='CAD'),  6, 1.75, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='CAD'), 12, 2.25, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='CAD'), 24, 2.75, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='CAD'), 36, 3.00, 1, '2026-01-01', NOW()),

  ((SELECT id FROM currencies WHERE code='AUD'),  3, 1.75, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='AUD'),  6, 2.00, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='AUD'), 12, 2.50, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='AUD'), 24, 3.00, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='AUD'), 36, 3.25, 1, '2026-01-01', NOW()),

  ((SELECT id FROM currencies WHERE code='JPY'),  3, 0.50, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='JPY'),  6, 1.00, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='JPY'), 12, 1.50, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='JPY'), 24, 2.00, 1, '2026-01-01', NOW()),
  ((SELECT id FROM currencies WHERE code='JPY'), 36, 2.50, 1, '2026-01-01', NOW());

SELECT setval('savings_interest_rates_id_seq', (SELECT COALESCE(MAX(id), 1) FROM savings_interest_rates));

-- Demo deposit-i za seedovane klijente:
-- Stefan (client_id=1): 200,000 RSD na 12 meseci, autoRenew=true, otvoren 2 meseca pre, vec 2 isplate kamate
--   linked_account: prvi aktivni RSD racun Stefana (account_number=222000112345678911)
-- Milica (client_id=2): 1,000 EUR na 6 meseci, autoRenew=false, otvoren 1 mesec pre, 1 isplata kamate
--   linked_account: prvi aktivni EUR racun Milice (account_number=222000121345678923)
-- Koristimo subquery umesto hard-coded account.id jer accounts tabela nema eksplicitne ID-eve u seed-u.

INSERT INTO savings_deposits (
    client_id, linked_account_id, principal_amount, currency_id, term_months,
    annual_interest_rate, start_date, maturity_date, next_interest_payment_date,
    total_interest_paid, auto_renew, status, version, created_at, updated_at
) VALUES
  (1,
   (SELECT id FROM accounts WHERE client_id=1 AND currency_id=(SELECT id FROM currencies WHERE code='RSD') AND status='ACTIVE' ORDER BY id LIMIT 1),
   200000.0000, (SELECT id FROM currencies WHERE code='RSD'), 12,
   4.00, '2026-03-12', '2027-03-12', '2026-06-12',
   1333.3333, 1, 'ACTIVE', 0, NOW(), NOW()),
  (2,
   (SELECT id FROM accounts WHERE client_id=2 AND currency_id=(SELECT id FROM currencies WHERE code='EUR') AND status='ACTIVE' ORDER BY id LIMIT 1),
   1000.0000, (SELECT id FROM currencies WHERE code='EUR'), 6,
   2.00, '2026-04-12', '2026-10-12', '2026-06-12',
   1.6667, 0, 'ACTIVE', 0, NOW(), NOW());

SELECT setval('savings_deposits_id_seq', (SELECT COALESCE(MAX(id), 1) FROM savings_deposits));

-- Istorija transakcija za demo deposit-e
-- deposit_id=1 = Stefanov RSD deposit, deposit_id=2 = Milicin EUR deposit
-- (Ovi ID-evi su sigurni jer su ovo prvi INSERT-i u savings_deposits tabelu)
INSERT INTO savings_transactions (deposit_id, type, amount, currency_id, processed_date, description, created_at) VALUES
  (1, 'OPEN', 200000.0000, (SELECT id FROM currencies WHERE code='RSD'),
   '2026-03-12', 'Otvaranje depozita rok=12m stopa=4.00% p.a.', '2026-03-12 09:00:00'),
  (1, 'INTEREST_PAYMENT', 666.6667, (SELECT id FROM currencies WHERE code='RSD'),
   '2026-04-12', 'Mesecna kamata depozita #1', '2026-04-12 02:00:00'),
  (1, 'INTEREST_PAYMENT', 666.6667, (SELECT id FROM currencies WHERE code='RSD'),
   '2026-05-12', 'Mesecna kamata depozita #1', '2026-05-12 02:00:00'),
  (2, 'OPEN', 1000.0000, (SELECT id FROM currencies WHERE code='EUR'),
   '2026-04-12', 'Otvaranje depozita rok=6m stopa=2.00% p.a.', '2026-04-12 10:30:00'),
  (2, 'INTEREST_PAYMENT', 1.6667, (SELECT id FROM currencies WHERE code='EUR'),
   '2026-05-12', 'Mesecna kamata depozita #2', '2026-05-12 02:00:00');

SELECT setval('savings_transactions_id_seq', (SELECT COALESCE(MAX(id), 1) FROM savings_transactions));
