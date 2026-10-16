-- Rozeh Booking schema (owned exclusively by the rozeh-booking service).
create extension if not exists btree_gist;

create table provider (
  id            uuid        primary key,
  display_name  text        not null check (char_length(display_name) between 2 and 120),
  active        boolean     not null,
  registered_at timestamptz not null,
  updated_at    timestamptz not null,
  version       bigint      not null
);

create table booking (
  id                   uuid        primary key,
  tracking_code        text        not null unique,
  host_id              uuid        not null,
  client_request_id    uuid        not null,
  host_name            text        not null,
  host_phone           text        not null,
  city                 text        not null,
  area                 text,
  address              text        not null,
  category             text        not null,
  audience             text,
  delivery_mode        text        not null,
  approximate_capacity integer     check (approximate_capacity between 1 and 5000),
  notes                text,
  starts_at            timestamptz not null,
  ends_at              timestamptz not null,
  time_zone            text        not null,
  status               text        not null check (status in ('REQUESTED', 'ACCEPTED', 'REJECTED', 'CONFIRMED', 'CANCELLED', 'COMPLETED')),
  provider_id          uuid        references provider (id),
  requested_at         timestamptz not null,
  updated_at           timestamptz not null,
  version              bigint      not null,
  constraint booking_client_request_unique unique (host_id, client_request_id),
  constraint booking_end_after_start check (ends_at > starts_at),
  -- Concurrency guarantee: a provider's confirmed ceremonies can never overlap, whatever the interleaving of
  -- transactions. Half-open ranges let back-to-back slots coexist.
  constraint booking_provider_confirmed_no_overlap exclude using gist (
    provider_id with =,
    tstzrange(starts_at, ends_at, '[)') with &&
  ) where (status = 'CONFIRMED' and provider_id is not null)
);
create index booking_host_requested on booking (host_id, requested_at desc);
create index booking_status_start on booking (status, starts_at);

-- Append-only audit trail of every meaningful change.
create table booking_history (
  id          bigserial   primary key,
  booking_id  uuid        not null references booking (id),
  sequence_no integer     not null,
  action      text        not null,
  from_status text,
  to_status   text        not null,
  actor_id    uuid        not null,
  actor_role  text        not null,
  detail      text,
  occurred_at timestamptz not null,
  constraint booking_history_sequence unique (booking_id, sequence_no)
);

create or replace function booking_history_append_only() returns trigger language plpgsql as $$
begin
  raise exception 'booking_history is append-only';
end $$;
create trigger booking_history_no_update before update or delete on booking_history
  for each row execute function booking_history_append_only();
