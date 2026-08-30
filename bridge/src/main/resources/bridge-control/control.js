const form = document.querySelector('#config');
const targets = document.querySelector('#targets');
const template = document.querySelector('#target-template');
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

function addTarget(value = {}) {
  const node = template.content.firstElementChild.cloneNode(true);
  for (const input of node.querySelectorAll('[data-name]')) {
    const name = input.dataset.name;
    if (input.type === 'checkbox') input.checked = Boolean(value[name]);
    else input.value = value[name] || '';
  }
  node.querySelector('.remove').onclick = () => node.remove();
  targets.append(node);
}

function values() {
  const body = new URLSearchParams(new FormData(form));
  const nodes = [...targets.children];
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
  form.sourceHost.value = config.sourceHost;
  for (const [name, checked] of Object.entries(config.presentation)) form.elements[name].checked = checked;
  targets.replaceChildren();
  config.targets.forEach(addTarget);
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

  [...targets.children].forEach(node => {
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

document.querySelector('#add').onclick = () => addTarget({type: 'SELFHOST'});

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
