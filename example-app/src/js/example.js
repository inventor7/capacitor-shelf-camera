import { ShelfCamera } from 'capacitor-shelf-camera';

const SESSION_ID = `session-${Date.now()}`;
let isStarted = false;
let isPanoActive = false;

// --- DOM refs ---
const log = document.getElementById('log');
const btnStart  = document.getElementById('btnStart');
const btnStop   = document.getElementById('btnStop');
const btnBegin  = document.getElementById('btnBegin');
const btnCommit = document.getElementById('btnCommit');
const btnCancel = document.getElementById('btnCancel');

const sigBlur    = document.getElementById('sigBlur');
const sigMotion  = document.getElementById('sigMotion');
const sigTilt    = document.getElementById('sigTilt');
const sigOverlap = document.getElementById('sigOverlap');
const sigLuma    = document.getElementById('sigLuma');
const sigFps     = document.getElementById('sigFps');

// --- Logging ---
function appendLog(msg, cls = 'info') {
  const ts = new Date().toLocaleTimeString('en-GB', { hour12: false, fractionalSecondDigits: 1 });
  const line = document.createElement('div');
  line.className = cls;
  line.textContent = `[${ts}] ${msg}`;
  log.appendChild(line);
  log.scrollTop = log.scrollHeight;
}

function updateButtons() {
  btnStart.disabled  = isStarted;
  btnStop.disabled   = !isStarted;
  btnBegin.disabled  = !isStarted || isPanoActive;
  btnCommit.disabled = !isPanoActive;
  btnCancel.disabled = !isPanoActive;
}

// --- Listeners ---
async function registerListeners() {
  await ShelfCamera.addListener('frame', (s) => {
    sigBlur.textContent    = s.blurScore.toFixed(2);
    sigMotion.textContent  = s.motionMagnitude.toFixed(2);
    sigTilt.textContent    = s.tiltDeg.toFixed(1);
    sigOverlap.textContent = s.overlapPct.toFixed(0);
    sigLuma.textContent    = s.lumaMean.toFixed(0);
    sigFps.textContent     = s.fps.toFixed(0);
  });

  await ShelfCamera.addListener('keyframeAccepted', (e) => {
    appendLog(`🖼 Keyframe ${e.frameId.slice(0,8)} @ (${e.gridCell.row},${e.gridCell.col}) — q=${e.qualityScore.toFixed(2)}`, 'event');
  });

  await ShelfCamera.addListener('stitchProgress', (e) => {
    appendLog(`🧩 Stitch progress: ${e.completedCells}/${e.totalCells} — seam=${e.seamScore.toFixed(2)}`, 'info');
  });

  await ShelfCamera.addListener('panoramaReady', (e) => {
    appendLog(`🎉 PANORAMA READY: ${e.width}×${e.height}px, seam=${e.seamScore.toFixed(2)}, ${e.durationMs}ms`, 'event');
    appendLog(`   URI: ${e.uri}`, 'info');
  });

  await ShelfCamera.addListener('error', (e) => {
    appendLog(`❌ ERROR [${e.code}]: ${e.message}`, 'error');
  });
}

// --- Actions ---
window.doStart = async () => {
  try {
    appendLog('Starting camera...', 'info');
    await ShelfCamera.start({ resolution: '1080p', coaching: true });
    isStarted = true;
    updateButtons();
    appendLog('Camera started ✓', 'event');
    await registerListeners();
  } catch (e) {
    appendLog(`Start failed: ${e.message || e}`, 'error');
  }
};

window.doStop = async () => {
  try {
    await ShelfCamera.removeAllListeners();
    await ShelfCamera.stop();
    isStarted = false;
    isPanoActive = false;
    updateButtons();
    resetSignals();
    appendLog('Camera stopped ✓', 'info');
  } catch (e) {
    appendLog(`Stop failed: ${e.message || e}`, 'error');
  }
};

window.doBeginPano = async () => {
  try {
    appendLog(`Beginning panorama: ${SESSION_ID}`, 'info');
    await ShelfCamera.beginPanorama({
      sessionId: SESSION_ID,
      mode: 'sweep',
      keyframeThresholds: {
        minBlur: 0.55,
        maxMotion: 0.35,
        maxTiltDeg: 8,
        minOverlapPct: 25,
      },
    });
    isPanoActive = true;
    updateButtons();
    appendLog('Panorama sweep started — pan slowly across shelf ✓', 'event');
  } catch (e) {
    appendLog(`Begin failed: ${e.message || e}`, 'error');
  }
};

window.doCommit = async () => {
  try {
    appendLog('Committing panorama...', 'info');
    const result = await ShelfCamera.commitPanorama({ sessionId: SESSION_ID });
    isPanoActive = false;
    updateButtons();
    appendLog(`Commit done: ${result.width}×${result.height}px`, 'event');
  } catch (e) {
    appendLog(`Commit failed: ${e.message || e}`, 'error');
  }
};

window.doCancel = async () => {
  try {
    await ShelfCamera.cancelPanorama({ sessionId: SESSION_ID });
    isPanoActive = false;
    updateButtons();
    appendLog('Panorama cancelled ✓', 'warn');
  } catch (e) {
    appendLog(`Cancel failed: ${e.message || e}`, 'error');
  }
};

function resetSignals() {
  sigBlur.textContent = sigMotion.textContent = sigTilt.textContent = '—';
  sigOverlap.textContent = sigLuma.textContent = sigFps.textContent = '—';
}

// --- Init ---
appendLog('Shelf Camera Demo ready. Tap "Start" to begin.', 'info');
updateButtons();
