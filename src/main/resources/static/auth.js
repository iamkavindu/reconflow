// OAuth authorization code + S256 PKCE. Identity and role come only from /api/session.
// Access tokens stay in memory; sessionStorage holds only a short-lived pending handshake.
const pendingKey = 'reconflow.authorization';
let accessToken;
let expiresAt = 0;
let settings;
const encode = bytes => btoa(String.fromCharCode(...bytes)).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');
const random = () => encode(crypto.getRandomValues(new Uint8Array(32)));
export async function configure() {
  const response = await fetch('/ui/config', {credentials: 'omit', cache: 'no-store'});
  if (!response.ok) throw new Error('Sign-in configuration is unavailable.');
  settings = await response.json();
  const issuer = new URL(settings.issuer);
  if (issuer.username || issuer.password || issuer.search || issuer.hash ||
      !(issuer.protocol === 'https:' || (issuer.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(issuer.hostname)))) {
    throw new Error('Sign-in requires HTTPS or a local development issuer.');
  }
  settings.issuer = issuer.href.replace(/\/$/, '');
}
export async function signIn() {
  const verifier = random();
  const state = random();
  const redirect = location.origin + '/';
  sessionStorage.setItem(pendingKey, JSON.stringify({verifier, state, redirect, created: Date.now()}));
  const challenge = encode(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))));
  const url = new URL(settings.issuer + '/protocol/openid-connect/auth');
  url.search = new URLSearchParams({client_id: settings.clientId, response_type: 'code', redirect_uri: redirect,
    code_challenge_method: 'S256', code_challenge: challenge, state, prompt: 'login'});
  location.assign(url);
}
export async function finishSignIn() {
  const query = new URLSearchParams(location.search);
  if (!query.has('code') && !query.has('error')) return false;
  const stored = sessionStorage.getItem(pendingKey);
  sessionStorage.removeItem(pendingKey);
  history.replaceState(null, '', '/');
  const pending = stored ? JSON.parse(stored) : null;
  if (!pending || pending.state !== query.get('state') || pending.redirect !== location.origin + '/' ||
      Date.now() - pending.created > 300000 || pending.created > Date.now() || query.getAll('code').length !== 1 ||
      query.getAll('state').length !== 1 || query.get('iss') !== settings.issuer || query.has('error')) {
    throw new Error('Sign-in could not be verified. Start a new sign-in.');
  }
  const response = await fetch(settings.issuer + '/protocol/openid-connect/token', {method: 'POST', credentials: 'omit',
    headers: {'Content-Type': 'application/x-www-form-urlencoded'}, cache: 'no-store',
    body: new URLSearchParams({grant_type: 'authorization_code', client_id: settings.clientId,
      code: query.get('code'), redirect_uri: pending.redirect, code_verifier: pending.verifier})});
  if (!response.ok) throw new Error('Sign-in could not be completed. Start a new sign-in.');
  const token = await response.json();
  if (token.token_type?.toLowerCase() !== 'bearer' || typeof token.access_token !== 'string' ||
      !Number.isFinite(token.expires_in) || token.expires_in <= 0) throw new Error('Invalid sign-in response.');
  accessToken = token.access_token;
  expiresAt = Date.now() + token.expires_in * 1000;
  return true;
}
export function bearer() {
  if (!accessToken || Date.now() >= expiresAt) {
    signOut();
    throw new Error('Your session has ended. Sign in to continue.');
  }
  return accessToken;
}
export function signOut() {
  accessToken = undefined;
  expiresAt = 0;
  sessionStorage.removeItem(pendingKey);
}
