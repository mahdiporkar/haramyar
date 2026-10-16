-- Solution infrastructure tables shared by every HaramYar service schema (owned by that service's database).

-- Pinned (issuer, subject) -> canonical Hive user binding; see IdentityBindings.
create table hive_identity_binding (
  issuer       text        not null,
  subject      text        not null,
  hive_user_id uuid        not null,
  created_at   timestamptz not null default now(),
  primary key (issuer, subject)
);

-- Transactional outbox: written in the same transaction as the aggregate change, published asynchronously.
create table outbox_event (
  id              uuid        primary key,
  event_type      text        not null,
  aggregate_type  text        not null,
  aggregate_id    text        not null,
  occurred_at     timestamptz not null,
  payload         jsonb       not null,
  status          text        not null default 'PENDING' check (status in ('PENDING', 'IN_FLIGHT', 'PUBLISHED', 'FAILED')),
  attempts        integer     not null default 0,
  next_attempt_at timestamptz not null default now(),
  claimed_by      text,
  claimed_until   timestamptz,
  last_error      text,
  created_at      timestamptz not null default now(),
  published_at    timestamptz
);
create index outbox_event_due on outbox_event (created_at) where status in ('PENDING', 'IN_FLIGHT');

-- Inbox: one row per (consumer, event) processed; committed together with the consumer's business change.
create table inbox_message (
  consumer     text        not null,
  event_id     uuid        not null,
  event_type   text        not null,
  processed_at timestamptz not null default now(),
  primary key (consumer, event_id)
);
