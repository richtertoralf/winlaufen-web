let state = null;
let display = null;
let liveClassIndex = null;
let resultsClassIndex = null;
let publicationRevision = -1;
const health = document.querySelector('#health');
const clock = document.querySelector('#clock');
const select = document.querySelector('#class-select');
const publicMessage = document.querySelector('#public-message');

document.querySelectorAll('.tabs button').forEach(button => button.addEventListener('click', () => {
  document.querySelectorAll('.tabs button').forEach(item => {
    const active = item === button;
    item.classList.toggle('active', active);
    if (active) item.setAttribute('aria-current', 'page'); else item.removeAttribute('aria-current');
  });
  document.querySelectorAll('.view').forEach(item => item.classList.toggle('active', item.id === button.dataset.view));
}));
select.addEventListener('change', () => { resultsClassIndex = Number(select.value); renderResults(); });

function receive(message) {
  if (message.publicationRevision < publicationRevision) return;
  publicationRevision = message.publicationRevision;
  const hadState = state !== null;
  state = message.state;
  display = message.presentation;
  // LIVE follows the class of the newest result snapshot, exactly as WinLaufen transmitted it.
  // currentFinish carries the class index of the most recent result telegram.
  if (state.currentFinish) liveClassIndex = state.currentFinish.classIndex;
  renderChrome();
  if (message.type === 'snapshot' || !hadState) renderTables();
}
function render() { renderChrome(); renderTables(); }
function renderChrome() {
  health.textContent = state.health;
  health.className = `pill ${state.health.toLowerCase()}`;
  clock.textContent = state.clock || '--:--:--';
  const visible = display.showPublicMessages && Boolean(state.message);
  publicMessage.hidden = !visible;
  publicMessage.textContent = visible ? `Hinweis: ${state.message}` : '';
}
function displayRoundOrHeat(rawRoundOrHeat) { return rawRoundOrHeat + 1; }
function renderTables() {
  const classes = state.competition?.classes || [];
  const previous = resultsClassIndex;
  select.replaceChildren(...classes.map(item => new Option(item.name, item.index)));
  if (previous !== null && classes.some(item => item.index === previous)) resultsClassIndex = previous;
  else if (resultsClassIndex === null && classes.length) resultsClassIndex = classes[0].index;
  if (resultsClassIndex !== null) select.value = resultsClassIndex;
  const live = classes.find(item => item.index === liveClassIndex);
  const round = state.competition ? ` · Runde/Durchgang ${displayRoundOrHeat(state.competition.roundOrHeat)}` : '';
  document.querySelector('#live-context').textContent = live ? `${live.name}${round}` : 'Noch keine aktuellen Ergebnisse von WinLaufen.';
  table(document.querySelector('#live-table'), live?.snapshot, currentRow(live), 'Noch keine aktuellen Ergebnisse von WinLaufen.');
  renderResults();
}
function currentRow(item) {
  const finish = state.currentFinish;
  return item?.snapshot && finish && finish.classIndex === item.index && finish.snapshotRevision === item.snapshot.revision ? finish.rowIndex : -1;
}
function renderResults() {
  const item = state?.competition?.classes.find(value => value.index === resultsClassIndex);
  table(document.querySelector('#results-table'), item?.snapshot, -1, 'Noch keine Ergebnisdaten verfügbar.');
}
function visibleColumn(header) {
  if (header === 'Verein') return display.showClub;
  if (header === 'Vbd') return display.showAssociation;
  if (header === 'Nation') return display.showNation;
  if (header === 'Schießen') return display.showShooting;
  return true;
}
function table(target, snapshot, highlighted, emptyText) {
  if (!snapshot) { target.innerHTML = `<div class="compact-empty">${emptyText}</div>`; return; }
  const columns = snapshot.headers.map((header, index) => ({header, index})).filter(column => visibleColumn(column.header));
  const node = document.createElement('table');
  const head = node.createTHead().insertRow();
  columns.forEach(column => { const th = document.createElement('th'); th.scope = 'col'; th.textContent = column.header; head.append(th); });
  const body = node.createTBody();
  snapshot.rows.forEach((row, index) => {
    const tr = body.insertRow();
    if (index === highlighted) { tr.className = 'current'; tr.setAttribute('aria-current', 'true'); }
    columns.forEach(column => { const td = tr.insertCell(); td.textContent = row[column.index]; });
  });
  target.replaceChildren(node);
}
function connect(runtime) {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
  const socket = new WebSocket(`${scheme}://${location.hostname}:${runtime.webSocketPort}${runtime.webSocketPath}`);
  socket.onmessage = event => receive(JSON.parse(event.data));
  socket.onclose = () => setTimeout(() => connect(runtime), 1500);
}
Promise.all([fetch('/api/v1/state').then(response => response.json()), fetch('/api/v1/runtime').then(response => response.json())])
  .then(([value, runtime]) => { receive(value); connect(runtime); });
