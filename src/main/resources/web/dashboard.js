const health = document.querySelector('#health');
const clock = document.querySelector('#clock');
const revision = document.querySelector('#revision');
const form = document.querySelector('#config-form');
const message = document.querySelector('#form-message');

function showState(state) {
  health.textContent = state.health.toLowerCase();
  health.className = `pill ${state.health.toLowerCase()}`;
  clock.textContent = state.clock || '--:--:--';
  revision.textContent = `Revision ${state.revision}`;
}
function connect(port) {
  const socket = new WebSocket(`ws://${location.hostname}:${port}`);
  socket.onmessage = event => showState(JSON.parse(event.data).state);
  socket.onclose = () => setTimeout(() => connect(port), 1500);
}
async function json(response) {
  let result;
  try {
    result = await response.json();
  } catch (error) {
    throw new Error(`Ungültige JSON-Antwort (${response.status})`);
  }
  if (!response.ok) throw new Error(result.error || `HTTP-Fehler ${response.status}`);
  return result;
}
Promise.all([fetch('/api/v1/config').then(json), fetch('/api/v1/state').then(json)]).then(([config, state]) => {
  form.winlaufenHost.value = config.winLaufenHost;
  form.outputMode.value = config.outputMode;
  for (const name of ['showClub','showAssociation','showNation','showShooting','showPublicMessages']) {
    form.elements[name].checked = config[name];
  }
  document.querySelector('#renderer-address').textContent = `${location.protocol}//${location.host}/renderer`;
  showState(state);
  connect(config.webSocketPort);
}).catch(error => { message.textContent = `Initialisierung fehlgeschlagen: ${error.message}`; });
form.addEventListener('submit', async event => {
  event.preventDefault();
  const button = form.querySelector('button[type="submit"]');
  button.disabled = true;
  message.textContent = 'Speichere …';
  try {
    const response = await fetch('/api/v1/config', {method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams(new FormData(form))});
    await json(response);
    message.textContent = 'Gespeichert. Renderer neu laden, um Darstellungsänderungen zu übernehmen.';
  } catch (error) {
    message.textContent = `Speichern fehlgeschlagen: ${error.message}`;
  } finally {
    button.disabled = false;
  }
});
