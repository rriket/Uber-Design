import keycloak from './keycloak';

const BASE = process.env.REACT_APP_API_BASE_URL;

async function request(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (keycloak.token) headers.Authorization = `Bearer ${keycloak.token}`;
  const res = await fetch(`${BASE}${path}`, { ...options, headers });
  const text = await res.text();
  const body = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error(body?.message || `HTTP ${res.status}`);
  return body;
}

export const api = {
  // Fare & rides
  estimateFare: (payload) => request('/api/fares/estimate', { method: 'POST', body: JSON.stringify(payload) }),
  requestRide:  (fareId)  => request('/api/rides', { method: 'POST', body: JSON.stringify({ fareId }) }),
  scheduleRide: (fareId, scheduledFor) => request('/api/rides/schedule', { method: 'POST', body: JSON.stringify({ fareId, scheduledFor }) }),
  myScheduledRides: () => request('/api/rides/scheduled/mine'),
  getRide:      (rideId)  => request(`/api/rides/${rideId}`),
  myRides:      ()        => request('/api/rides/mine'),
  myDriverRides:()        => request('/api/rides/driver/mine'),
  acceptRide:   (rideId)  => request(`/api/rides/${rideId}/accept`, { method: 'PATCH' }),
  declineRide:  (rideId)  => request(`/api/rides/${rideId}/decline`, { method: 'PATCH' }),
  updateStatus: (rideId, status) => request(`/api/rides/${rideId}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }),

  // Location
  updateDriverLocation: (loc) => request('/api/locations/driver', { method: 'POST', body: JSON.stringify(loc) }),
  goOffline:    () => request('/api/locations/driver', { method: 'DELETE' }),
  getDriverLocation: (driverId) => request(`/api/locations/driver/${driverId}`),

  // Ratings
  submitRating: (payload) => request('/api/ratings', { method: 'POST', body: JSON.stringify(payload) }),
  ratingSummary: (userId, role = 'DRIVER') => request(`/api/ratings/user/${encodeURIComponent(userId)}/summary?role=${role}`),

  // Payments / earnings
  paymentForRide: (rideId) => request(`/api/payments/ride/${rideId}`),
  driverEarnings: (days = 7) => request(`/api/payments/driver/mine/earnings?days=${days}`),
};
