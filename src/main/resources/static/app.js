import {configure, signIn, finishSignIn, bearer, signOut} from './auth.js';

const content = document.querySelector('#content');
const notice = document.querySelector('#notice');
const navigation = document.querySelector('#navigation');
const sessionAction = document.querySelector('#session-action');
let actor;
let generation = 0;
const uid = value => value?.value ?? value;
const writable = () => actor && ['ADMIN', 'ANALYST'].includes(actor.role);
const human = value => String(value ?? '—').replaceAll('_', ' ');
const time = value => value ? new Date(value).toLocaleString() : '—';
function el(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  if (className) node.className = className;
  return node;
}
function button(text, run, secondary = false) {
  const node = el('button', text, secondary ? 'secondary' : '');
  node.type = 'button';
  node.addEventListener('click', () => task(run, node));
  return node;
}
function message(text = '') { notice.textContent = text; }
async function task(run, control) {
  const version = generation;
  if (control) control.disabled = true;
  message();
  try { await run(); } catch (error) { if (version === generation) message(error.message); }
  finally { if (control?.isConnected) control.disabled = control.dataset.complete === 'true'; }
}
function signedOut() {
  generation++;
  signOut();
  actor = undefined;
  document.querySelector('#identity').replaceChildren();
  navigation.hidden = true;
  sessionAction.textContent = 'Sign in';
  content.replaceChildren(el('h1', 'Payment operations'), el('p', 'Sign in to access your organization’s workspace.'));
}
async function api(path, method = 'GET', body) {
  let token;
  try { token = bearer(); } catch (error) { signedOut(); message(error.message); throw error; }
  const version = generation;
  let response;
  try {
    response = await fetch(path, {method, credentials: 'omit', cache: 'no-store', headers: {
      Authorization: 'Bearer ' + token, ...(body === undefined ? {} : {'Content-Type': 'application/json'})},
      body: body === undefined ? undefined : JSON.stringify(body)});
  } catch {
    throw new Error(method === 'GET' ? 'Connection unavailable. Try refreshing.' :
      'Delivery could not be confirmed. Inspect the records before submitting again; keep the same provider event identity.');
  }
  if (version !== generation) throw new Error('The workspace changed.');
  if (response.status === 401) { signedOut(); message('Your session has ended. Sign in to continue.'); throw new Error('Sign in required.'); }
  if (!response.ok) {
    if (response.status === 403) throw new Error('Your role does not allow this action.');
    if (response.status === 404) throw new Error('This record is unavailable in your organization.');
    const detail = await response.json().catch(() => ({}));
    throw new Error(detail.detail || detail.title || 'The operation could not be completed.');
  }
  // Preserve exact monetary JSON lexemes instead of rounding them through binary floats.
  const moneyKeys = new Set(['amount', 'grossAmount', 'feeAmount', 'netAmount']);
  return JSON.parse(await response.text(), (key, value, context) =>
    typeof value === 'number' && moneyKeys.has(key) ? context.source : value);
}
function badge(value) { return el('span', human(value), 'badge ' + value); }
function fields(record, labels) {
  const list = el('dl');
  for (const [key, label] of Object.entries(labels)) {
    let value = record[key];
    if (value?.value) value = value.value;
    else if (value && typeof value === 'object') value = JSON.stringify(value, null, 2);
    list.append(el('dt', label), el('dd', String(value ?? '—')));
  }
  return list;
}
function section(title, ...nodes) {
  const box = el('section', undefined, 'card');
  box.append(el('h2', title), ...nodes);
  return box;
}
function table(headers, rows) {
  const wrap = el('div', undefined, 'table-wrap');
  const result = el('table');
  const head = el('thead');
  const titles = el('tr');
  headers.forEach(label => { const th = el('th', label); th.scope = 'col'; titles.append(th); });
  head.append(titles);
  const body = el('tbody');
  for (const values of rows) {
    const row = el('tr');
    values.forEach(value => { const td = el('td'); td.append(value instanceof Node ? value : document.createTextNode(String(value ?? '—'))); row.append(td); });
    body.append(row);
  }
  result.append(head, body); wrap.append(result); return wrap;
}
function input(form, name, label, options = {}) {
  const field = el('label', label);
  let control;
  if (options.values) {
    control = el('select');
    options.values.forEach(value => { const option = el('option', human(value)); option.value = value; control.append(option); });
  } else control = el(options.multiline ? 'textarea' : 'input');
  control.name = name;
  if (options.type) control.type = options.type;
  if (options.required) control.required = true;
  if (options.max) control.maxLength = options.max;
  if (options.value !== undefined) control.value = options.value;
  if (options.pattern) control.pattern = options.pattern;
  if (options.placeholder) control.placeholder = options.placeholder;
  field.append(control); form.append(field); return control;
}
function formAction(form, label, run) {
  const submit = el('button', label); submit.type = 'submit'; form.append(submit);
  form.addEventListener('submit', event => { event.preventDefault(); task(() => run(Object.fromEntries(new FormData(form))), submit); });
  return submit;
}
function title(text, subtitle) {
  content.replaceChildren(el('p', 'PAYMENT OPERATIONS', 'eyebrow'), el('h1', text));
  if (subtitle) content.append(el('p', subtitle, 'muted'));
}
function audit(type, id) {
  const box = section('Audit history');
  let before;
  const rows = [];
  const load = button('Load audit history', async () => {
    const records = await api(`/api/audit/${type}/${id}?limit=25` + (before ? '&before=' + before : ''));
    rows.push(...records);
    box.replaceChildren(el('h2', 'Audit history'), table(['Time', 'Action', 'Actor', 'Details'], rows.map(row =>
      [time(row.occurredAt), human(row.action), row.actor, 'Correlation ' + row.correlationId])), load);
    before = records.at(-1)?.sequence;
    load.textContent = records.length === 25 ? 'Load earlier entries' : 'All available entries loaded';
    load.disabled = records.length < 25;
    if (records.length < 25) load.dataset.complete = 'true';
  }, true);
  box.append(load); return box;
}
const definitions = {
  transactions: {title: 'Internal transactions', id: 'transactionId', filters: ['transactionId', 'reference'], status: [],
    columns: ['Reference', 'Amount', 'Type', 'Occurred', 'Record'],
    row: item => [item.businessReference, item.currency + ' ' + item.amount, item.type, time(item.occurredAt)]},
  'provider-transactions': {title: 'Provider records', id: 'providerTransactionId', filters: ['providerTransactionId', 'providerReference', 'reference'], status: [],
    columns: ['Provider reference', 'Reference', 'Amount', 'Status', 'Record'],
    row: item => [item.providerTransactionReference, item.businessReference, item.currency + ' ' + item.amount, badge(item.status)]},
  reconciliations: {title: 'Reconciliations', id: 'reconciliationId', filters: ['reconciliationId', 'transactionId', 'providerTransactionId', 'reference'], status: ['', 'PENDING', 'MATCHED', 'REVIEW_REQUIRED', 'UNMATCHED'],
    columns: ['Reference', 'Status', 'Started', 'Discrepancies', 'Record'],
    row: item => [item.businessReference, badge(item.status), time(item.startedAt), item.discrepancies.map(x => human(x.type)).join(', ') || 'None']},
  cases: {title: 'Exception cases', id: 'caseId', filters: ['caseId', 'reconciliationId'], status: ['', 'OPEN', 'UNDER_REVIEW', 'RESOLVED'],
    columns: ['Case', 'Status', 'Opened', 'Updated', 'Record'],
    row: item => [el('span', uid(item.caseId), 'id'), badge(item.status), time(item.openedAt), time(item.updatedAt)]}
};
async function search(view, version) {
  const definition = definitions[view];
  title(definition.title, 'Search within your organization. Dates use your local timezone.');
  const form = el('form', undefined, 'toolbar');
  const labels = {transactionId: 'Internal transaction ID', providerTransactionId: 'Provider record ID', providerReference: 'Provider reference',
    reconciliationId: 'Reconciliation ID', caseId: 'Case ID', reference: 'Order / business reference'};
  for (const name of definition.filters) input(form, name, labels[name]);
  if (definition.status.length) input(form, 'status', 'Status (blank = all)', {values: definition.status});
  input(form, 'from', 'From', {type: 'datetime-local'});
  input(form, 'to', 'To (exclusive)', {type: 'datetime-local'});
  const result = el('div');
  const pager = el('div', undefined, 'actions');
  let offset = 0;
  let filters = {};
  let loading = false;
  const previous = button('Previous', () => load(Math.max(0, offset - 25)), true);
  const next = button('Next', () => load(offset + 25), true);
  const page = el('span', '', 'muted');
  pager.append(previous, page, next);
  async function load(start) {
    if (loading) return;
    loading = true; previous.disabled = next.disabled = submit.disabled = true;
    result.replaceChildren(el('p', 'Searching…', 'muted'));
    try {
      const rows = await api('/api/' + view + '?' + new URLSearchParams({...filters, limit: 25, offset: start}));
      if (version !== generation) return;
      offset = start;
      result.replaceChildren(rows.length ? table(definition.columns, rows.map(item => [...definition.row(item),
        button('Open', () => openRecord(view, uid(item[definition.id])), true)])) : el('p', 'No records match these filters.', 'card'));
      page.textContent = `Page ${Math.floor(offset / 25) + 1} · ${rows.length} records`;
      previous.disabled = offset === 0; next.disabled = rows.length < 25;
      previous.dataset.complete = String(offset === 0); next.dataset.complete = String(rows.length < 25);
    } finally { loading = false; submit.disabled = false; }
  }
  const submit = formAction(form, 'Search', data => {
    if (loading) return;
    filters = Object.fromEntries(Object.entries(data).filter(([, value]) => value.trim()).map(([key, value]) =>
      [key, ['from', 'to'].includes(key) ? new Date(value).toISOString() : value.trim()]));
    return load(0);
  });
  content.append(form, result, pager);
  await load(0);
}
async function overview(version) {
  const counts = await api('/api/dashboard');
  if (version !== generation) return;
  title('Reconciliation overview', 'Lifetime totals for your organization. Refresh to see asynchronous processing progress.');
  const metrics = el('div', undefined, 'metrics');
  for (const [key, label] of Object.entries({transactionsProcessed: 'Transactions processed', matched: 'Successfully reconciled', reviewRequired: 'Review required',
    unmatched: 'Unmatched', openCases: 'Open cases', resolvedCases: 'Resolved cases'})) {
    const card = el('div', undefined, 'card metric'); card.append(el('strong', counts[key]), el('span', label)); metrics.append(card);
  }
  content.append(metrics, button('Refresh overview', () => navigate('dashboard'), true));
}
async function openRecord(view, id) {
  const version = ++generation;
  message();
  try {
  const record = await api(`/api/${view}/${id}`);
  if (version !== generation) return;
  title(view === 'cases' ? 'Exception case' : human(view), id);
  content.append(button('Back to ' + definitions[view].title.toLowerCase(), () => navigate(view), true));
  if (view === 'cases') return await caseDetail(record, version);
  if (view === 'reconciliations') return await reconciliationDetail(record, version);
  content.append(section('Source record', fields(record, {businessReference: 'Business reference', provider: 'Provider', providerTransactionReference: 'Provider reference',
    type: 'Type', amount: 'Amount', currency: 'Currency', status: 'Status', occurredAt: 'Occurred at', recordedAt: 'Recorded at'})));
  content.append(button('View reconciliation', async () => {
    const result = await api(`/api/${view}/${id}/reconciliation`);
    return openRecord('reconciliations', uid(result.reconciliationId));
  }, true));
  if (view === 'provider-transactions') {
    const settlements = await api(`/api/provider-transactions/${id}/settlements`);
    if (version !== generation) return;
    content.append(settlementPanel(settlements));
  }
  content.append(audit(view === 'transactions' ? 'TRANSACTION' : 'PROVIDER_TRANSACTION', id));
  } catch (error) { if (version === generation) message(error.message); }
}
function settlementPanel(records) {
  return section('Settlement evidence', ...(records.length ? records.map(record => fields(record, {
    providerSettlementReference: 'Settlement reference', currency: 'Currency', grossAmount: 'Gross', feeAmount: 'Reported fee', netAmount: 'Net', lines: 'Source lines'})) : [el('p', 'No settlement evidence recorded.') ]));
}
async function reconciliationDetail(record, version) {
  content.append(section('Deterministic determination', badge(record.status), fields(record, {businessReference: 'Business reference', transactionId: 'Internal transaction ID',
    providerTransactionId: 'Matched provider record', providerOriginId: 'Origin provider record', startedAt: 'Started at', expiresAt: 'Matching deadline', decidedAt: 'Decided at',
    comparedProviderTransactionIds: 'Compared provider records', ruleEvaluations: 'Rule evaluations', discrepancies: 'Discrepancies'})));
  const actions = el('div', undefined, 'actions');
  if (record.transactionId) actions.append(button('Open internal evidence', () => openRecord('transactions', uid(record.transactionId)), true));
  const providerIds = new Set([record.providerTransactionId, record.providerOriginId, ...record.comparedProviderTransactionIds].filter(Boolean).map(uid));
  providerIds.forEach(id => actions.append(button('Open provider ' + id, () => openRecord('provider-transactions', id), true)));
  if (['REVIEW_REQUIRED', 'UNMATCHED'].includes(record.status)) actions.append(button('Open exception case', async () => {
    const item = await api(`/api/reconciliations/${uid(record.reconciliationId)}/case`);
    return openRecord('cases', uid(item.caseId));
  }));
  content.append(actions);
  const settlements = await api(`/api/reconciliations/${uid(record.reconciliationId)}/settlements`);
  if (version !== generation) return;
  content.append(settlementPanel(settlements), audit('RECONCILIATION', uid(record.reconciliationId)));
}
async function caseDetail(record, version) {
  const id = uid(record.caseId);
  content.append(section('Case status', badge(record.status), fields(record, {caseId: 'Case ID', reconciliationId: 'Reconciliation ID', openedAt: 'Opened at', updatedAt: 'Updated at'})));
  const actions = el('div', undefined, 'actions');
  actions.append(button('View financial evidence', () => openRecord('reconciliations', uid(record.reconciliationId)), true),
    button('Refresh case', () => openRecord('cases', id), true));
  if (writable() && record.status !== 'RESOLVED') {
    if (record.status === 'OPEN') actions.append(button('Start review', async () => { await api(`/api/cases/${id}/review`, 'POST'); return openRecord('cases', id); }));
    actions.append(button('Request AI investigation', async () => {
      await api(`/api/cases/${id}/investigations`, 'POST'); await openRecord('cases', id);
      message('Investigation requested. Refresh this case to view progress.');
    }));
  }
  content.append(actions, section('Notes', ...(record.notes.length ? record.notes.map(note => {
    const node = el('article'); node.append(el('p', note.text), el('p', `${note.addedBy} · ${time(note.addedAt)}`, 'muted')); return node;
  }) : [el('p', 'No notes yet.')])));
  if (record.resolution) content.append(section('Human resolution', fields(record.resolution, {type: 'Resolution', reason: 'Reason', resolvedBy: 'Resolved by', resolvedAt: 'Resolved at', evidenceReferences: 'Evidence references'})));
  if (writable() && record.status !== 'RESOLVED') {
    const note = el('form', undefined, 'card'); note.append(el('h2', 'Add an investigation note'));
    input(note, 'text', 'Note', {multiline: true, required: true, max: 4000});
    formAction(note, 'Add note', async data => { await api(`/api/cases/${id}/notes`, 'POST', data); return openRecord('cases', id); });
    const resolve = el('form', undefined, 'card'); resolve.append(el('h2', 'Resolve this case'), el('p', 'Choose your conclusion after reviewing the evidence. This is a final human decision; resolved cases cannot be reopened.', 'muted'));
    input(resolve, 'type', 'Resolution type', {values: ['MANUAL_REVIEW_COMPLETED', 'MATCH_CONFIRMED', 'ACCEPTED_FEE_VARIANCE', 'DUPLICATE_CONFIRMED', 'PROVIDER_RECORD_ERROR', 'INTERNAL_RECORD_ERROR']});
    input(resolve, 'reason', 'Reason for resolution', {multiline: true, required: true, max: 4000});
    input(resolve, 'evidenceReferences', 'Supporting evidence references (one per line)', {multiline: true, placeholder: 'investigation:<id>'});
    const confirmLabel = el('label'); const confirmation = el('input'); confirmation.type = 'checkbox'; confirmation.required = true;
    confirmLabel.append(confirmation, document.createTextNode('I reviewed the evidence and confirm this final resolution.')); resolve.append(confirmLabel);
    formAction(resolve, 'Confirm resolution', async data => {
      await api(`/api/cases/${id}/resolution`, 'POST', {...data, evidenceReferences: data.evidenceReferences.split('\n').map(x => x.trim()).filter(Boolean)});
      return openRecord('cases', id);
    });
    content.append(note, resolve);
  }
  const investigations = await api(`/api/cases/${id}/investigations`);
  if (version !== generation) return;
  const history = section('AI investigations', el('p', 'Advisory findings only. An investigation does not resolve the case.', 'muted'));
  for (const item of investigations) {
    const box = el('article');
    box.append(el('h3', uid(item.investigationId)), badge(item.status), el('p', `Requested ${time(item.requestedAt)}`, 'muted'));
    if (item.failureReason) box.append(el('p', item.failureReason));
    if (item.result) {
      box.append(fields(item.result, {summary: 'Summary', likelyCause: 'Likely cause', suggestedResolution: 'Suggested resolution', confidence: 'Model confidence (not certainty)', limitations: 'Limitations', judgment: 'Screening result'}));
      for (const evidence of item.result.evidence) {
        box.append(el('p', evidence.explanation), el('p', evidence.reference, 'id'));
        const match = /^document-chunk:([0-9a-f-]{36})$/i.exec(evidence.reference);
        if (match) box.append(button('Read cited passage', async () => {
          const passage = await api('/api/knowledge/passages/' + match[1]);
          box.append(section('Cited documentation', fields(passage, {documentId: 'Document ID', content: 'Passage', kind: 'Kind', title: 'Title'})));
        }, true));
      }
    }
    box.append(audit('INVESTIGATION', uid(item.investigationId))); history.append(box);
  }
  if (!investigations.length) history.append(el('p', 'No investigations requested.'));
  content.append(history, audit('CASE', id));
}
function activity() {
  title('Record payment activity', 'Financial source records are immutable. Use the demo runner to generate all eight scenarios.');
  for (const external of [false, true]) {
    const form = el('form', undefined, 'card'); form.append(el('h2', external ? 'Simulate provider event' : 'Record internal transaction'));
    const grid = el('div', undefined, 'form-grid');
    input(grid, 'businessReference', 'Business reference', {required: true, max: 100});
    if (external) {
      input(grid, 'providerEventId', 'Provider event ID', {required: true, max: 200});
      input(grid, 'providerTransactionReference', 'Provider transaction reference', {required: true, max: 200});
      input(grid, 'status', 'Provider status', {values: ['SUCCEEDED', 'FAILED']});
    }
    input(grid, 'type', 'Transaction type', {values: ['PAYMENT', 'REFUND']});
    input(grid, 'amount', 'Amount', {required: true, pattern: '[0-9]+(\\.[0-9]{1,4})?', value: '100.00'});
    input(grid, 'currency', 'Currency', {required: true, pattern: '[A-Z]{3}', max: 3, value: 'USD'});
    input(grid, 'occurredAt', 'Occurred at (local time)', {type: 'datetime-local', required: true}); form.append(grid);
    if (external) form.append(el('p', 'Repeat the same source event ID and reference when retrying a delivery. Broker acceptance is followed by asynchronous ingestion.', 'muted'));
    formAction(form, external ? 'Send provider event' : 'Record transaction', async data => {
      const result = await api(external ? '/api/provider-events/simulate' : '/api/transactions', 'POST', {...data, occurredAt: new Date(data.occurredAt).toISOString()});
      message(external ? 'Provider event accepted. Search provider records and reconciliations to observe processing.' : 'Internal transaction recorded: ' + uid(result.transactionId));
    }); content.append(form);
  }
}
function knowledge() {
  title('Investigation documentation', 'Import tenant-scoped policies and provider documentation for evidence retrieval. AI indexing must be configured.');
  const form = el('form', undefined, 'card');
  input(form, 'kind', 'Document kind', {values: ['POLICY', 'PROVIDER_DOCUMENTATION', 'PAST_CASE']});
  input(form, 'title', 'Title', {required: true, max: 200});
  input(form, 'content', 'Document content', {multiline: true, required: true, max: 20000});
  const result = el('div');
  formAction(form, 'Import documentation', async data => {
    const record = await api('/api/knowledge/documents', 'POST', data);
    result.replaceChildren(section('Imported document', fields(record, {id: 'Document ID', title: 'Title', kind: 'Kind', importedAt: 'Imported at'})));
  }); content.append(form, result);
}
async function navigate(view) {
  const version = ++generation;
  message();
  if (!actor) return;
  for (const control of navigation.querySelectorAll('[data-view]')) {
    if (control.dataset.view === view) control.setAttribute('aria-current', 'page'); else control.removeAttribute('aria-current');
  }
  content.replaceChildren(el('p', 'Loading…'));
  try {
    if (view === 'dashboard') await overview(version);
    else if (definitions[view]) await search(view, version);
    else if (view === 'activity' && writable()) activity();
    else if (view === 'knowledge' && actor.role === 'ADMIN') knowledge();
  } catch (error) { if (version === generation) message(error.message); }
}
navigation.addEventListener('click', event => { const control = event.target.closest('[data-view]'); if (control) navigate(control.dataset.view); });
sessionAction.addEventListener('click', () => task(async () => { if (actor) { signedOut(); message('Signed out of this workspace.'); } else await signIn(); }, sessionAction));
async function start() {
  await configure();
  if (!await finishSignIn()) return;
  actor = await api('/api/session');
  document.querySelector('#identity').textContent = `${actor.role} · Organization ${uid(actor.tenantId)}`;
  sessionAction.textContent = 'Sign out'; navigation.hidden = false;
  navigation.querySelectorAll('[data-write]').forEach(node => node.hidden = !writable());
  navigation.querySelectorAll('[data-admin]').forEach(node => node.hidden = actor.role !== 'ADMIN');
  await navigate('dashboard');
}
task(start, sessionAction);
