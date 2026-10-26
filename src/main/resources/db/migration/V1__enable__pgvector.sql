-- Adds vector types, distance operators and HNSW indexes to Postgres.
-- Application tables (users, documents, ...) arrive in later migrations.
CREATE EXTENSION IF NOT EXISTS vector;