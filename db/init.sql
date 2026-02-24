CREATE TABLE IF NOT EXISTS products (
  id   SERIAL PRIMARY KEY,
  name TEXT NOT NULL UNIQUE
);

INSERT INTO products (name) VALUES
  ('apple'),
  ('banana'),
  ('coffee')
ON CONFLICT (name) DO NOTHING;
