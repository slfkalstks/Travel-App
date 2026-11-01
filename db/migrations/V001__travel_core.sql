-- PostgreSQL target schema for the next implementation stage.
-- This migration is NOT applied by the local JSON demo adapter.
CREATE TABLE traveler (
    id uuid PRIMARY KEY,
    display_name varchar(100) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE trip (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES traveler(id),
    title varchar(120) NOT NULL,
    destination varchar(120) NOT NULL,
    country varchar(120) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL CHECK (end_date >= start_date),
    travelers integer NOT NULL CHECK (travelers > 0),
    budget_krw bigint NOT NULL CHECK (budget_krw >= 0),
    destination_timezone varchar(64) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);
CREATE TABLE travel_event (
    id uuid PRIMARY KEY,
    trip_id uuid NOT NULL REFERENCES trip(id) ON DELETE CASCADE,
    date date NOT NULL,
    start_time time NOT NULL,
    end_time time NOT NULL CHECK (end_time > start_time),
    title varchar(120) NOT NULL,
    category varchar(16) NOT NULL CHECK (category IN ('flight','stay','food','place','transport')),
    location varchar(200) NOT NULL DEFAULT '',
    note text NOT NULL DEFAULT '',
    cost_krw bigint NOT NULL DEFAULT 0 CHECK (cost_krw >= 0),
    latitude double precision CHECK (latitude BETWEEN -90 AND 90),
    longitude double precision CHECK (longitude BETWEEN -180 AND 180),
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);
CREATE INDEX travel_event_trip_date_idx ON travel_event(trip_id, date, start_time);
CREATE TABLE checklist_task (
    id uuid PRIMARY KEY,
    trip_id uuid NOT NULL REFERENCES trip(id) ON DELETE CASCADE,
    title varchar(200) NOT NULL,
    category varchar(16) NOT NULL CHECK (category IN ('packing','booking')),
    done boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);
CREATE TABLE expense (
    id uuid PRIMARY KEY,
    trip_id uuid NOT NULL REFERENCES trip(id) ON DELETE CASCADE,
    title varchar(120) NOT NULL,
    category varchar(16) NOT NULL CHECK (category IN ('flight','stay','food','transport','shopping','other')),
    amount_krw bigint NOT NULL CHECK (amount_krw > 0),
    date date NOT NULL,
    method varchar(60) NOT NULL,
    source varchar(16) NOT NULL DEFAULT 'manual' CHECK (source IN ('manual','receipt')),
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);
CREATE INDEX expense_trip_date_idx ON expense(trip_id, date);
CREATE TABLE reservation (
    id uuid PRIMARY KEY,
    trip_id uuid NOT NULL REFERENCES trip(id) ON DELETE CASCADE,
    type varchar(16) NOT NULL,
    title varchar(200) NOT NULL,
    date date NOT NULL,
    time time NOT NULL,
    reference varchar(100) NOT NULL,
    detail text NOT NULL DEFAULT '',
    status varchar(30) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE favorite (
    id uuid PRIMARY KEY,
    trip_id uuid NOT NULL REFERENCES trip(id) ON DELETE CASCADE,
    title varchar(200) NOT NULL,
    category varchar(16) NOT NULL CHECK (category IN ('place','food','stay')),
    location varchar(200) NOT NULL,
    note text NOT NULL DEFAULT '',
    saved boolean NOT NULL DEFAULT true,
    latitude double precision CHECK (latitude BETWEEN -90 AND 90),
    longitude double precision CHECK (longitude BETWEEN -180 AND 180),
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);
CREATE TABLE journal (
    id uuid PRIMARY KEY,
    trip_id uuid NOT NULL REFERENCES trip(id) ON DELETE CASCADE,
    date date NOT NULL,
    title varchar(120) NOT NULL,
    body text NOT NULL,
    mood varchar(40) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz
);
