-- Minimal schema for zwloader (no custom types/extensions; UUID defaults omitted)

-- positions
CREATE TABLE IF NOT EXISTS positions (
    position_id UUID PRIMARY KEY,
    position_name TEXT NOT NULL,
    access_level  INTEGER,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_positions_name_lower ON positions (lower(position_name));

-- personnel
CREATE TABLE IF NOT EXISTS personnel (
    person_id          UUID PRIMARY KEY,
    last_name          TEXT,
    first_name         TEXT,
    middle_name        TEXT,
    full_name          TEXT,
    date_of_birth      DATE,
    position_id        UUID REFERENCES positions(position_id),
    phone              TEXT,
    compreface_subject TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_personnel_name_lower ON personnel (lower(last_name), lower(first_name));
CREATE INDEX IF NOT EXISTS idx_personnel_full_name_lower ON personnel (lower(full_name));

-- cards
CREATE TABLE IF NOT EXISTS cards (
    uid TEXT PRIMARY KEY,
    person_id UUID NOT NULL REFERENCES personnel(person_id) ON DELETE CASCADE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_cards_person_id ON cards(person_id);

-- devices
CREATE TABLE IF NOT EXISTS devices (
    device_id TEXT PRIMARY KEY,
    kind TEXT,
    location TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- cameras
CREATE TABLE IF NOT EXISTS cameras (
    camera_id   TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    rtsp_url    TEXT NOT NULL,
    location    TEXT,
    device_id   TEXT REFERENCES devices(device_id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- guests
CREATE TABLE IF NOT EXISTS guests (
    guest_id      UUID PRIMARY KEY,
    last_name     TEXT,
    first_name    TEXT,
    middle_name   TEXT,
    full_name     TEXT,
    phone         TEXT,
    company       TEXT,
    document      TEXT,
    notes         TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- guest_visits
CREATE TABLE IF NOT EXISTS guest_visits (
    visit_id       BIGSERIAL PRIMARY KEY,
    guest_id       UUID NOT NULL REFERENCES guests(guest_id) ON DELETE CASCADE,
    host_person_id UUID NOT NULL REFERENCES personnel(person_id),
    planned_from   TIMESTAMPTZ,
    planned_to     TIMESTAMPTZ,
    reason         TEXT,
    status         TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- events (direction/source as TEXT)
CREATE TABLE IF NOT EXISTS events (
    event_id     BIGSERIAL PRIMARY KEY,
    uid          TEXT REFERENCES cards(uid),
    person_id    UUID REFERENCES personnel(person_id),
    face_name    TEXT,
    device_id    TEXT REFERENCES devices(device_id),
    direction    TEXT NOT NULL,
    source       TEXT NOT NULL,
    meta         JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (uid IS NOT NULL OR face_name IS NOT NULL OR person_id IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_events_created_at ON events(created_at);
CREATE INDEX IF NOT EXISTS idx_events_person_id  ON events(person_id);
CREATE INDEX IF NOT EXISTS idx_events_uid        ON events(uid);
CREATE INDEX IF NOT EXISTS idx_events_device_id  ON events(device_id);

-- roles and users
CREATE TABLE IF NOT EXISTS roles (
    role_id   SERIAL PRIMARY KEY,
    name      TEXT UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS shuffleport_users (
    user_id    SERIAL PRIMARY KEY,
    email      TEXT UNIQUE NOT NULL,
    password   TEXT NOT NULL,
    role_id    INTEGER NOT NULL REFERENCES roles(role_id),
    person_id  UUID REFERENCES personnel(person_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

