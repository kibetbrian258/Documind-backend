-- Emails are stored lower-case so "Brian@x.com" and "brian@x.com" are one account.
CREATE TABLE users (
                       id            UUID         PRIMARY KEY,
                       email         VARCHAR(254) NOT NULL,
                       password_hash VARCHAR(100) NOT NULL,
                       role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
                       daily_limit   INTEGER      NOT NULL DEFAULT 50,
                       created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

                       CONSTRAINT uk_users_email           UNIQUE (email),
                       CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email)),
                       CONSTRAINT ck_users_role            CHECK (role IN ('USER', 'ADMIN')),
                       CONSTRAINT ck_users_daily_limit     CHECK (daily_limit >= 0)
);