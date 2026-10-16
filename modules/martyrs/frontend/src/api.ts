import {createHttpClient, HiveHttpError} from '@hive-platform/http-client';
import {createAuth} from '@hive-platform/auth';

/** Calls go through the Hive BFF route `/api/routes/martyrs/**` (PUBLIC operations for readers, AUTHENTICATED for editors). */
const http = createHttpClient();
const auth = createAuth(http);
const BASE = '/api/routes/martyrs';

export type Calendar = 'SOLAR_HIJRI' | 'LUNAR_HIJRI' | 'GREGORIAN';
export type Status = 'DRAFT' | 'IN_REVIEW' | 'PUBLISHED' | 'ARCHIVED';
export interface HistoricalDate {year: number; month?: number; day?: number; calendar: Calendar}
export interface LifeEvent {date?: HistoricalDate; place?: string}
export interface Media {mediaId: string; caption?: string; credit?: string}
export interface Source {id: string; kind: 'BOOK' | 'ARCHIVE' | 'INTERVIEW' | 'ARTICLE' | 'WEBSITE' | 'OTHER'; title: string; author?: string; publisher?: string; year?: number; url?: string; note?: string}
export interface Story {id: string; title: string; body: string; sourceIds: string[]}
export interface PublicProfile {
  slug: string; fullName: string; honorific?: string; contentLocale: 'fa' | 'ar' | 'en'; shortDescription?: string; biography?: string;
  birth?: LifeEvent; martyrdom?: LifeEvent; portrait?: Media; gallery: Media[]; sources: Source[]; stories: Story[];
  seo?: {title?: string; description?: string}; firstPublishedAt: string; updatedAt: string;
}
export interface PublicSummary {
  slug: string; fullName: string; honorific?: string; shortDescription?: string; portraitMediaId?: string; birthYear?: number; birthCalendar?: Calendar;
  martyrdomYear?: number; martyrdomCalendar?: Calendar; contentLocale: string; publishedAt: string;
}
export interface Page<T> {items: T[]; page: number; size: number; total: number}
export interface Content {
  name: {fullName: string; honorific?: string}; contentLocale: 'fa' | 'ar' | 'en'; shortDescription?: string; biography: {text?: string};
  birth?: LifeEvent; martyrdom?: LifeEvent; seo: {title?: string; description?: string};
}
export interface HistoryEntry {operation: string; from?: Status; to: Status; role: 'EDITOR' | 'REVIEWER'; byViewer: boolean; note?: string; at: string}
export interface EditorialProfile {
  id: string; slug: string; status: Status; content: Content; portrait?: Media; gallery: Media[]; sources: Source[]; stories: Story[];
  submittedByViewer: boolean; createdAt: string; updatedAt: string; firstPublishedAt?: string; publishedAt?: string; version: number; history: HistoryEntry[];
}
export interface EditorialSummary {id: string; slug: string; fullName: string; status: Status; updatedAt: string; version: number}
export interface ContentBody {
  fullName: string; honorific?: string; contentLocale: string; shortDescription?: string; biography?: string;
  birth?: {date?: HistoricalDate; place?: string}; martyrdom?: {date?: HistoricalDate; place?: string}; seoTitle?: string; seoDescription?: string;
}

const q = (params: Record<string, string | number | string[] | undefined>) => {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === '') continue;
    if (Array.isArray(value)) value.forEach(v => search.append(key, v)); else search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : '';
};
const id = (value: string) => encodeURIComponent(value);

export const publicMediaUrl = (mediaId: string) => `${BASE}/public/media/${id(mediaId)}`;
export const previewMediaUrl = (mediaId: string) => `${BASE}/editorial/media/${id(mediaId)}`;

export const api = {
  search: (query: string, page = 0, size = 20) => http.get<Page<PublicSummary>>(`${BASE}/public/profiles${q({q: query.trim() || undefined, page, size})}`),
  profile: (slug: string) => http.get<PublicProfile>(`${BASE}/public/profiles/${id(slug)}`),
  editorialList: (status: Status[], query: string, page = 0) => http.get<Page<EditorialSummary>>(`${BASE}/editorial/profiles${q({status, q: query.trim() || undefined, page})}`),
  create: (slug: string, content: ContentBody) => http.post<EditorialProfile>(`${BASE}/editorial/profiles`, {slug, content}),
  get: (profileId: string) => http.get<EditorialProfile>(`${BASE}/editorial/profiles/${id(profileId)}`),
  revise: (profileId: string, expectedVersion: number, slug: string, content: ContentBody) => http.put<EditorialProfile>(`${BASE}/editorial/profiles/${id(profileId)}`, {expectedVersion, slug, content}),
  addSource: (profileId: string, expectedVersion: number, source: Omit<Source, 'id'>) => http.post<EditorialProfile>(`${BASE}/editorial/profiles/${id(profileId)}/sources`, {expectedVersion, ...source}),
  removeSource: (profileId: string, expectedVersion: number, sourceId: string) => http.delete<EditorialProfile>(`${BASE}/editorial/profiles/${id(profileId)}/sources/${id(sourceId)}${q({expectedVersion})}`),
  addStory: (profileId: string, expectedVersion: number, story: Omit<Story, 'id'>) => http.post<EditorialProfile>(`${BASE}/editorial/profiles/${id(profileId)}/stories`, {expectedVersion, ...story}),
  updateStory: (profileId: string, expectedVersion: number, story: Story) => http.put<EditorialProfile>(`${BASE}/editorial/profiles/${id(profileId)}/stories/${id(story.id)}`, {expectedVersion, title: story.title, body: story.body, sourceIds: story.sourceIds}),
  removeStory: (profileId: string, expectedVersion: number, storyId: string) => http.delete<EditorialProfile>(`${BASE}/editorial/profiles/${id(profileId)}/stories/${id(storyId)}${q({expectedVersion})}`),
  upload: (profileId: string, expectedVersion: number, file: File, usage: 'PORTRAIT' | 'GALLERY', caption?: string, credit?: string) => {
    const form = new FormData();
    form.append('file', file);
    return http.post<EditorialProfile>(`${BASE}/editorial/profiles/${id(profileId)}/media${q({usage, caption, credit, expectedVersion})}`, form);
  },
  removeMedia: (profileId: string, expectedVersion: number, mediaId: string) => http.delete<EditorialProfile>(`${BASE}/editorial/profiles/${id(profileId)}/media/${id(mediaId)}${q({expectedVersion})}`),
  transition: (profileId: string, action: 'submit' | 'request-changes' | 'publish' | 'archive' | 'reopen', expectedVersion: number, note?: string) =>
    http.post<EditorialProfile>(`${BASE}/editorial/profiles/${id(profileId)}/${action}`, {expectedVersion, note}),
};

export function login(returnUrl: string): void { auth.login(returnUrl); }
export function errorCode(error: unknown): string { return error instanceof HiveHttpError ? error.code : 'NETWORK_FAILURE'; }
export function isUnauthenticated(error: unknown): boolean { return error instanceof HiveHttpError && error.status === 401; }
export function isNotFound(error: unknown): boolean { return error instanceof HiveHttpError && error.status === 404; }
