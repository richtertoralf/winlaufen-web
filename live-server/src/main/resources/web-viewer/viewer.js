let state = null;
let display = null;
let liveClassIndex = null;
let resultsClassIndex = null;
let renderedClasses = null;
let publicationRevision = -1;
const health = document.querySelector('#health');
const clock = document.querySelector('#clock');
const select = document.querySelector('#class-select');
const publicMessage = document.querySelector('#public-message');
const linkNotice = document.querySelector('#link-notice');

// Zustand der Verbindung dieses Browsers zum Live Server. Er ist nicht der Zustand der
// WinLaufen-Quelle (state.health) und nicht der Zustand Bridge -> Live Server. CONNECTED darf
// nur erscheinen, solange diese Seite tatsaechlich Live-Daten empfaengt.
let linkLive = false;
let socket = null;
let linkTimer = null;
let reconnectAttempt = 0;
let reconnectTimer = null;

// Der Live Server sendet Browsern alle 2 s ein Lebenszeichen; drei ausgefallene davon gelten
// als Verbindungsverlust. Der Wert liegt bewusst ueber dem 4-s-Stale-Fenster der Quelle, damit
// eine ruhende Veranstaltung nicht faelschlich als Verbindungsverlust erscheint.
const LINK_TIMEOUT_MILLIS = 6000;
// Wartezeiten wie beim Output-Reconnect der Bridge: sofort, 2 s, 5 s, danach 10 s.
const RECONNECT_DELAYS_MILLIS = [0, 2000, 5000, 10000];

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
  noteTraffic();
  // Ein Lebenszeichen traegt bewusst keinen Zustand und darf keine Tabelle anfassen.
  if (message.type === 'heartbeat') return;
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
  // Ohne funktionierende Verbindung ist die zuletzt gemeldete Quellenlage keine Aussage mehr
  // ueber das Jetzt; dann gilt allein der Zustand dieser Verbindung.
  const shown = linkLive && state ? state.health : 'DISCONNECTED';
  health.textContent = shown;
  health.className = `pill ${shown.toLowerCase()}`;
  clock.textContent = state?.clock || '--:--:--';
  // Die letzten Ergebnisse bleiben lesbar, werden aber sichtbar als nicht aktuell markiert.
  document.body.classList.toggle('link-lost', !linkLive);
  linkNotice.hidden = linkLive;
  const visible = Boolean(display?.showPublicMessages) && Boolean(state?.message);
  publicMessage.hidden = !visible;
  publicMessage.textContent = visible ? `Hinweis: ${state.message}` : '';
}

function setLink(live) {
  linkLive = live;
  renderChrome();
}

/** Jede empfangene Nachricht ist der Beweis, dass die Verbindung noch traegt. */
function noteTraffic() {
  clearTimeout(linkTimer);
  linkTimer = setTimeout(linkTimedOut, LINK_TIMEOUT_MILLIS);
  if (!linkLive) setLink(true);
}

/**
 * Eine tote TCP-Verbindung meldet sich nie von selbst: nach einem Reboot des Live-Server-Rechners
 * bleibt der Socket im Browser offen, ohne dass je wieder Daten kommen. Deshalb entscheidet das
 * ausbleibende Lebenszeichen, nicht onclose allein.
 */
function linkTimedOut() {
  setLink(false);
  if (socket) socket.close();
}
function displayRoundOrHeat(rawRoundOrHeat) { return rawRoundOrHeat + 1; }
function renderTables() {
  const classes = state.competition?.classes || [];
  // Ein Uhrtelegramm laesst die Klassenliste unveraendert. Die Auswahl wird deshalb nur
  // neu aufgebaut, wenn sich die angebotenen Klassen wirklich geaendert haben; ein
  // Neuaufbau bei jedem Snapshot wuerde eine gerade offene Auswahl zerstoeren.
  const signature = JSON.stringify(classes.map(item => [item.index, item.name]));
  if (signature !== renderedClasses) {
    renderedClasses = signature;
    const previous = resultsClassIndex;
    select.replaceChildren(...classes.map(item => new Option(item.name, item.index)));
    if (previous !== null && classes.some(item => item.index === previous)) resultsClassIndex = previous;
    else if (resultsClassIndex === null && classes.length) resultsClassIndex = classes[0].index;
    if (resultsClassIndex !== null) select.value = resultsClassIndex;
  }
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
  socket = new WebSocket(`${scheme}://${location.hostname}:${runtime.webSocketPort}${runtime.webSocketPath}`);
  socket.onopen = () => {
    // Der Revisionszaehler gehoert zu genau einer Live-Server-Laufzeit. Ein neu gestarteter Live
    // Server beginnt wieder bei 0; ohne diesen Reset wuerde der Browser jeden neuen Snapshot als
    // veraltet verwerfen und trotz bestehender Verbindung nie wieder Daten anzeigen. Die erste
    // Nachricht jeder Verbindung ist ein vollstaendiger, autoritativer Snapshot.
    publicationRevision = -1;
    reconnectAttempt = 0;
    noteTraffic();
  };
  socket.onmessage = event => receive(JSON.parse(event.data));
  // onerror wird laut Spezifikation stets von onclose gefolgt; der Reconnect steht nur dort.
  socket.onerror = () => socket.close();
  socket.onclose = () => {
    clearTimeout(linkTimer);
    setLink(false);
    retryLater(() => connect(runtime));
  };
}

/** Begrenzte, ansteigende Wartezeit statt schneller Endlosschleife; nach Erfolg zurueckgesetzt. */
function retryLater(action) {
  clearTimeout(reconnectTimer);
  const index = Math.min(reconnectAttempt, RECONNECT_DELAYS_MILLIS.length - 1);
  reconnectAttempt += 1;
  reconnectTimer = setTimeout(action, RECONNECT_DELAYS_MILLIS[index]);
}

/**
 * Auch der erste Aufruf kann in einen Ausfall laufen. Ohne eigenen Wiederholungsversuch bliebe
 * eine waehrend des Ausfalls geladene Seite dauerhaft leer und nur ein Reload wuerde helfen.
 */
function start() {
  Promise.all([
    fetch('/api/v1/state').then(response => response.json()),
    fetch('/api/v1/runtime').then(response => response.json())
  ]).then(([value, runtime]) => { receive(value); connect(runtime); })
    .catch(() => { setLink(false); retryLater(start); });
}
start();
