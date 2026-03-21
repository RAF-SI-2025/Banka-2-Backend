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
(8, 'RSD', 'Serbian Dinar', 'RSD', 'Serbia', 'Serbian Dinar – currency of Serbia', true)
ON DUPLICATE KEY UPDATE code = code;

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
                       majority_owner_id, active, created_at)
VALUES
    (3, 'Banka 2025 Tim 2', '22200022', '222000222', '64.19',
     'Bulevar Kralja Aleksandra 73, Beograd',
     NULL, 1, NOW())
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

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, base_currency, quote_currency, liquidity,
                      contract_size, contract_unit, settlement_date)
SELECT * FROM (SELECT
  'AAPL' AS ticker, 'Apple Inc.' AS name, 'NASDAQ' AS ea, 'STOCK' AS lt,
  189.8400 AS price, 190.1200 AS ask, 189.5600 AS bid, 54230000 AS vol, 2.3400 AS pc, NOW() AS lr,
  15500000000 AS os, 0.0055 AS dy, NULL AS bc, NULL AS qc, NULL AS liq, 1 AS cs, NULL AS cu, NULL AS sd
) AS tmp WHERE NOT EXISTS (SELECT 1 FROM listings WHERE ticker = 'AAPL');

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
SELECT * FROM (SELECT
  'MSFT', 'Microsoft Corp.', 'NASDAQ', 'STOCK',
  415.2600, 415.8000, 414.7200, 22100000, -1.1800, NOW(),
  7430000000, 0.0072, 1
) AS tmp WHERE NOT EXISTS (SELECT 1 FROM listings WHERE ticker = 'MSFT');

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
SELECT * FROM (SELECT
  'GOOG', 'Alphabet Inc.', 'NASDAQ', 'STOCK',
  173.4500, 173.9000, 173.0000, 18500000, 0.8700, NOW(),
  12200000000, 0.0000, 1
) AS tmp WHERE NOT EXISTS (SELECT 1 FROM listings WHERE ticker = 'GOOG');

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
SELECT * FROM (SELECT
  'TSLA', 'Tesla Inc.', 'NASDAQ', 'STOCK',
  248.9100, 249.5000, 248.3200, 72300000, -5.4300, NOW(),
  3180000000, 0.0000, 1
) AS tmp WHERE NOT EXISTS (SELECT 1 FROM listings WHERE ticker = 'TSLA');

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      outstanding_shares, dividend_yield, contract_size)
SELECT * FROM (SELECT
  'AMZN', 'Amazon.com Inc.', 'NASDAQ', 'STOCK',
  186.3200, 186.8000, 185.8400, 35600000, 1.5600, NOW(),
  10300000000, 0.0000, 1
) AS tmp WHERE NOT EXISTS (SELECT 1 FROM listings WHERE ticker = 'AMZN');

-- Forex parovi
INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      base_currency, quote_currency, liquidity, contract_size)
SELECT * FROM (SELECT
  'EUR/USD', 'Euro / US Dollar', 'FOREX', 'FOREX',
  1.0856, 1.0858, 1.0854, 180000000, 0.0012, NOW(),
  'EUR', 'USD', 'HIGH', 1000
) AS tmp WHERE NOT EXISTS (SELECT 1 FROM listings WHERE ticker = 'EUR/USD');

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      base_currency, quote_currency, liquidity, contract_size)
SELECT * FROM (SELECT
  'GBP/USD', 'British Pound / US Dollar', 'FOREX', 'FOREX',
  1.2943, 1.2946, 1.2940, 95000000, -0.0008, NOW(),
  'GBP', 'USD', 'HIGH', 1000
) AS tmp WHERE NOT EXISTS (SELECT 1 FROM listings WHERE ticker = 'GBP/USD');

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      base_currency, quote_currency, liquidity, contract_size)
SELECT * FROM (SELECT
  'USD/JPY', 'US Dollar / Japanese Yen', 'FOREX', 'FOREX',
  149.2300, 149.2600, 149.2000, 120000000, 0.4500, NOW(),
  'USD', 'JPY', 'HIGH', 1000
) AS tmp WHERE NOT EXISTS (SELECT 1 FROM listings WHERE ticker = 'USD/JPY');

-- Futures
INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      contract_size, contract_unit, settlement_date)
SELECT * FROM (SELECT
  'CLM26', 'Crude Oil June 2026', 'CME', 'FUTURES',
  68.4500, 68.5200, 68.3800, 312000, -0.8700, NOW(),
  1000, 'Barrel', '2026-06-20'
) AS tmp WHERE NOT EXISTS (SELECT 1 FROM listings WHERE ticker = 'CLM26');

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      contract_size, contract_unit, settlement_date)
SELECT * FROM (SELECT
  'GCQ26', 'Gold August 2026', 'CME', 'FUTURES',
  2345.8000, 2346.5000, 2345.1000, 185000, 12.4000, NOW(),
  100, 'Troy Ounce', '2026-08-27'
) AS tmp WHERE NOT EXISTS (SELECT 1 FROM listings WHERE ticker = 'GCQ26');

INSERT INTO listings (ticker, name, exchange_acronym, listing_type, price, ask, bid, volume, price_change, last_refresh,
                      contract_size, contract_unit, settlement_date)
SELECT * FROM (SELECT
  'SIH26', 'Silver March 2026', 'CME', 'FUTURES',
  27.3500, 27.3900, 27.3100, 64000, 0.1800, NOW(),
  5000, 'Troy Ounce', '2026-03-27'
) AS tmp WHERE NOT EXISTS (SELECT 1 FROM listings WHERE ticker = 'SIH26');

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
