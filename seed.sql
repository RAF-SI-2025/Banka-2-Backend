-- ============================================================
-- Banka 2025 — Seed Data
-- ============================================================
-- Lozinke za testiranje:
--   Admin korisnici (users tabela):    Admin12345
--   Obicni klijenti (users tabela):    Klijent12345
--   Zaposleni (employees tabela):      Zaposleni12
-- ============================================================

SET sql_mode='';

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
(8, 'RSD', 'Serbian Dinar', 'RSD', 'Serbia', 'Serbian Dinar – currency of Serbia', true)
ON DUPLICATE KEY UPDATE code = code;

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
                       majority_owner_id, active, is_state, created_at)
VALUES
    (1, 'TechStar DOO', '12345678', '123456789', '62.01',
     'Bulevar Mihajla Pupina 10, Novi Beograd',
     NULL, 1, 0, NOW()),
    (2, 'Green Food AD', '87654321', '987654321', '10.10',
     'Industrijska zona bb, Subotica',
     NULL, 1, 0, NOW())
    ON DUPLICATE KEY UPDATE name = name;

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

    ('222000112345678914', 'BUSINESS', 'STANDARD', 8, 2, 1, 2,
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
     0.0000, '2031-09-01', 'ACTIVE', 'Euro račun', NOW())

    ON DUPLICATE KEY UPDATE account_number = account_number;


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

-- Prvo kreiramo firmu za banku
INSERT INTO companies (id, name, registration_number, tax_number, activity_code, address,
                       majority_owner_id, active, is_state, created_at)
VALUES
    (3, 'Banka 2025 Tim 2', '22200022', '222000222', '64.19',
     'Bulevar Kralja Aleksandra 73, Beograd',
     NULL, 1, 0, NOW())
    ON DUPLICATE KEY UPDATE name = name;

-- Država (Republika Srbija) — poseban entitet za uplatu poreza
INSERT INTO companies (id, name, registration_number, tax_number, activity_code, address,
                       majority_owner_id, active, is_state, created_at)
VALUES
    (4, 'Republika Srbija', '17858459', '100002288', '84.11',
     'Nemanjina 11, Beograd',
     NULL, 1, 1, NOW())
    ON DUPLICATE KEY UPDATE name = name;

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
     0.0000, 0.0000, 0.0000, '2050-01-01', 'ACTIVE', 'Banka AUD', NOW())

    ON DUPLICATE KEY UPDATE account_number = account_number;

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
     0.0000, 0.0000, 0.0000, '2050-01-01', 'ACTIVE', 'Republika Srbija - poreski racun', NOW())
    ON DUPLICATE KEY UPDATE account_number = account_number;


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
     150000.0000, 'BLOCKED', '2026-01-01', '2030-01-01')

    ON DUPLICATE KEY UPDATE card_number = card_number;


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

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, base_currency, quote_currency, liquidity,
                      contract_size, contract_unit, settlement_date)
VALUES (  'AAPL', 'Apple Inc.', 'NASDAQ', 'STOCK',
  189.8400, 190.1200, 189.5600, 54230000, 2.3400, NOW(),
  15500000000, 0.0055, NULL, NULL, NULL, 1, NULL, NULL);

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'MSFT', 'Microsoft Corp.', 'NASDAQ', 'STOCK',
  415.2600, 415.8000, 414.7200, 22100000, -1.1800, NOW(),
  7430000000, 0.0072, 1);

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'GOOG', 'Alphabet Inc.', 'NASDAQ', 'STOCK',
  173.4500, 173.9000, 173.0000, 18500000, 0.8700, NOW(),
  12200000000, 0.0000, 1);

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'TSLA', 'Tesla Inc.', 'NASDAQ', 'STOCK',
  248.9100, 249.5000, 248.3200, 72300000, -5.4300, NOW(),
  3180000000, 0.0000, 1);

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'AMZN', 'Amazon.com Inc.', 'NASDAQ', 'STOCK',
  186.3200, 186.8000, 185.8400, 35600000, 1.5600, NOW(),
  10300000000, 0.0000, 1);

-- Forex parovi
INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      base_currency, quote_currency, liquidity, contract_size)
VALUES (  'EUR/USD', 'Euro / US Dollar', 'FOREX', 'FOREX',
  1.0856, 1.0858, 1.0854, 180000000, 0.0012, NOW(),
  'EUR', 'USD', 'HIGH', 1000);

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      base_currency, quote_currency, liquidity, contract_size)
VALUES (  'GBP/USD', 'British Pound / US Dollar', 'FOREX', 'FOREX',
  1.2943, 1.2946, 1.2940, 95000000, -0.0008, NOW(),
  'GBP', 'USD', 'HIGH', 1000);

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      base_currency, quote_currency, liquidity, contract_size)
VALUES (  'USD/JPY', 'US Dollar / Japanese Yen', 'FOREX', 'FOREX',
  149.2300, 149.2600, 149.2000, 120000000, 0.4500, NOW(),
  'USD', 'JPY', 'HIGH', 1000);

-- Futures
INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      contract_size, contract_unit, settlement_date)
VALUES (  'CLM26', 'Crude Oil June 2026', 'CME', 'FUTURES',
  68.4500, 68.5200, 68.3800, 312000, -0.8700, NOW(),
  1000, 'Barrel', '2026-06-20');

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      contract_size, contract_unit, settlement_date)
VALUES (  'GCQ26', 'Gold August 2026', 'CME', 'FUTURES',
  2345.8000, 2346.5000, 2345.1000, 185000, 12.4000, NOW(),
  100, 'Troy Ounce', '2026-08-27');

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      contract_size, contract_unit, settlement_date)
VALUES (  'SIH26', 'Silver March 2026', 'CME', 'FUTURES',
  27.3500, 27.3900, 27.3100, 64000, 0.1800, NOW(),
  5000, 'Troy Ounce', '2026-03-27');

-- Dodatni stocks (10 novih)
INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'META', 'Meta Platforms Inc.', 'NASDAQ', 'STOCK',
  500.1200, 500.6200, 499.6200, 18200000, 3.4500, NOW(),
  2560000000, 0.0000, 1);

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'NVDA', 'NVIDIA Corp.', 'NASDAQ', 'STOCK',
  880.3500, 881.2300, 879.4700, 41500000, 12.6000, NOW(),
  24600000000, 0.0004, 1);

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'JPM', 'JPMorgan Chase & Co.', 'NYSE', 'STOCK',
  195.4200, 195.6200, 195.2200, 9800000, 1.2300, NOW(),
  2870000000, 0.0245, 1);

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'V', 'Visa Inc.', 'NYSE', 'STOCK',
  280.7500, 281.0300, 280.4700, 7200000, 0.8500, NOW(),
  2050000000, 0.0076, 1);

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'JNJ', 'Johnson & Johnson', 'NYSE', 'STOCK',
  155.3200, 155.4800, 155.1600, 6500000, -0.4200, NOW(),
  2410000000, 0.0302, 1);

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'WMT', 'Walmart Inc.', 'NYSE', 'STOCK',
  60.2500, 60.3100, 60.1900, 8400000, 0.3200, NOW(),
  8050000000, 0.0135, 1);

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'DIS', 'The Walt Disney Co.', 'NYSE', 'STOCK',
  115.4800, 115.6000, 115.3600, 10200000, -1.1200, NOW(),
  1830000000, 0.0000, 1);

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'NFLX', 'Netflix Inc.', 'NASDAQ', 'STOCK',
  620.8000, 621.4200, 620.1800, 5300000, 4.5600, NOW(),
  430000000, 0.0000, 1);

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'BA', 'The Boeing Co.', 'NYSE', 'STOCK',
  180.2300, 180.4100, 180.0500, 12100000, -2.3400, NOW(),
  610000000, 0.0000, 1);

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
VALUES (  'INTC', 'Intel Corp.', 'NASDAQ', 'STOCK',
  30.1500, 30.1800, 30.1200, 28500000, -0.6500, NOW(),
  4180000000, 0.0133, 1);

-- Dodatni forex parovi (5 novih)
INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      base_currency, quote_currency, liquidity, contract_size)
VALUES (  'USD/CHF', 'US Dollar / Swiss Franc', 'FOREX', 'FOREX',
  0.8815, 0.8818, 0.8812, 72000000, -0.0023, NOW(),
  'USD', 'CHF', 'HIGH', 1000);

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      base_currency, quote_currency, liquidity, contract_size)
VALUES (  'AUD/USD', 'Australian Dollar / US Dollar', 'FOREX', 'FOREX',
  0.6524, 0.6527, 0.6521, 58000000, 0.0015, NOW(),
  'AUD', 'USD', 'MEDIUM', 1000);

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      base_currency, quote_currency, liquidity, contract_size)
VALUES (  'USD/CAD', 'US Dollar / Canadian Dollar', 'FOREX', 'FOREX',
  1.3612, 1.3615, 1.3609, 65000000, 0.0031, NOW(),
  'USD', 'CAD', 'HIGH', 1000);

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      base_currency, quote_currency, liquidity, contract_size)
VALUES (  'EUR/GBP', 'Euro / British Pound', 'FOREX', 'FOREX',
  0.8523, 0.8526, 0.8520, 42000000, -0.0009, NOW(),
  'EUR', 'GBP', 'HIGH', 1000);

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      base_currency, quote_currency, liquidity, contract_size)
VALUES (  'EUR/JPY', 'Euro / Japanese Yen', 'FOREX', 'FOREX',
  163.1200, 163.1800, 163.0600, 55000000, 0.5600, NOW(),
  'EUR', 'JPY', 'HIGH', 1000);

-- Dodatni futures (4 nova)
INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      contract_size, contract_unit, settlement_date)
VALUES (  'NGK26', 'Natural Gas May 2026', 'CME', 'FUTURES',
  2.1050, 2.1100, 2.1000, 145000, 0.0350, NOW(),
  10000, 'MMBtu', '2026-05-28');

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      contract_size, contract_unit, settlement_date)
VALUES (  'ZCN26', 'Corn July 2026', 'CME', 'FUTURES',
  450.2500, 450.7500, 449.7500, 98000, 3.5000, NOW(),
  5000, 'Bushel', '2026-07-14');

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      contract_size, contract_unit, settlement_date)
VALUES (  'ZWN26', 'Wheat July 2026', 'CME', 'FUTURES',
  580.5000, 581.0800, 579.9200, 72000, -4.2500, NOW(),
  5000, 'Bushel', '2026-07-14');

INSERT IGNORE INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      contract_size, contract_unit, settlement_date)
VALUES (  'HGK26', 'Copper May 2026', 'CME', 'FUTURES',
  4.2050, 4.2100, 4.2000, 54000, 0.0320, NOW(),
  25000, 'Pound', '2026-05-27');

-- ============================================================
-- LISTING DAILY PRICES (istorijski podaci za grafike)
-- ============================================================

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 185.20, 186.50, 184.10, -1.30, 48000000
FROM listings l WHERE l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 186.50, 188.20, 185.80, 1.30, 51000000
FROM listings l WHERE l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 187.10, 189.00, 186.40, 0.60, 53000000
FROM listings l WHERE l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 188.40, 190.10, 187.50, 1.30, 55000000
FROM listings l WHERE l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 187.50, 189.90, 186.80, -0.90, 52000000
FROM listings l WHERE l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, CURDATE(), 189.84, 190.50, 188.20, 2.34, 54230000
FROM listings l WHERE l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = CURDATE());

-- EUR/USD istorija
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 1.0830, 1.0865, 1.0810, -0.0015, 175000000
FROM listings l WHERE l.ticker = 'EUR/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 1.0844, 1.0870, 1.0825, 0.0014, 178000000
FROM listings l WHERE l.ticker = 'EUR/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 1.0848, 1.0880, 1.0830, 0.0004, 182000000
FROM listings l WHERE l.ticker = 'EUR/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, CURDATE(), 1.0856, 1.0890, 1.0840, 0.0012, 180000000
FROM listings l WHERE l.ticker = 'EUR/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = CURDATE());

-- MSFT istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 410.30, 412.50, 409.10, -2.40, 20500000
FROM listings l WHERE l.ticker = 'MSFT'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 412.80, 414.00, 410.50, 2.50, 21800000
FROM listings l WHERE l.ticker = 'MSFT'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 413.50, 415.20, 412.00, 0.70, 22300000
FROM listings l WHERE l.ticker = 'MSFT'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 414.90, 416.80, 413.60, 1.40, 21500000
FROM listings l WHERE l.ticker = 'MSFT'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 416.44, 417.20, 414.50, 1.54, 22100000
FROM listings l WHERE l.ticker = 'MSFT'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- GOOG istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 170.20, 171.80, 169.50, 1.10, 17200000
FROM listings l WHERE l.ticker = 'GOOG'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 171.50, 172.90, 170.80, 1.30, 18000000
FROM listings l WHERE l.ticker = 'GOOG'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 172.10, 173.50, 171.20, 0.60, 17800000
FROM listings l WHERE l.ticker = 'GOOG'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 172.80, 174.00, 171.90, 0.70, 18500000
FROM listings l WHERE l.ticker = 'GOOG'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 172.58, 174.20, 172.00, -0.22, 18500000
FROM listings l WHERE l.ticker = 'GOOG'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- AMZN istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 182.40, 183.80, 181.50, -1.20, 33000000
FROM listings l WHERE l.ticker = 'AMZN'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 183.50, 185.00, 182.80, 1.10, 34200000
FROM listings l WHERE l.ticker = 'AMZN'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 184.20, 185.50, 183.40, 0.70, 35000000
FROM listings l WHERE l.ticker = 'AMZN'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 185.10, 186.80, 184.30, 0.90, 35600000
FROM listings l WHERE l.ticker = 'AMZN'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 184.76, 186.20, 184.00, -0.34, 35600000
FROM listings l WHERE l.ticker = 'AMZN'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- TSLA istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 258.40, 262.00, 255.80, 6.20, 68000000
FROM listings l WHERE l.ticker = 'TSLA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 255.10, 259.50, 253.20, -3.30, 70500000
FROM listings l WHERE l.ticker = 'TSLA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 252.80, 256.00, 251.00, -2.30, 72000000
FROM listings l WHERE l.ticker = 'TSLA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 251.50, 254.80, 250.10, -1.30, 71000000
FROM listings l WHERE l.ticker = 'TSLA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 254.34, 256.20, 250.80, 2.84, 72300000
FROM listings l WHERE l.ticker = 'TSLA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- META istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 492.30, 495.00, 490.50, -2.80, 16500000
FROM listings l WHERE l.ticker = 'META'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 494.80, 497.50, 493.10, 2.50, 17200000
FROM listings l WHERE l.ticker = 'META'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 496.20, 498.80, 494.50, 1.40, 17800000
FROM listings l WHERE l.ticker = 'META'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 497.80, 500.50, 496.00, 1.60, 18200000
FROM listings l WHERE l.ticker = 'META'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 496.67, 499.80, 495.20, -1.13, 18200000
FROM listings l WHERE l.ticker = 'META'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- NVDA istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 860.50, 865.00, 855.20, -8.30, 38000000
FROM listings l WHERE l.ticker = 'NVDA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 864.20, 870.00, 858.50, 3.70, 39500000
FROM listings l WHERE l.ticker = 'NVDA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 868.00, 874.50, 863.80, 3.80, 40200000
FROM listings l WHERE l.ticker = 'NVDA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 873.40, 880.00, 868.50, 5.40, 41500000
FROM listings l WHERE l.ticker = 'NVDA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 867.75, 878.00, 865.00, -5.65, 41500000
FROM listings l WHERE l.ticker = 'NVDA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- JPM istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 192.80, 194.00, 191.50, 0.90, 9200000
FROM listings l WHERE l.ticker = 'JPM'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 193.50, 194.80, 192.60, 0.70, 9500000
FROM listings l WHERE l.ticker = 'JPM'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 194.10, 195.30, 193.20, 0.60, 9600000
FROM listings l WHERE l.ticker = 'JPM'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 194.80, 196.00, 193.90, 0.70, 9800000
FROM listings l WHERE l.ticker = 'JPM'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 194.19, 195.80, 193.50, -0.61, 9800000
FROM listings l WHERE l.ticker = 'JPM'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- V istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 278.20, 279.50, 277.00, -0.60, 6800000
FROM listings l WHERE l.ticker = 'V'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 279.10, 280.30, 278.20, 0.90, 7000000
FROM listings l WHERE l.ticker = 'V'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 279.80, 281.00, 279.00, 0.70, 7100000
FROM listings l WHERE l.ticker = 'V'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 280.50, 281.80, 279.60, 0.70, 7200000
FROM listings l WHERE l.ticker = 'V'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 279.90, 281.50, 279.20, -0.60, 7200000
FROM listings l WHERE l.ticker = 'V'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- JNJ istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 156.80, 157.50, 155.90, 1.20, 6200000
FROM listings l WHERE l.ticker = 'JNJ'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 156.30, 157.10, 155.60, -0.50, 6300000
FROM listings l WHERE l.ticker = 'JNJ'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 155.90, 156.80, 155.20, -0.40, 6400000
FROM listings l WHERE l.ticker = 'JNJ'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 155.50, 156.40, 154.80, -0.40, 6500000
FROM listings l WHERE l.ticker = 'JNJ'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 155.74, 156.50, 155.00, 0.24, 6500000
FROM listings l WHERE l.ticker = 'JNJ'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- WMT istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 59.40, 59.90, 59.00, 0.25, 7800000
FROM listings l WHERE l.ticker = 'WMT'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 59.70, 60.10, 59.30, 0.30, 8000000
FROM listings l WHERE l.ticker = 'WMT'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 59.90, 60.30, 59.50, 0.20, 8200000
FROM listings l WHERE l.ticker = 'WMT'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 60.10, 60.50, 59.70, 0.20, 8400000
FROM listings l WHERE l.ticker = 'WMT'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 59.93, 60.40, 59.60, -0.17, 8400000
FROM listings l WHERE l.ticker = 'WMT'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- DIS istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 118.20, 119.50, 117.30, 1.80, 9500000
FROM listings l WHERE l.ticker = 'DIS'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 117.50, 118.80, 116.80, -0.70, 9800000
FROM listings l WHERE l.ticker = 'DIS'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 116.80, 118.00, 116.00, -0.70, 10000000
FROM listings l WHERE l.ticker = 'DIS'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 116.10, 117.50, 115.50, -0.70, 10200000
FROM listings l WHERE l.ticker = 'DIS'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 116.60, 117.80, 115.80, 0.50, 10200000
FROM listings l WHERE l.ticker = 'DIS'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- NFLX istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 610.50, 614.00, 608.00, -3.20, 4800000
FROM listings l WHERE l.ticker = 'NFLX'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 613.80, 616.50, 611.20, 3.30, 5000000
FROM listings l WHERE l.ticker = 'NFLX'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 615.20, 618.00, 613.50, 1.40, 5100000
FROM listings l WHERE l.ticker = 'NFLX'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 617.50, 621.00, 615.80, 2.30, 5300000
FROM listings l WHERE l.ticker = 'NFLX'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 616.24, 620.00, 614.50, -1.26, 5300000
FROM listings l WHERE l.ticker = 'NFLX'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- BA istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 184.50, 186.00, 183.00, 2.10, 11000000
FROM listings l WHERE l.ticker = 'BA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 183.20, 185.50, 182.00, -1.30, 11500000
FROM listings l WHERE l.ticker = 'BA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 182.10, 183.80, 181.00, -1.10, 11800000
FROM listings l WHERE l.ticker = 'BA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 181.30, 183.00, 180.50, -0.80, 12100000
FROM listings l WHERE l.ticker = 'BA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 182.57, 183.50, 180.80, 1.27, 12100000
FROM listings l WHERE l.ticker = 'BA'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- INTC istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 31.20, 31.80, 30.80, 0.40, 26000000
FROM listings l WHERE l.ticker = 'INTC'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 30.90, 31.50, 30.50, -0.30, 27000000
FROM listings l WHERE l.ticker = 'INTC'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 30.60, 31.20, 30.20, -0.30, 27500000
FROM listings l WHERE l.ticker = 'INTC'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 30.40, 31.00, 30.00, -0.20, 28500000
FROM listings l WHERE l.ticker = 'INTC'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 30.80, 31.10, 30.30, 0.40, 28500000
FROM listings l WHERE l.ticker = 'INTC'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- GBP/USD istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 1.2910, 1.2940, 1.2885, 0.0018, 90000000
FROM listings l WHERE l.ticker = 'GBP/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 1.2925, 1.2955, 1.2900, 0.0015, 92000000
FROM listings l WHERE l.ticker = 'GBP/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 1.2938, 1.2960, 1.2910, 0.0013, 93000000
FROM listings l WHERE l.ticker = 'GBP/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 1.2950, 1.2975, 1.2920, 0.0012, 94000000
FROM listings l WHERE l.ticker = 'GBP/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 1.2935, 1.2965, 1.2915, -0.0015, 95000000
FROM listings l WHERE l.ticker = 'GBP/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- USD/JPY istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 150.20, 150.80, 149.60, 0.85, 115000000
FROM listings l WHERE l.ticker = 'USD/JPY'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 150.60, 151.20, 150.00, 0.40, 117000000
FROM listings l WHERE l.ticker = 'USD/JPY'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 150.90, 151.50, 150.30, 0.30, 118000000
FROM listings l WHERE l.ticker = 'USD/JPY'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 150.50, 151.30, 150.00, -0.40, 119000000
FROM listings l WHERE l.ticker = 'USD/JPY'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 150.78, 151.40, 150.20, 0.28, 120000000
FROM listings l WHERE l.ticker = 'USD/JPY'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- USD/CHF istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 0.8860, 0.8880, 0.8840, 0.0015, 68000000
FROM listings l WHERE l.ticker = 'USD/CHF'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 0.8845, 0.8870, 0.8825, -0.0015, 69000000
FROM listings l WHERE l.ticker = 'USD/CHF'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 0.8830, 0.8855, 0.8810, -0.0015, 70000000
FROM listings l WHERE l.ticker = 'USD/CHF'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 0.8820, 0.8845, 0.8800, -0.0010, 71000000
FROM listings l WHERE l.ticker = 'USD/CHF'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 0.8808, 0.8835, 0.8790, -0.0012, 72000000
FROM listings l WHERE l.ticker = 'USD/CHF'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- AUD/USD istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 0.6490, 0.6510, 0.6470, -0.0010, 55000000
FROM listings l WHERE l.ticker = 'AUD/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 0.6500, 0.6520, 0.6480, 0.0010, 56000000
FROM listings l WHERE l.ticker = 'AUD/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 0.6508, 0.6530, 0.6490, 0.0008, 57000000
FROM listings l WHERE l.ticker = 'AUD/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 0.6515, 0.6535, 0.6495, 0.0007, 57500000
FROM listings l WHERE l.ticker = 'AUD/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 0.6509, 0.6530, 0.6490, -0.0006, 58000000
FROM listings l WHERE l.ticker = 'AUD/USD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- USD/CAD istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 1.3560, 1.3585, 1.3535, -0.0020, 62000000
FROM listings l WHERE l.ticker = 'USD/CAD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 1.3575, 1.3600, 1.3550, 0.0015, 63000000
FROM listings l WHERE l.ticker = 'USD/CAD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 1.3588, 1.3610, 1.3565, 0.0013, 63500000
FROM listings l WHERE l.ticker = 'USD/CAD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 1.3598, 1.3620, 1.3575, 0.0010, 64000000
FROM listings l WHERE l.ticker = 'USD/CAD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 1.3581, 1.3610, 1.3560, -0.0017, 65000000
FROM listings l WHERE l.ticker = 'USD/CAD'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- EUR/GBP istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 0.8545, 0.8560, 0.8530, 0.0012, 40000000
FROM listings l WHERE l.ticker = 'EUR/GBP'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 0.8538, 0.8555, 0.8520, -0.0007, 40500000
FROM listings l WHERE l.ticker = 'EUR/GBP'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 0.8530, 0.8548, 0.8512, -0.0008, 41000000
FROM listings l WHERE l.ticker = 'EUR/GBP'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 0.8525, 0.8540, 0.8508, -0.0005, 41500000
FROM listings l WHERE l.ticker = 'EUR/GBP'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 0.8532, 0.8548, 0.8515, 0.0007, 42000000
FROM listings l WHERE l.ticker = 'EUR/GBP'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- EUR/JPY istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 162.10, 162.80, 161.50, 0.65, 52000000
FROM listings l WHERE l.ticker = 'EUR/JPY'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 162.45, 163.10, 161.80, 0.35, 53000000
FROM listings l WHERE l.ticker = 'EUR/JPY'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 162.70, 163.30, 162.10, 0.25, 53500000
FROM listings l WHERE l.ticker = 'EUR/JPY'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 162.90, 163.50, 162.30, 0.20, 54000000
FROM listings l WHERE l.ticker = 'EUR/JPY'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 162.56, 163.20, 162.00, -0.34, 55000000
FROM listings l WHERE l.ticker = 'EUR/JPY'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- CLM26 futures istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 69.80, 70.50, 69.20, 0.95, 295000
FROM listings l WHERE l.ticker = 'CLM26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 69.50, 70.20, 68.80, -0.30, 300000
FROM listings l WHERE l.ticker = 'CLM26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 69.10, 69.80, 68.50, -0.40, 305000
FROM listings l WHERE l.ticker = 'CLM26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 68.80, 69.50, 68.30, -0.30, 308000
FROM listings l WHERE l.ticker = 'CLM26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 69.32, 69.80, 68.60, 0.52, 312000
FROM listings l WHERE l.ticker = 'CLM26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- GCQ26 gold futures istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 2325.00, 2332.00, 2318.00, 8.50, 170000
FROM listings l WHERE l.ticker = 'GCQ26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 2330.50, 2338.00, 2324.00, 5.50, 175000
FROM listings l WHERE l.ticker = 'GCQ26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 2335.80, 2342.00, 2328.00, 5.30, 178000
FROM listings l WHERE l.ticker = 'GCQ26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 2340.20, 2348.00, 2333.00, 4.40, 180000
FROM listings l WHERE l.ticker = 'GCQ26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 2333.40, 2346.00, 2330.00, -6.80, 185000
FROM listings l WHERE l.ticker = 'GCQ26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- SIH26 silver futures istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 26.80, 27.10, 26.50, -0.25, 58000
FROM listings l WHERE l.ticker = 'SIH26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 26.95, 27.20, 26.70, 0.15, 59000
FROM listings l WHERE l.ticker = 'SIH26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 27.10, 27.35, 26.85, 0.15, 60000
FROM listings l WHERE l.ticker = 'SIH26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 27.20, 27.45, 26.95, 0.10, 62000
FROM listings l WHERE l.ticker = 'SIH26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 27.17, 27.40, 26.90, -0.03, 64000
FROM listings l WHERE l.ticker = 'SIH26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- NGK26 natural gas futures istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 2.0500, 2.0800, 2.0200, -0.0200, 138000
FROM listings l WHERE l.ticker = 'NGK26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 2.0650, 2.0900, 2.0400, 0.0150, 140000
FROM listings l WHERE l.ticker = 'NGK26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 2.0750, 2.1000, 2.0500, 0.0100, 141000
FROM listings l WHERE l.ticker = 'NGK26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 2.0850, 2.1100, 2.0600, 0.0100, 143000
FROM listings l WHERE l.ticker = 'NGK26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 2.0700, 2.1050, 2.0500, -0.0150, 145000
FROM listings l WHERE l.ticker = 'NGK26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- ZCN26 corn futures istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 444.50, 447.00, 442.00, 2.50, 92000
FROM listings l WHERE l.ticker = 'ZCN26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 445.80, 448.00, 443.50, 1.30, 93000
FROM listings l WHERE l.ticker = 'ZCN26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 447.20, 449.50, 445.00, 1.40, 94000
FROM listings l WHERE l.ticker = 'ZCN26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 448.80, 451.00, 446.50, 1.60, 96000
FROM listings l WHERE l.ticker = 'ZCN26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 446.75, 450.00, 445.00, -2.05, 98000
FROM listings l WHERE l.ticker = 'ZCN26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- ZWN26 wheat futures istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 588.00, 592.00, 585.00, 3.50, 68000
FROM listings l WHERE l.ticker = 'ZWN26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 586.50, 590.00, 583.50, -1.50, 69000
FROM listings l WHERE l.ticker = 'ZWN26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 584.80, 588.00, 582.00, -1.70, 70000
FROM listings l WHERE l.ticker = 'ZWN26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 583.00, 586.50, 580.50, -1.80, 71000
FROM listings l WHERE l.ticker = 'ZWN26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 584.75, 587.00, 581.50, 1.75, 72000
FROM listings l WHERE l.ticker = 'ZWN26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- HGK26 copper futures istorija (5 dana)
INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 4.1500, 4.1800, 4.1200, 0.0250, 50000
FROM listings l WHERE l.ticker = 'HGK26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 5 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 4.1650, 4.1900, 4.1400, 0.0150, 51000
FROM listings l WHERE l.ticker = 'HGK26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 4 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 4.1780, 4.2050, 4.1550, 0.0130, 52000
FROM listings l WHERE l.ticker = 'HGK26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 3 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 4.1900, 4.2150, 4.1650, 0.0120, 53000
FROM listings l WHERE l.ticker = 'HGK26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 2 DAY));

INSERT INTO listing_daily_prices (listing_id, date, price, high, low, price_change, volume)
SELECT l.id, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 4.1730, 4.2100, 4.1600, -0.0170, 54000
FROM listings l WHERE l.ticker = 'HGK26'
AND NOT EXISTS (SELECT 1 FROM listing_daily_prices WHERE listing_id = l.id AND date = DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- ============================================================
-- ACTUARY INFO (aktuarski podaci za zaposlene)
-- ============================================================

INSERT INTO actuary_info (employee_id, actuary_type, daily_limit, used_limit, need_approval)
SELECT e.id, 'SUPERVISOR', NULL, 0, false
FROM employees e WHERE e.email = 'nikola.milenkovic@banka.rs'
AND NOT EXISTS (SELECT 1 FROM actuary_info WHERE employee_id = e.id);

INSERT INTO actuary_info (employee_id, actuary_type, daily_limit, used_limit, need_approval)
SELECT e.id, 'AGENT', 100000, 0, false
FROM employees e WHERE e.email = 'tamara.pavlovic@banka.rs'
AND NOT EXISTS (SELECT 1 FROM actuary_info WHERE employee_id = e.id);

INSERT INTO actuary_info (employee_id, actuary_type, daily_limit, used_limit, need_approval)
SELECT e.id, 'AGENT', 50000, 15000, true
FROM employees e WHERE e.email = 'nemanja.savic@banka.rs'
AND NOT EXISTS (SELECT 1 FROM actuary_info WHERE employee_id = e.id);

-- ============================================================
-- PORTFOLIOS (hartije od vrednosti u vlasnistvu korisnika)
-- ============================================================
-- Stefan Jovanovic poseduje akcije AAPL, MSFT, TSLA i futures CLM26

-- Stefan drzi 50 AAPL akcija (kupljeno po $145.00)
INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT u.id, l.id, l.ticker, l.name, 'STOCK', 50, 145.0000, 0, NOW()
FROM users u, listings l
WHERE u.email = 'stefan.jovanovic@gmail.com' AND l.ticker = 'AAPL'
AND NOT EXISTS (
    SELECT 1 FROM portfolios p WHERE p.user_id = u.id AND p.listing_id = l.id
);

-- Stefan drzi 30 MSFT akcija (kupljeno po $380.50)
INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT u.id, l.id, l.ticker, l.name, 'STOCK', 30, 380.5000, 10, NOW()
FROM users u, listings l
WHERE u.email = 'stefan.jovanovic@gmail.com' AND l.ticker = 'MSFT'
AND NOT EXISTS (
    SELECT 1 FROM portfolios p WHERE p.user_id = u.id AND p.listing_id = l.id
);

-- Stefan drzi 20 TSLA akcija (kupljeno po $265.00 — u gubitku)
INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT u.id, l.id, l.ticker, l.name, 'STOCK', 20, 265.0000, 0, NOW()
FROM users u, listings l
WHERE u.email = 'stefan.jovanovic@gmail.com' AND l.ticker = 'TSLA'
AND NOT EXISTS (
    SELECT 1 FROM portfolios p WHERE p.user_id = u.id AND p.listing_id = l.id
);

-- Stefan drzi 5 CLM26 futures ugovora (kupljeno po $65.20)
INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT u.id, l.id, l.ticker, l.name, 'FUTURES', 5, 65.2000, 0, NOW()
FROM users u, listings l
WHERE u.email = 'stefan.jovanovic@gmail.com' AND l.ticker = 'CLM26'
AND NOT EXISTS (
    SELECT 1 FROM portfolios p WHERE p.user_id = u.id AND p.listing_id = l.id
);

-- Milica drzi 100 GOOG akcija (kupljeno po $155.00)
INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT u.id, l.id, l.ticker, l.name, 'STOCK', 100, 155.0000, 25, NOW()
FROM users u, listings l
WHERE u.email = 'milica.nikolic@gmail.com' AND l.ticker = 'GOOG'
AND NOT EXISTS (
    SELECT 1 FROM portfolios p WHERE p.user_id = u.id AND p.listing_id = l.id
);

-- Milica drzi 15 AMZN akcija (kupljeno po $172.80)
INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT u.id, l.id, l.ticker, l.name, 'STOCK', 15, 172.8000, 0, NOW()
FROM users u, listings l
WHERE u.email = 'milica.nikolic@gmail.com' AND l.ticker = 'AMZN'
AND NOT EXISTS (
    SELECT 1 FROM portfolios p WHERE p.user_id = u.id AND p.listing_id = l.id
);


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
    CURDATE() + INTERVAL 15 DAY,
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
    CURDATE() + INTERVAL 15 DAY,
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
    CURDATE() - INTERVAL 1 DAY,
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
    CURDATE() + INTERVAL 15 DAY,
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
    CURDATE() + INTERVAL 20 DAY,
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
    CURDATE() + INTERVAL 20 DAY,
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
    CURDATE() + INTERVAL 15 DAY, 100,
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
    CURDATE() + INTERVAL 15 DAY, 100,
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
    CURDATE() - INTERVAL 1 DAY, 100,
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
    CURDATE() + INTERVAL 15 DAY, 100,
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
    CURDATE() + INTERVAL 20 DAY, 100,
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
    CURDATE() + INTERVAL 20 DAY, 100,
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
INSERT IGNORE INTO actuary_info (actuary_type, daily_limit, need_approval, used_limit, employee_id)
SELECT 'SUPERVISOR', NULL, 0, 0.00, id FROM employees WHERE email = 'marko.petrovic@banka.rs';

INSERT IGNORE INTO actuary_info (actuary_type, daily_limit, need_approval, used_limit, employee_id)
SELECT 'SUPERVISOR', NULL, 0, 0.00, id FROM employees WHERE email = 'jelena.djordjevic@banka.rs';

-- Dodaj vise agenata (Djordje=agent, Maja=agent)
INSERT IGNORE INTO actuary_info (actuary_type, daily_limit, need_approval, used_limit, employee_id)
SELECT 'AGENT', 150000.00, 0, 25000.00, id FROM employees WHERE email = 'djordje.jankovic@banka.rs';

INSERT IGNORE INTO actuary_info (actuary_type, daily_limit, need_approval, used_limit, employee_id)
SELECT 'AGENT', 200000.00, 1, 180000.00, id FROM employees WHERE email = 'maja.ristic@banka.rs';

-- ============================================================
-- ORDERS (sample nalozi za prikaz)
-- ============================================================
-- user_id 3 = Stefan (CLIENT), user_id 1 = Marko (ADMIN)
-- listing_id 1 = AAPL, 2 = MSFT, 9 = CLM26

INSERT IGNORE INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    -- Stefan kupio 50 AAPL - DONE
    (1, 0, 0, 'No need for approval', 9250.0000, 1, DATE_SUB(NOW(), INTERVAL 5 DAY),
     'BUY', 1, DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, 0, 'MARKET', 185.0000,
     50, 0, 'DONE', NULL, 3, 'CLIENT', 1),
    -- Stefan kupio 30 MSFT - DONE
    (1, 0, 0, 'No need for approval', 11415.0000, 1, DATE_SUB(NOW(), INTERVAL 4 DAY),
     'BUY', 1, DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, 0, 'MARKET', 380.5000,
     30, 0, 'DONE', NULL, 3, 'CLIENT', 2),
    -- Stefan - LIMIT BUY GOOG - APPROVED, u toku
    (1, 0, 0, 'No need for approval', 5200.0000, 1, DATE_SUB(NOW(), INTERVAL 1 DAY),
     'BUY', 0, NOW(), 170.0000, 0, 'LIMIT', 173.0000,
     30, 15, 'APPROVED', NULL, 3, 'CLIENT', 3),
    -- Stefan - SELL AAPL - PENDING (AON)
    (1, 0, 1, NULL, 4750.0000, 1, NOW(),
     'SELL', 0, NOW(), NULL, 0, 'MARKET', 190.0000,
     25, 25, 'PENDING', NULL, 3, 'CLIENT', 1),
    -- Tamara (agent, employee 2) - BUY TSLA - PENDING (needs supervisor approval)
    (NULL, 0, 0, NULL, 12500.0000, 1, NOW(),
     'BUY', 0, NOW(), NULL, 0, 'MARKET', 250.0000,
     50, 50, 'PENDING', NULL, 2, 'EMPLOYEE', 4),
    -- Tamara - STOP-LIMIT SELL MSFT - APPROVED
    (NULL, 1, 0, 'Nikola Milenkovic', 6000.0000, 1, DATE_SUB(NOW(), INTERVAL 2 DAY),
     'SELL', 0, NOW(), 400.0000, 0, 'STOP_LIMIT', 410.0000,
     15, 10, 'APPROVED', 395.0000, 2, 'EMPLOYEE', 2),
    -- Djordje (agent, employee 3) - BUY futures CLM26 - DONE
    (NULL, 0, 0, 'Nikola Milenkovic', 325000.0000, 1000, DATE_SUB(NOW(), INTERVAL 7 DAY),
     'BUY', 1, DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, 0, 'MARKET', 65.0000,
     5, 0, 'DONE', NULL, 3, 'EMPLOYEE', 9),
    -- Milica - BUY GOOG - DONE
    (4, 0, 0, 'No need for approval', 15500.0000, 1, DATE_SUB(NOW(), INTERVAL 3 DAY),
     'BUY', 1, DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, 0, 'MARKET', 155.0000,
     100, 0, 'DONE', NULL, 4, 'CLIENT', 3);

-- ============================================================
-- MARGIN ACCOUNTS
-- ============================================================
-- client_id: 1=Stefan, 2=Milica, 3=Lazar, 4=Ana
-- account_id: 1=Stefan RSD, 4=Milica RSD, 7=Lazar RSD, 10=Ana Youth RSD

INSERT IGNORE INTO margin_accounts (bank_participation, created_at, initial_margin, loan_value,
                                    maintenance_margin, status, user_id, account_id)
VALUES
    -- Stefan (client_id=1): aktivan margin racun, linked to his RSD checking (account_id=1)
    (0.4000, NOW(), 50000.0000, 20000.0000, 25000.0000, 'ACTIVE', 1, 1),
    -- Milica (client_id=2): blokiran margin racun, linked to her RSD checking (account_id=4)
    (0.5000, NOW(), 25000.0000, 12500.0000, 12500.0000, 'BLOCKED', 2, 4),
    -- Lazar (client_id=3): aktivan margin racun, linked to his RSD checking (account_id=7)
    (0.3000, NOW(), 40000.0000, 12000.0000, 20000.0000, 'ACTIVE', 3, 7),
    -- Ana (client_id=4): aktivan margin racun, linked to her Youth RSD (account_id=10)
    (0.4500, NOW(), 30000.0000, 13500.0000, 15000.0000, 'ACTIVE', 4, 10);

-- Margin transactions for Stefan's margin account
INSERT IGNORE INTO margin_transactions (amount, created_at, description, type, margin_account_id)
SELECT 50000.00, DATE_SUB(NOW(), INTERVAL 3 DAY), 'Inicijalna uplata', 'DEPOSIT', id
FROM margin_accounts WHERE user_id = 1 LIMIT 1;

INSERT IGNORE INTO margin_transactions (amount, created_at, description, type, margin_account_id)
SELECT 20000.00, DATE_SUB(NOW(), INTERVAL 1 DAY), 'Isplata', 'WITHDRAWAL', id
FROM margin_accounts WHERE user_id = 1 LIMIT 1;

-- Margin transactions for Lazar's margin account
INSERT IGNORE INTO margin_transactions (amount, created_at, description, type, margin_account_id)
SELECT 40000.00, DATE_SUB(NOW(), INTERVAL 5 DAY), 'Inicijalna uplata', 'DEPOSIT', id
FROM margin_accounts WHERE user_id = 3 LIMIT 1;

INSERT IGNORE INTO margin_transactions (amount, created_at, description, type, margin_account_id)
SELECT 10000.00, DATE_SUB(NOW(), INTERVAL 2 DAY), 'Delimicna isplata', 'WITHDRAWAL', id
FROM margin_accounts WHERE user_id = 3 LIMIT 1;

-- Margin transactions for Ana's margin account
INSERT IGNORE INTO margin_transactions (amount, created_at, description, type, margin_account_id)
SELECT 30000.00, DATE_SUB(NOW(), INTERVAL 4 DAY), 'Inicijalna uplata', 'DEPOSIT', id
FROM margin_accounts WHERE user_id = 4 LIMIT 1;

-- ============================================================
-- TAX RECORDS
-- ============================================================

-- Obrisi stare tax zapise pre ponovnog ubacivanja (sprecava duplikate pri re-seed)
DELETE FROM tax_records;

-- client_id: 1=Stefan, 2=Milica, 3=Lazar, 4=Ana
-- employee_id: 1=Marko, 2=Jelena, 3=Nikola, 4=Tamara, 5=Djordje, 6=Maja
INSERT IGNORE INTO tax_records (calculated_at, currency, tax_owed, tax_paid, total_profit,
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
-- Nikola Milenkovic (supervisor, employee_id=3) poseduje AAPL, GOOG, EUR/USD forex
INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 3, l.id, l.ticker, l.name, 'STOCK', 25, 178.5000, 0, NOW()
FROM listings l WHERE l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 3 AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 3, l.id, l.ticker, l.name, 'STOCK', 40, 162.0000, 0, NOW()
FROM listings l WHERE l.ticker = 'GOOG'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 3 AND p.listing_id = l.id);

-- Tamara Pavlovic (agent, employee_id=4) poseduje MSFT, TSLA
INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 4, l.id, l.ticker, l.name, 'STOCK', 15, 395.0000, 0, NOW()
FROM listings l WHERE l.ticker = 'MSFT'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 4 AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 4, l.id, l.ticker, l.name, 'STOCK', 10, 248.0000, 0, NOW()
FROM listings l WHERE l.ticker = 'TSLA'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 4 AND p.listing_id = l.id);

-- Djordje Jankovic (agent, employee_id=5) poseduje CLM26 futures, AMZN
INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 5, l.id, l.ticker, l.name, 'FUTURES', 5, 65.2000, 0, NOW()
FROM listings l WHERE l.ticker = 'CLM26'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 5 AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 5, l.id, l.ticker, l.name, 'STOCK', 20, 178.5000, 0, NOW()
FROM listings l WHERE l.ticker = 'AMZN'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 5 AND p.listing_id = l.id);

-- Maja Ristic (agent, employee_id=6) poseduje AAPL, MSFT, NVDA
INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 6, l.id, l.ticker, l.name, 'STOCK', 20, 180.0000, 0, NOW()
FROM listings l WHERE l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 6 AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 6, l.id, l.ticker, l.name, 'STOCK', 10, 390.0000, 0, NOW()
FROM listings l WHERE l.ticker = 'MSFT'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 6 AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 6, l.id, l.ticker, l.name, 'STOCK', 8, 820.0000, 0, NOW()
FROM listings l WHERE l.ticker = 'NVDA'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 6 AND p.listing_id = l.id);

-- Lazar Ilic (klijent, user_id=3) poseduje TSLA, GOOG
INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT u.id, l.id, l.ticker, l.name, 'STOCK', 15, 255.0000, 0, NOW()
FROM users u, listings l
WHERE u.email = 'lazar.ilic@yahoo.com' AND l.ticker = 'TSLA'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = u.id AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT u.id, l.id, l.ticker, l.name, 'STOCK', 30, 160.0000, 0, NOW()
FROM users u, listings l
WHERE u.email = 'lazar.ilic@yahoo.com' AND l.ticker = 'GOOG'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = u.id AND p.listing_id = l.id);

-- Ana Stojanovic (klijent) poseduje NVDA, AAPL
INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT u.id, l.id, l.ticker, l.name, 'STOCK', 5, 810.0000, 0, NOW()
FROM users u, listings l
WHERE u.email = 'ana.stojanovic@hotmail.com' AND l.ticker = 'NVDA'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = u.id AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT u.id, l.id, l.ticker, l.name, 'STOCK', 10, 175.0000, 0, NOW()
FROM users u, listings l
WHERE u.email = 'ana.stojanovic@hotmail.com' AND l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = u.id AND p.listing_id = l.id);

-- Marko Petrovic (admin/supervizor, employee_id=1) poseduje AAPL, MSFT, AMZN
INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 1, l.id, l.ticker, l.name, 'STOCK', 30, 176.0000, 5, NOW()
FROM listings l WHERE l.ticker = 'AAPL'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 1 AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 1, l.id, l.ticker, l.name, 'STOCK', 20, 385.0000, 0, NOW()
FROM listings l WHERE l.ticker = 'MSFT'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 1 AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 1, l.id, l.ticker, l.name, 'STOCK', 12, 175.0000, 0, NOW()
FROM listings l WHERE l.ticker = 'AMZN'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 1 AND p.listing_id = l.id);

-- Jelena Djordjevic (admin/supervizor, employee_id=2) poseduje GOOG, NVDA
INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 2, l.id, l.ticker, l.name, 'STOCK', 35, 158.0000, 0, NOW()
FROM listings l WHERE l.ticker = 'GOOG'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 2 AND p.listing_id = l.id);

INSERT INTO portfolios (user_id, listing_id, listing_ticker, listing_name, listing_type, quantity, average_buy_price, public_quantity, last_modified)
SELECT 2, l.id, l.ticker, l.name, 'STOCK', 6, 800.0000, 0, NOW()
FROM listings l WHERE l.ticker = 'NVDA'
AND NOT EXISTS (SELECT 1 FROM portfolios p WHERE p.user_id = 2 AND p.listing_id = l.id);

-- ============================================================
-- ORDERS ZA ZAPOSLENE (aktuare) — razliciti statusi za testiranje
-- ============================================================

-- Nikola (supervisor, emp_id=3) — BUY AAPL DONE
INSERT IGNORE INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, 'No need for approval', 4462.5000, 1, DATE_SUB(NOW(), INTERVAL 10 DAY),
     'BUY', 1, DATE_SUB(NOW(), INTERVAL 10 DAY), NULL, 0, 'MARKET', 178.5000,
     25, 0, 'DONE', NULL, 3, 'EMPLOYEE', 1);

-- Nikola — BUY GOOG DONE
INSERT IGNORE INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, 'No need for approval', 6480.0000, 1, DATE_SUB(NOW(), INTERVAL 8 DAY),
     'BUY', 1, DATE_SUB(NOW(), INTERVAL 8 DAY), NULL, 0, 'MARKET', 162.0000,
     40, 0, 'DONE', NULL, 3, 'EMPLOYEE', 3);

-- Maja (agent, emp_id=6) — BUY AAPL DONE
INSERT IGNORE INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, 'Nikola Milenkovic', 3600.0000, 1, DATE_SUB(NOW(), INTERVAL 6 DAY),
     'BUY', 1, DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, 0, 'MARKET', 180.0000,
     20, 0, 'DONE', NULL, 6, 'EMPLOYEE', 1);

-- Maja — BUY MSFT DONE
INSERT IGNORE INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, 'Nikola Milenkovic', 3900.0000, 1, DATE_SUB(NOW(), INTERVAL 5 DAY),
     'BUY', 1, DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, 0, 'MARKET', 390.0000,
     10, 0, 'DONE', NULL, 6, 'EMPLOYEE', 2);

-- Maja — BUY NVDA DONE
INSERT IGNORE INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, 'Nikola Milenkovic', 6560.0000, 1, DATE_SUB(NOW(), INTERVAL 4 DAY),
     'BUY', 1, DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, 0, 'MARKET', 820.0000,
     8, 0, 'DONE', NULL, 6, 'EMPLOYEE', 5);

-- Maja — LIMIT BUY GOOG PENDING (treba odobrenje jer needApproval=true)
INSERT IGNORE INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, NULL, 3400.0000, 1, NOW(),
     'BUY', 0, NOW(), 170.0000, 0, 'LIMIT', 170.0000,
     20, 20, 'PENDING', NULL, 6, 'EMPLOYEE', 3);

-- Maja — SELL AAPL APPROVED (u toku izvrsavanja)
INSERT IGNORE INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, 'Nikola Milenkovic', 950.0000, 1, DATE_SUB(NOW(), INTERVAL 1 DAY),
     'SELL', 0, NOW(), NULL, 0, 'MARKET', 190.0000,
     5, 3, 'APPROVED', NULL, 6, 'EMPLOYEE', 1);

-- Tamara (agent, emp_id=4) — BUY MSFT DONE
INSERT IGNORE INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, 'Nikola Milenkovic', 5925.0000, 1, DATE_SUB(NOW(), INTERVAL 9 DAY),
     'BUY', 1, DATE_SUB(NOW(), INTERVAL 9 DAY), NULL, 0, 'MARKET', 395.0000,
     15, 0, 'DONE', NULL, 4, 'EMPLOYEE', 2);

-- Djordje (agent, emp_id=5) — BUY AMZN DONE
INSERT IGNORE INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, 'Nikola Milenkovic', 3570.0000, 1, DATE_SUB(NOW(), INTERVAL 7 DAY),
     'BUY', 1, DATE_SUB(NOW(), INTERVAL 7 DAY), NULL, 0, 'MARKET', 178.5000,
     20, 0, 'DONE', NULL, 5, 'EMPLOYEE', 6);

-- Marko (admin/supervisor, emp_id=1) — BUY AAPL, MSFT, AMZN DONE
INSERT IGNORE INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, 'No need for approval', 5280.0000, 1, DATE_SUB(NOW(), INTERVAL 14 DAY),
     'BUY', 1, DATE_SUB(NOW(), INTERVAL 14 DAY), NULL, 0, 'MARKET', 176.0000,
     30, 0, 'DONE', NULL, 1, 'EMPLOYEE', 1),
    (NULL, 0, 0, 'No need for approval', 7700.0000, 1, DATE_SUB(NOW(), INTERVAL 12 DAY),
     'BUY', 1, DATE_SUB(NOW(), INTERVAL 12 DAY), NULL, 0, 'MARKET', 385.0000,
     20, 0, 'DONE', NULL, 1, 'EMPLOYEE', 2),
    (NULL, 0, 0, 'No need for approval', 2100.0000, 1, DATE_SUB(NOW(), INTERVAL 11 DAY),
     'BUY', 1, DATE_SUB(NOW(), INTERVAL 11 DAY), NULL, 0, 'MARKET', 175.0000,
     12, 0, 'DONE', NULL, 1, 'EMPLOYEE', 6);

-- Jelena (admin/supervisor, emp_id=2) — BUY GOOG, NVDA DONE
INSERT IGNORE INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (NULL, 0, 0, 'No need for approval', 5530.0000, 1, DATE_SUB(NOW(), INTERVAL 13 DAY),
     'BUY', 1, DATE_SUB(NOW(), INTERVAL 13 DAY), NULL, 0, 'MARKET', 158.0000,
     35, 0, 'DONE', NULL, 2, 'EMPLOYEE', 3),
    (NULL, 0, 0, 'No need for approval', 4800.0000, 1, DATE_SUB(NOW(), INTERVAL 10 DAY),
     'BUY', 1, DATE_SUB(NOW(), INTERVAL 10 DAY), NULL, 0, 'MARKET', 800.0000,
     6, 0, 'DONE', NULL, 2, 'EMPLOYEE', 5);

-- Lazar (klijent) — BUY TSLA, GOOG DONE
INSERT IGNORE INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (7, 0, 0, 'No need for approval', 3825.0000, 1, DATE_SUB(NOW(), INTERVAL 6 DAY),
     'BUY', 1, DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, 0, 'MARKET', 255.0000,
     15, 0, 'DONE', NULL, 3, 'CLIENT', 4),
    (7, 0, 0, 'No need for approval', 4800.0000, 1, DATE_SUB(NOW(), INTERVAL 5 DAY),
     'BUY', 1, DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, 0, 'MARKET', 160.0000,
     30, 0, 'DONE', NULL, 3, 'CLIENT', 3);

-- Ana (klijent) — BUY NVDA, AAPL DONE + PENDING SELL
INSERT IGNORE INTO orders (account_id, after_hours, all_or_none, approved_by, approximate_price,
                           contract_size, created_at, direction, is_done, last_modification,
                           limit_value, is_margin, order_type, price_per_unit, quantity,
                           remaining_portions, status, stop_value, user_id, user_role, listing_id)
VALUES
    (11, 0, 0, 'No need for approval', 4050.0000, 1, DATE_SUB(NOW(), INTERVAL 8 DAY),
     'BUY', 1, DATE_SUB(NOW(), INTERVAL 8 DAY), NULL, 0, 'MARKET', 810.0000,
     5, 0, 'DONE', NULL, 4, 'CLIENT', 5),
    (11, 0, 0, 'No need for approval', 1750.0000, 1, DATE_SUB(NOW(), INTERVAL 7 DAY),
     'BUY', 1, DATE_SUB(NOW(), INTERVAL 7 DAY), NULL, 0, 'MARKET', 175.0000,
     10, 0, 'DONE', NULL, 4, 'CLIENT', 1),
    (11, 0, 1, NULL, 1900.0000, 1, NOW(),
     'SELL', 0, NOW(), NULL, 0, 'MARKET', 190.0000,
     3, 3, 'PENDING', NULL, 4, 'CLIENT', 1);