import {createHttpClient, HiveHttpError} from '@hive-platform/http-client';
import {createAuth} from '@hive-platform/auth';

/** All calls go through the Hive BFF route `/api/routes/rozeh/**`; the session cookie and CSRF are handled by the SDK. */
const http = createHttpClient();
const auth = createAuth(http);
const BASE = '/api/routes/rozeh';

export type BookingStatus = 'REQUESTED' | 'ACCEPTED' | 'REJECTED' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED';
export type BookingAction = 'REQUEST' | 'ACCEPT' | 'REJECT' | 'ASSIGN_PROVIDER' | 'CONFIRM' | 'CANCEL' | 'COMPLETE';

export interface Catalog {
  categories: string[]; audiences: string[]; deliveryModes: string[]; confirmationBy: string[];
  providerRequiredForConfirmation: boolean; hostCancellationNoticeMinutes: number; requireVerifiedMobile: boolean; provisional: boolean;
}
export interface BookingSummary {
  id: string; trackingCode: string; status: BookingStatus; hostName: string; city: string; area?: string; category: string; audience?: string;
  startsAt: string; endsAt: string; timeZone: string; providerId?: string; providerName?: string; requestedAt: string; updatedAt: string;
}
export interface Page<T> {items: T[]; page: number; size: number; total: number}
export interface HistoryEntry {action: BookingAction; from?: BookingStatus; to: BookingStatus; actorRole: 'HOST' | 'COORDINATOR'; byViewer: boolean; detail?: string; at: string}
export interface BookingView {
  id: string; trackingCode: string; status: BookingStatus;
  host: {hostId: string; name: string; phone: string};
  location: {city: string; area?: string; address: string};
  details: {category: string; audience?: string; deliveryMode: string; approximateCapacity?: number; notes?: string};
  time: {startsAt: string; endsAt: string; timeZone: string};
  provider?: {id: string; displayName?: string};
  requestedAt: string; updatedAt: string; version: number; history: HistoryEntry[]; allowedActions: BookingAction[];
}
export interface Submission {
  clientRequestId: string; hostName: string; hostPhone: string; city: string; area?: string; address: string; category: string; audience?: string;
  deliveryMode: string; approximateCapacity?: number; notes?: string; startsAt: string; durationMinutes: number; timeZone: string;
}
export interface Provider {id: string; displayName: string; active: boolean; registeredAt: string; version: number}
export interface ProviderSlot {bookingId: string; trackingCode: string; startsAt: string; endsAt: string}

const query = (params: Record<string, string | number | string[] | undefined>) => {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === '') continue;
    if (Array.isArray(value)) value.forEach(v => search.append(key, v));
    else search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : '';
};

export const api = {
  catalog: () => http.get<Catalog>(`${BASE}/public/catalog`),
  submit: (body: Submission) => http.post<BookingView>(`${BASE}/my/bookings`, body),
  mine: (status: BookingStatus[] = [], page = 0) => http.get<Page<BookingSummary>>(`${BASE}/my/bookings${query({status, page, size: 20})}`),
  mineDetails: (id: string) => http.get<BookingView>(`${BASE}/my/bookings/${encodeURIComponent(id)}`),
  confirmMine: (id: string, expectedVersion: number) => http.post<BookingView>(`${BASE}/my/bookings/${encodeURIComponent(id)}/confirm`, {expectedVersion}),
  cancelMine: (id: string, expectedVersion: number, reason?: string) => http.post<BookingView>(`${BASE}/my/bookings/${encodeURIComponent(id)}/cancel`, {expectedVersion, reason}),
  coordination: (filters: {status?: BookingStatus[]; city?: string; page?: number}) => http.get<Page<BookingSummary>>(`${BASE}/coordination/bookings${query({status: filters.status, city: filters.city, page: filters.page ?? 0, size: 25})}`),
  coordinationDetails: (id: string) => http.get<BookingView>(`${BASE}/coordination/bookings/${encodeURIComponent(id)}`),
  transition: (id: string, action: 'accept' | 'reject' | 'confirm' | 'cancel' | 'complete', expectedVersion: number, reason?: string) =>
    http.post<BookingView>(`${BASE}/coordination/bookings/${encodeURIComponent(id)}/${action}`, {expectedVersion, reason}),
  assign: (id: string, expectedVersion: number, providerId: string) => http.post<BookingView>(`${BASE}/coordination/bookings/${encodeURIComponent(id)}/provider`, {expectedVersion, providerId}),
  providers: (includeInactive = true) => http.get<Provider[]>(`${BASE}/coordination/providers${query({includeInactive: String(includeInactive)})}`),
  registerProvider: (displayName: string) => http.post<Provider>(`${BASE}/coordination/providers`, {displayName}),
  updateProvider: (id: string, expectedVersion: number, change: {displayName?: string; active?: boolean}) =>
    http.put<Provider>(`${BASE}/coordination/providers/${encodeURIComponent(id)}`, {expectedVersion, ...change}),
  availability: (id: string, from: string, to: string) => http.get<ProviderSlot[]>(`${BASE}/coordination/providers/${encodeURIComponent(id)}/availability${query({from, to})}`),
};

/** Starts the Hive BFF login (Keycloak) and returns to a local path afterwards. */
export function login(returnUrl: string): void {
  auth.login(returnUrl);
}

export function errorCode(error: unknown): string {
  if (error instanceof HiveHttpError) return error.code;
  return 'NETWORK_FAILURE';
}

export function isUnauthenticated(error: unknown): boolean {
  return error instanceof HiveHttpError && error.status === 401;
}

export function uuid(): string {
  return globalThis.crypto?.randomUUID?.() ?? 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}
