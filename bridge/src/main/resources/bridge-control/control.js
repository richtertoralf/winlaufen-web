const form = document.querySelector('#config');
const targets = document.querySelector('#targets');
const localTargets = document.querySelector('#local-targets');
const browserView = document.querySelector('#browser-view');
const browserAddresses = document.querySelector('#browser-addresses');
const template = document.querySelector('#target-template');
const localTemplate = document.querySelector('#local-target-template');
const message = document.querySelector('#message');

// Die Runtime-Zustände des Backends bleiben unverändert; hier werden sie nur für
// die normale Benutzeransicht übersetzt. Ein unbekannter Zustand wird bewusst roh
// durchgereicht, statt ihn zu verschlucken.
const SOURCE_HEALTH_TEXT = {
  CONNECTED: 'Verbunden',
  DISCONNECTED: 'Nicht verbunden',
  STALE: 'Keine Daten – Verbindung wird erneuert'
};

const TARGET_STATE_TEXT = {
  CONNECTED: 'Verbunden',
  CONNECTING: 'Verbinde …',
  RETRY_WAIT: 'Nicht erreichbar – neuer Versuch läuft',
  STALE: 'Verbunden, aber keine Bestätigung',
  DISABLED: 'Nicht in Verwendung'
};

function sourceHealthText(state) { return SOURCE_HEALTH_TEXT[state] || state; }

function targetStateText(state) { return TARGET_STATE_TEXT[state] || state; }

function stateClass(state) {
  if (state === 'CONNECTED') return 'ok';
  if (state === 'DISABLED') return 'muted';
  return 'warn';
}

const hostField = document.querySelector('#source-host-field');
const hostInput = document.querySelector('#source-host');

/** Der Wert, der beim ausdrücklichen Wechsel auf "Auf diesem Computer" gesendet wird. */
const LOCAL_SOURCE_HOST = '127.0.0.1';

/** Nur eindeutige Loopback-Schreibweisen gelten als "dieser Computer". */
function isLocalSourceHost(host) {
  const value = String(host || '').trim().toLowerCase();
  return value === 'localhost' || value === '::1' || value === '[::1]'
      || value === '0:0:0:0:0:0:0:1' || /^127\.\d+\.\d+\.\d+$/.test(value);
}

function sourceLocation() {
  return form.elements.sourceLocation.value;
}

/**
 * Übernimmt den gespeicherten Host beim Laden unverändert. Ein bestehender Wert wird
 * hier nie umgeschrieben; das passiert erst, wenn der Benutzer die Auswahl selbst
 * ändert.
 */
function showSourceHost(host) {
  const local = isLocalSourceHost(host);
  form.elements.sourceLocation.value = local ? 'local' : 'remote';
  hostInput.value = local ? '' : host;
  form.sourceHost.value = host;
  updateSourceHostField();
}

function updateSourceHostField() {
  const remote = sourceLocation() === 'remote';
  hostField.hidden = !remote;
  hostInput.required = remote;
  if (!remote) {
    hostInput.setCustomValidity('');
  }
}

/** Wird nur durch eine Benutzeraktion ausgelöst und darf deshalb kanonisieren. */
function sourceLocationChanged() {
  updateSourceHostField();
  const remote = sourceLocation() === 'remote';
  form.sourceHost.value = remote ? hostInput.value.trim() : LOCAL_SOURCE_HOST;
}

function sourceHostTyped() {
  const value = hostInput.value.trim();
  hostInput.setCustomValidity(/^[a-z][a-z0-9+.-]*:\/\//i.test(value)
    ? 'Bitte nur Hostname oder IP-Adresse eingeben, ohne http:// oder https://'
    : '');
  form.sourceHost.value = value;
}

for (const radio of form.elements.sourceLocation) radio.onchange = sourceLocationChanged;
hostInput.oninput = sourceHostTyped;

/** Fester Portblock des Produkts; siehe installer/common/dist-manifest.env. */
const LIVE_HTTP_PORT = 44440;
const LIVE_WEBSOCKET_PORT = 44441;

/** Vertragswerte des Bridge-Ingest; siehe docs/MODULAR_ARCHITECTURE.md. */
const INGEST_PATH_PREFIX = '/bridge/v1/channels/';
const DEFAULT_CHANNEL = 'local';

function targetField(node, name) {
  return node.querySelector(`[data-name=${name}]`);
}

function advanced(node) {
  return node.querySelector('[data-role=advanced]');
}

function isIpv4Literal(value) {
  return /^\d{1,3}(\.\d{1,3}){3}$/.test(value)
    && value.split('.').every(part => Number(part) < 256);
}

function isIpv6Literal(value) {
  return value.includes(':') && /^[0-9a-f:.]+$/i.test(value);
}

/**
 * Aus einer IP-Adresse wird der Ingest-Endpunkt auf dem festen Port 44441 gebildet; die
 * Übertragung ist dann unverschlüsselt. Ein Hostname besitzt keinen solchen Standardport und wird
 * verschlüsselt über wss verbunden. Welche Kombination zulässig ist, entscheidet weiterhin allein
 * die EndpointPolicy im Backend.
 */
function derivedEndpoint(address, channel) {
  if (isIpv4Literal(address)) {
    return `ws://${address}:${LIVE_WEBSOCKET_PORT}${INGEST_PATH_PREFIX}${channel}`;
  }
  if (isIpv6Literal(address)) {
    return `ws://[${address}]:${LIVE_WEBSOCKET_PORT}${INGEST_PATH_PREFIX}${channel}`;
  }
  return `wss://${address}${INGEST_PATH_PREFIX}${channel}`;
}

function slug(value) {
  return String(value).toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

/**
 * Deterministisch aus Adresse und Channel. Der Channel gehört bewusst dazu: derselbe Live-Server
 * kann später mehrere Veranstaltungen als getrennte Channels aufnehmen. Dieselbe Eingabe ergibt
 * wieder dieselbe ID, sodass ein bereits gespeicherter Verbindungsschlüssel erhalten bleibt.
 */
function derivedTargetId(address, channel) {
  return `selfhost-${slug(address)}-${slug(channel)}`.slice(0, 64).replace(/-+$/, '');
}

/** Stabil und nachvollziehbar statt zufällig: der erste freie Zähler gewinnt. */
function uniqueTargetId(base, node) {
  const taken = new Set(targetNodes()
    .filter(other => other !== node)
    .map(other => targetField(other, 'id').value));
  if (!taken.has(base)) {
    return base;
  }
  for (let suffix = 2; suffix <= 99; suffix++) {
    if (!taken.has(`${base}-${suffix}`)) {
      return `${base}-${suffix}`;
    }
  }
  return base;
}

/** Erkennt die eigene Ableitung wieder, auch mit angehängtem Kollisionszähler. */
function matchesDerivedId(id, base) {
  return id === base || new RegExp(`^${base}-\\d+$`).test(id);
}

function addressOf(endpoint) {
  if (!endpoint) {
    return '';
  }
  try {
    return new URL(endpoint).hostname.replace(/^\[|\]$/g, '');
  } catch (error) {
    return '';
  }
}

/**
 * Erkennt das vom Setup eingerichtete lokale Ziel konservativ: Typ LOCAL und ein
 * Endpunkt auf einen eindeutigen Loopback-Host. Im Zweifel wird auf die technische
 * Darstellung zurückgefallen, damit ein fremdes Target nie fälschlich als
 * eingebautes Standardziel behandelt wird.
 */
function isBuiltInLocalTarget(value) {
  if (value.type !== 'LOCAL' || !value.endpoint) return false;
  let host;
  try {
    host = new URL(value.endpoint).hostname;
  } catch (error) {
    return false;
  }
  return isLocalSourceHost(host);
}

/**
 * Zeigt das lokale Ziel als benannte Zeile statt als Konfigurationsblock. ID, Typ,
 * enabled, Endpunkt, Channel und Secret werden unverändert in versteckten Feldern
 * mitgeführt, damit der bestehende POST-Vertrag bitgleich erhalten bleibt und beim
 * Speichern kein überflüssiger Reconnect entsteht.
 */
function addLocalTarget(value, order) {
  const node = localTemplate.content.firstElementChild.cloneNode(true);
  node.dataset.order = order;
  const field = name => node.querySelector(`[data-name=${name}]`);
  field('id').value = value.id;
  field('type').value = value.type;
  field('enabled').value = value.enabled ? 'on' : '';
  field('endpoint').value = value.endpoint;
  field('channelId').value = value.channelId;
  field('secret').value = '';
  localTargets.append(node);
}

let nextTargetOrder = 0;

function addTarget(value = {}) {
  const order = nextTargetOrder++;
  if (isBuiltInLocalTarget(value)) {
    addLocalTarget(value, order);
    return;
  }
  const node = template.content.firstElementChild.cloneNode(true);
  node.dataset.order = order;
  for (const input of node.querySelectorAll('[data-name]')) {
    const name = input.dataset.name;
    if (input.type === 'checkbox') input.checked = Boolean(value[name]);
    else input.value = value[name] || '';
  }
  showTargetWarnings(node, value);
  prepareAddress(node);
  node.querySelector('.remove').onclick = () => node.remove();
  targets.append(node);
}

/**
 * Die Warntexte kommen unverändert aus der Backend-Policy. Bridge Control stellt sie nur dar und
 * entscheidet keine Sicherheitsfrage selbst.
 */
function showTargetWarnings(node, value) {
  for (const role of ['transportWarning', 'secretWarning']) {
    const element = node.querySelector(`[data-role=${role}]`);
    element.textContent = value[role] || '';
    element.hidden = !value[role];
  }
}

/**
 * Ein gespeichertes Target wird nur dann als einfache Adresse geführt, wenn Endpoint und ID exakt
 * seiner Ableitung entsprechen. Jede andere, von Hand gepflegte Konfiguration bleibt unangetastet
 * und wird nie neu berechnet.
 */
function prepareAddress(node) {
  const address = node.querySelector('[data-role=address]');
  const endpoint = targetField(node, 'endpoint').value;
  const id = targetField(node, 'id').value;
  address.value = addressOf(endpoint);
  address.oninput = () => addressTyped(node);
  targetField(node, 'channelId').oninput = () => deriveFromAddress(node);
  targetField(node, 'id').oninput = () => setManualTarget(node);
  targetField(node, 'endpoint').oninput = () => setManualTarget(node);
  // Ein Pflichtfeld im zugeklappten Bereich wäre sonst unsichtbar ungültig.
  node.addEventListener('invalid', event => {
    if (event.target.closest('[data-role=advanced]')) advanced(node).open = true;
  }, true);
  if (!isDerivedTarget(node, address.value, endpoint, id)) {
    setManualTarget(node);
  }
}

/** Ein eben angelegtes, noch leeres Target zählt als abgeleitet und startet einfach. */
function isDerivedTarget(node, address, endpoint, id) {
  if (!endpoint && !id) return true;
  if (!address) return false;
  const channel = targetField(node, 'channelId').value.trim();
  return endpoint === derivedEndpoint(address, channel)
    && matchesDerivedId(id, derivedTargetId(address, channel));
}

/**
 * In dieses Feld gehört nur die Adresse selbst. Eine mitkopierte URL oder ein angehängter Port
 * werden sofort erklärt, statt später als unverständlicher Endpoint-Fehler zurückzukommen.
 */
function addressProblem(value) {
  if (value.includes('/')) {
    return 'Bitte nur die Adresse eintragen, ohne http:// und ohne Pfad';
  }
  if (/^\d{1,3}(\.\d{1,3}){3}:\d+$/.test(value)) {
    return 'Bitte nur die IP-Adresse eintragen, ohne Port';
  }
  return '';
}

function addressTyped(node) {
  const address = node.querySelector('[data-role=address]');
  address.setCustomValidity(addressProblem(address.value.trim()));
  if (address.validity.valid) deriveFromAddress(node);
}

function deriveFromAddress(node) {
  if (node.dataset.manual === 'true') return;
  const address = node.querySelector('[data-role=address]').value.trim();
  const channel = targetField(node, 'channelId').value.trim();
  if (!address || !channel) return;
  targetField(node, 'endpoint').value = derivedEndpoint(address, channel);
  targetField(node, 'id').value = uniqueTargetId(derivedTargetId(address, channel), node);
}

/**
 * Sobald Endpoint oder ID von Hand gesetzt sind, bleiben sie stehen. Die Adresse beschreibt das
 * Target dann nicht mehr und wird deaktiviert, damit sie weder überschreibt noch als Pflichtfeld
 * blockiert.
 */
function setManualTarget(node) {
  node.dataset.manual = 'true';
  const address = node.querySelector('[data-role=address]');
  address.disabled = true;
  node.querySelector('[data-role=manual-hint]').hidden = false;
  advanced(node).open = true;
}

/**
 * Bridge Control kennt den HTTP-Port des Live Servers nicht aus der API. Adressen werden
 * deshalb nur gezeigt, wenn das lokale Ziel den vereinbarten WebSocket-Port verwendet;
 * andernfalls ist ein Hinweis ehrlicher als eine womöglich falsche Adresse.
 */
function localViewPort(node) {
  const endpoint = node.querySelector('[data-name=endpoint]').value;
  try {
    return Number(new URL(endpoint).port) === LIVE_WEBSOCKET_PORT ? LIVE_HTTP_PORT : 0;
  } catch (error) {
    return 0;
  }
}

/** Loopback, unspezifische und Link-Local-Adressen taugen nicht als LAN-Adresse. */
function lanHost() {
  const host = location.hostname;
  if (!host || isLocalSourceHost(host) || host === '0.0.0.0'
      || /^169\.254\./.test(host)) {
    return '';
  }
  return host;
}

function addAddress(label, url) {
  const term = document.createElement('dt');
  term.textContent = label;
  const value = document.createElement('dd');
  const link = document.createElement('a');
  link.href = url;
  link.textContent = url;
  value.append(link);
  browserAddresses.append(term, value);
}

function addNote(text) {
  const note = document.createElement('dd');
  note.className = 'hint';
  note.textContent = text;
  browserAddresses.append(note);
}

function showBrowserAddresses() {
  const node = localTargets.firstElementChild;
  browserView.hidden = !node;
  browserAddresses.replaceChildren();
  if (!node) {
    return;
  }
  const port = localViewPort(node);
  if (!port) {
    addNote('Die Adresse hängt von der Konfiguration des Live Servers ab.');
    return;
  }
  addAddress('Auf diesem Computer:', `http://localhost:${port}/`);
  const host = lanHost();
  if (host) {
    addAddress('Im lokalen Netzwerk:', `http://${host}:${port}/`);
  } else {
    addNote(`Im lokalen Netzwerk über die Adresse dieses Computers auf Port ${port}. `
      + 'Rufen Sie Bridge Control über diese Adresse auf, um sie hier zu sehen.');
  }
}

/**
 * Die lokale Webansicht und die weiteren Live Server stehen in getrennten Abschnitten,
 * werden aber in ihrer ursprünglichen Reihenfolge serialisiert. Dadurch bleiben die
 * outputs.N-Indizes in der gespeicherten Konfiguration unverändert.
 */
function targetNodes() {
  return [...localTargets.children, ...targets.children]
    .sort((left, right) => Number(left.dataset.order) - Number(right.dataset.order));
}

function values() {
  const body = new URLSearchParams(new FormData(form));
  // Reine Bedienhilfe des Formulars; der bestehende POST-Vertrag kennt sie nicht.
  body.delete('sourceLocation');
  const nodes = targetNodes();
  body.set('targetCount', nodes.length);
  nodes.forEach((node, index) => node.querySelectorAll('[data-name]').forEach(input => {
    if (input.type !== 'checkbox' || input.checked) {
      body.set(`target.${index}.${input.dataset.name}`, input.type === 'checkbox' ? 'on' : input.value);
    }
  }));
  return body;
}

async function json(response) {
  let body;
  try {
    body = await response.json();
  } catch (error) {
    throw new Error(`Ungültige JSON-Antwort (${response.status})`);
  }
  if (!response.ok) throw new Error(body.error || `HTTP-Fehler ${response.status}`);
  return body;
}

async function load() {
  const [config, status] = await Promise.all([
    fetch('/api/v1/config').then(json),
    fetch('/api/v1/status').then(json)
  ]);
  showSourceHost(config.sourceHost);
  for (const [name, checked] of Object.entries(config.presentation)) form.elements[name].checked = checked;
  targets.replaceChildren();
  localTargets.replaceChildren();
  nextTargetOrder = 0;
  config.targets.forEach(value => addTarget(value));
  showBrowserAddresses();
  showStatus(status);
}

function showStatus(status) {
  const health = document.querySelector('#health');
  health.textContent = sourceHealthText(status.sourceHealth);
  health.className = stateClass(status.sourceHealth);
  document.querySelector('#clock').textContent = status.clock || '--:--:--';

  const sourceStatus = document.querySelector('#source-status');
  sourceStatus.textContent = sourceHealthText(status.sourceHealth);
  sourceStatus.className = stateClass(status.sourceHealth);
  document.querySelector('#source-help').hidden = status.sourceHealth !== 'DISCONNECTED';

  targetNodes().forEach(node => {
    const id = node.querySelector('[data-name=id]').value;
    const runtime = status.outputs.find(output => output.targetId === id);
    const output = node.querySelector('output');
    if (!runtime) {
      output.textContent = 'Noch kein Status';
      output.className = 'muted';
      return;
    }
    const error = runtime.lastError ? ` · ${runtime.lastError}` : '';
    output.textContent = `${targetStateText(runtime.state)}${error}`;
    output.className = stateClass(runtime.state);
  });
}

document.querySelector('#add').onclick =
  () => addTarget({type: 'SELFHOST', channelId: DEFAULT_CHANNEL, enabled: true});

form.onsubmit = async event => {
  event.preventDefault();
  const button = form.querySelector('button[type="submit"]');
  button.disabled = true;
  message.textContent = 'Speichere …';
  try {
    await fetch('/api/v1/config', {
      method: 'POST',
      headers: {'Content-Type': 'application/x-www-form-urlencoded'},
      body: values()
    }).then(json);
    message.textContent = 'Gespeichert';
    await load();
  } catch (error) {
    message.textContent = `Speichern fehlgeschlagen: ${error.message}`;
  } finally {
    button.disabled = false;
  }
};

load().catch(error => { message.textContent = `Initialisierung fehlgeschlagen: ${error.message}`; });
setInterval(() => fetch('/api/v1/status').then(json).then(showStatus).catch(() => {}), 1000);
