const form = document.querySelector('#config');
const targets = document.querySelector('#targets');
const template = document.querySelector('#target-template');
const message = document.querySelector('#message');

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
  document.querySelector('#health').textContent = status.sourceHealth;
  document.querySelector('#clock').textContent = status.clock || '--:--:--';
  [...targets.children].forEach(node => {
    const id = node.querySelector('[data-name=id]').value;
    const runtime = status.outputs.find(output => output.targetId === id);
    const output = node.querySelector('output');
    if (!runtime) {
      output.textContent = 'noch kein Runtime-Status';
      output.className = '';
      return;
    }
    const error = runtime.lastError ? ` · ${runtime.lastError}` : '';
    output.textContent = `${runtime.state} · ACK ${runtime.lastAckedSourceRevision}${error}`;
    output.className = runtime.state === 'CONNECTED' ? 'ok' : 'warn';
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
