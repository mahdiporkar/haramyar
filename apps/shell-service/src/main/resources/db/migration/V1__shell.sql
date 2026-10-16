-- HaramYar application backend schema.

create table occasion (
  id           uuid        primary key,
  occasion_key text        not null unique,
  theme        text        not null check (theme in ('DEFAULT', 'CELEBRATION', 'MOURNING')),
  titles       jsonb       not null,
  subtitles    jsonb       not null default '{}',
  starts_at    timestamptz not null,
  ends_at      timestamptz not null,
  priority     integer     not null check (priority between 0 and 1000),
  status       text        not null check (status in ('DRAFT', 'SCHEDULED', 'ARCHIVED')),
  version      bigint      not null,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  constraint occasion_window check (ends_at > starts_at)
);

-- Users already enrolled in the default business role (idempotency of onboarding).
create table onboarded_user (
  hive_user_id uuid        primary key,
  role_key     text        not null,
  onboarded_at timestamptz not null default now()
);

create table notification (
  id           uuid        primary key,
  hive_user_id uuid        not null,
  kind         text        not null,
  subject_id   uuid        not null,
  reference    text,
  event_id     uuid        not null unique,
  occurred_at  timestamptz not null,
  read_at      timestamptz,
  created_at   timestamptz not null default now()
);
create index notification_user on notification (hive_user_id, occurred_at desc);
