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
  // Die Startliste ist eine eigene Nachricht mit eigener Revision. Sie kommt beim Verbinden und
  // nach einem Import, nicht mit jedem Uhrtelegramm, und ruehrt die Ergebnisansichten nicht an.
  if (message.type === 'startlist') { receiveStartList(message); return; }
  if (message.publicationRevision < publicationRevision) return;
  publicationRevision = message.publicationRevision;
  const hadState = state !== null;
  const previousDisplay = JSON.stringify(display);
  state = message.state;
  display = message.presentation;
  // Nur eine wirklich geaenderte Darstellungsauswahl baut die Startliste neu auf; ein
  // Uhrtelegramm darf ihre Tabelle nicht jede Sekunde neu zeichnen.
  if (JSON.stringify(display) !== previousDisplay && startListClasses.length) renderStartList();
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
// --- Startliste --------------------------------------------------------------------------------

// Die Klassen entstehen als abgeleitete Sicht auf die gelieferten Einträge. Die Reihenfolge
// stammt vollstaendig aus dem Import: Klassen in der Reihenfolge ihres ersten Auftretens,
// Teilnehmer in der Reihenfolge der Datei. Es wird nirgends nachsortiert.
let startListClasses = [];
let startListClassIndex = 0;
let startListColumns = [];
let startListPublicationRevision = -1;

const startListSelect = document.querySelector('#startlist-class');
const startListNav = document.querySelector('#startlist-nav');
const startListPosition = document.querySelector('#startlist-position');
const startListPrevious = document.querySelector('#startlist-previous');
const startListNext = document.querySelector('#startlist-next');

/** Spalte, Ueberschrift und der Wert je Eintrag. Reihenfolge = Anzeigereihenfolge. */
const STARTLIST_FIELDS = [
  {header: 'Startzeit', value: entry => entry.startTime},
  {header: 'StNr', value: entry => entry.bib},
  {header: 'Name', value: entry => [entry.firstName, entry.lastName].filter(Boolean).join(' ')},
  {header: 'Verein', value: entry => entry.club, shown: () => display?.showClub !== false},
  {header: 'Vbd', value: entry => entry.association, shown: () => display?.showAssociation !== false},
  {header: 'Nation', value: entry => entry.nation, shown: () => display?.showNation === true},
  {header: 'Jahrgang', value: entry => entry.birthYear},
  {header: 'Strecke', value: entry => entry.course}
];

startListSelect.addEventListener('change', () => {
  startListClassIndex = Number(startListSelect.value);
  renderStartList();
});
startListPrevious.addEventListener('click', () => stepStartListClass(-1));
startListNext.addEventListener('click', () => stepStartListClass(1));

function stepStartListClass(step) {
  const next = startListClassIndex + step;
  // An den Raendern wird nicht umgebrochen; die Schaltflaeche ist dort deaktiviert.
  if (next < 0 || next >= startListClasses.length) return;
  startListClassIndex = next;
  renderStartList();
}

function receiveStartList(message) {
  if (message.publicationRevision < startListPublicationRevision) return;
  startListPublicationRevision = message.publicationRevision;
  const previousName = startListClasses[startListClassIndex]?.name;
  startListClasses = groupByClass(message.entries || []);
  // Eine Spalte erscheint nur, wenn irgendein Eintrag sie fuellt. Das haelt die Tabelle schmal
  // und ueber alle Klassen hinweg gleich aufgebaut.
  startListColumns = STARTLIST_FIELDS.filter(field =>
    startListClasses.some(item => item.entries.some(entry => field.value(entry))));
  // Nach einem neuen Import und nach einem Reconnect bleibt der Betrachter bei seiner Klasse,
  // solange es sie noch gibt; sonst beginnt die Ansicht wieder bei der ersten.
  startListClassIndex = Math.max(0, startListClasses.findIndex(item => item.name === previousName));
  renderStartList();
}

/** Klassen in der Reihenfolge ihres ersten Auftretens, Eintraege in Dateireihenfolge. */
function groupByClass(entries) {
  const classes = [];
  const byName = new Map();
  for (const entry of entries) {
    let item = byName.get(entry.className);
    if (!item) {
      item = {name: entry.className, entries: []};
      byName.set(entry.className, item);
      classes.push(item);
    }
    item.entries.push(entry);
  }
  return classes;
}

function renderStartList() {
  const target = document.querySelector('#startlist-table');
  const total = startListClasses.length;
  startListNav.hidden = total === 0;
  startListSelect.parentElement.hidden = total === 0;
  if (total === 0) {
    startListSelect.replaceChildren();
    target.replaceChildren(emptyNote('Keine Startliste verfügbar.'));
    return;
  }
  if (startListClassIndex >= total) startListClassIndex = 0;
  // Wie bei den Ergebnisklassen: eine Signatur, die keine Trennzeichenkollision kennt.
  const signature = JSON.stringify(startListClasses.map(item => item.name));
  if (signature !== startListSelect.dataset.signature) {
    startListSelect.dataset.signature = signature;
    startListSelect.replaceChildren(...startListClasses.map((item, index) => new Option(item.name, index)));
  }
  startListSelect.value = String(startListClassIndex);
  startListPosition.textContent = `Klasse ${startListClassIndex + 1} von ${total}`;
  startListPrevious.disabled = startListClassIndex === 0;
  startListNext.disabled = startListClassIndex === total - 1;

  const item = startListClasses[startListClassIndex];
  const columns = startListColumns.filter(field => !field.shown || field.shown());
  const node = document.createElement('table');
  const head = node.createTHead().insertRow();
  columns.forEach(column => {
    const th = document.createElement('th');
    th.scope = 'col';
    th.textContent = column.header;
    head.append(th);
  });
  const body = node.createTBody();
  item.entries.forEach(entry => {
    const row = body.insertRow();
    // textContent statt innerHTML: Teilnehmerdaten kommen aus einer fremden Datei.
    columns.forEach(column => { row.insertCell().textContent = column.value(entry) || ''; });
  });
  target.replaceChildren(node);
}

function emptyNote(text) {
  const node = document.createElement('div');
  node.className = 'compact-empty';
  node.textContent = text;
  return node;
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
    startListPublicationRevision = -1;
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
// Vor der ersten Nachricht steht bereits ein verstaendlicher Zustand statt einer Luecke.
renderStartList();
start();
