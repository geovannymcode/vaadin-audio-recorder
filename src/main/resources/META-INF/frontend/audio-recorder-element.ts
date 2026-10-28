import { css, html, LitElement, PropertyValues } from "lit";
import { customElement, property, state } from "lit/decorators.js";

/**
 * <audio-recorder-element>
 *
 * Client-side Lit component that captures audio from the microphone using
 * MediaRecorder + Web Audio API, renders a real-time canvas visualisation,
 * and dispatches the finished recording as a Base64 custom event.
 *
 * This component is AI-agnostic — it captures and delivers raw audio bytes.
 * The consuming Vaadin application decides what to do with them (send to
 * Whisper, Gemini, Anthropic, Deepgram, save to DB, etc.).
 */
@customElement("audio-recorder-element")
export class AudioRecorderElement extends LitElement {

  // ─── Properties (server → client) ─────────────────────────────────

  @property({ type: String })  visualMode: "waveform" | "bars" = "bars";
  @property({ type: Number })  canvasWidth  = 460;
  @property({ type: Number })  canvasHeight = 120;
  @property({ type: String })  strokeColor  = "#7c4dff";
  @property({ type: String })  bgColor      = "#161b2e";
  @property({ type: String })  gainColor    = "#00e676";
  @property({ type: String })  mimeType     = "audio/webm";
  @property({ type: Number })  fftSize      = 256;
  @property({ type: Number })  maxDuration  = 0;   // 0 = unlimited
  @property({ type: Boolean }) showControls = true;

  // ─── Internal state ───────────────────────────────────────────────

  @state() private _recording  = false;
  @state() private _paused     = false;
  @state() private _elapsed    = 0;
  @state() private _hasBlob    = false;

  private _stream:    MediaStream          | null = null;
  private _recorder:  MediaRecorder        | null = null;
  private _ctx:       AudioContext          | null = null;
  private _analyser:  AnalyserNode         | null = null;
  private _source:    MediaStreamAudioSourceNode | null = null;
  private _freqData:  Uint8Array<ArrayBuffer>           | null = null;
  private _chunks:    Blob[]               = [];
  private _rafId     = 0;
  private _timerId   = 0;
  private _lastBlob: Blob | null = null;
  @state() private _starting = false;
  @state() private _processing = false;
  private _generation = 0;
  private _recordedBytes = 0;
  private _discardRecording = false;
  private readonly _maxBytes = 50 * 1024 * 1024;
  private _playback: HTMLAudioElement | null = null;
  private _playbackUrl: string | null = null;

  // ─── Styles ───────────────────────────────────────────────────────

  static override styles = css`
    :host { display: inline-block; font-family: var(--lumo-font-family, system-ui, sans-serif); }

    .ar-root { display: flex; flex-direction: column; gap: 8px; align-items: center; }

    canvas { border-radius: 8px; display: block; }

    .ar-gain-track {
      width: 100%; height: 4px; border-radius: 2px; overflow: hidden;
      background: var(--lumo-contrast-10pct, #333);
    }
    .ar-gain-fill { height: 100%; border-radius: 2px; width: 0; transition: width 80ms linear; }

    .ar-bar {
      display: flex; gap: 6px; align-items: center; flex-wrap: wrap; justify-content: center;
    }

    .ar-timer {
      font-variant-numeric: tabular-nums; font-size: 13px; min-width: 48px; text-align: center;
      color: var(--lumo-secondary-text-color, #999);
    }

    /* Buttons */
    button {
      cursor: pointer; border: none; border-radius: 6px;
      padding: 7px 14px; font-size: 12px; font-weight: 700;
      color: #fff; letter-spacing: .03em;
      transition: opacity .15s, transform .1s;
    }
    button:active  { transform: scale(.95); }
    button:disabled { opacity: .35; cursor: default; }

    .ar-rec   { background: #e53935; }
    .ar-rec.on { background: #b71c1c; animation: ar-pulse 1.1s infinite; }
    .ar-pause { background: #fb8c00; }
    .ar-stop  { background: #546e7a; }
    .ar-play   { background: #43a047; }
    .ar-upload { background: #7c4dff; }

    @keyframes ar-pulse {
      0%,100% { box-shadow: 0 0 0 0 rgba(229,57,53,.45); }
      50%     { box-shadow: 0 0 0 7px rgba(229,57,53,0); }
    }
  `;

  // ─── Template ─────────────────────────────────────────────────────

  override render() {
    return html`
      <div class="ar-root">
        <canvas id="cv"
          width="${this.canvasWidth}" height="${this.canvasHeight}"
          style="background:${this.bgColor};width:${this.canvasWidth}px;height:${this.canvasHeight}px">
        </canvas>

        <div class="ar-gain-track">
          <div class="ar-gain-fill" id="gf" style="background:${this.gainColor}"></div>
        </div>

        ${this.showControls ? html`
          <div class="ar-bar">
            <button class="ar-rec ${this._recording ? 'on' : ''}"
              @click=${this._start} ?disabled=${this._recording || this._starting || this._processing}>● REC</button>

            <button class="ar-pause" @click=${this._togglePause}
              ?disabled=${!this._recording}>
              ${this._paused ? "▶ Resume" : "❚❚ Pause"}
            </button>

            <button class="ar-stop" @click=${this._stop}
              ?disabled=${!this._recording}>■ Stop</button>

            <span class="ar-timer">${this._fmt(this._elapsed)}</span>

            <button class="ar-play" @click=${this._play}
              ?disabled=${!this._hasBlob || this._recording}>▶ Play</button>

            <button class="ar-upload" @click=${this._triggerUpload}
              ?disabled=${this._recording || this._starting || this._processing}>⬆ Upload</button>
            <input id="fileInput" type="file" accept="audio/*"
              @change=${this._handleUpload} style="display:none">
          </div>
        ` : ""}
      </div>`;
  }

  // ─── Public JS API (callable from Java via element.callJsMethod) ──

  /** Programmatically start recording (e.g. from a custom Java button). */
  public startRecording()  { this._start(); }
  /** Programmatically stop recording. */
  public stopRecording()   { this._stop(); }
  /** Programmatically pause recording. */
  public pauseRecording()  { if (this._recording && !this._paused) this._togglePause(); }
  /** Programmatically resume recording. */
  public resumeRecording() { if (this._recording && this._paused)  this._togglePause(); }

  // ─── Recording lifecycle ──────────────────────────────────────────

  private async _start() {
    if (this._recording || this._starting || this._processing) return;
    this._starting = true;
    const generation = this._generation;
    try {
      if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === "undefined") {
        throw new Error("Audio recording requires a supported browser and HTTPS or localhost");
      }
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      if (!this.isConnected || generation !== this._generation) {
        stream.getTracks().forEach(track => track.stop());
        return;
      }
      this._stopPlayback();
      this._stream = stream;
      this._ctx = new AudioContext();
      await this._ctx.resume();
      if (!this.isConnected || generation !== this._generation) return;
      this._analyser = this._ctx.createAnalyser();
      this._analyser.fftSize = this.fftSize;
      this._freqData = new Uint8Array(this._analyser.frequencyBinCount);
      this._source = this._ctx.createMediaStreamSource(stream);
      this._source.connect(this._analyser);

      const opts: MediaRecorderOptions = {};
      if (MediaRecorder.isTypeSupported(this.mimeType)) opts.mimeType = this.mimeType;
      this._recorder = new MediaRecorder(stream, opts);
      this._chunks = [];
      this._recordedBytes = 0;
      this._discardRecording = false;
      this._recorder.ondataavailable = (ev) => {
        this._recordedBytes += ev.data.size;
        if (this._recordedBytes > this._maxBytes) {
          if (!this._discardRecording) {
            this._discardRecording = true;
            this._chunks = [];
            this._fire("recording-error", { message: "Audio exceeds the 50 MiB limit" });
            this._stop();
          }
        } else if (!this._discardRecording && ev.data.size > 0) {
          this._chunks.push(ev.data);
        }
      };
      this._recorder.onstop = () => { void this._encode(); };
      this._recorder.onerror = () => {
        this._fire("recording-error", { message: "Audio recording failed" });
        this._stop();
      };
      this._recorder.start(250);
      this._recording = true;
      this._paused = false;
      this._elapsed = 0;
      this._hasBlob = false;
      this._tick();
      this._draw();
      this._fire("recording-started", {});
    } catch (error) {
      if (this._recorder && this._recorder.state !== "inactive") {
        this._recorder.onstop = null;
        this._recorder.stop();
      }
      this._recording = false;
      this._stopTick();
      cancelAnimationFrame(this._rafId);
      this._teardown();
      this._fire("recording-error", { message: error instanceof Error ? error.message : "Microphone access denied or unavailable" });
    } finally {
      this._starting = false;
    }
  }

  private _togglePause() {
    if (!this._recorder || !this._recording) return;
    if (this._paused) {
      this._recorder.resume();
      this._paused = false;
      this._tick();
      this._draw();
      this._fire("recording-resumed", {});
    } else {
      this._recorder.pause();
      this._paused = true;
      this._stopTick();
      cancelAnimationFrame(this._rafId);
      this._fire("recording-paused", {});
    }
  }

  private _stop() {
    if (!this._recording) return;
    this._processing = true;
    if (this._recorder?.state !== "inactive") this._recorder?.stop();
    this._teardown();
    this._recording = false;
    this._paused    = false;
    this._stopTick();
    cancelAnimationFrame(this._rafId);
    this._clearCanvas();
    this._fire("recording-stopped", { durationSeconds: this._elapsed });
  }

  /** Convert chunks → Blob → Base64 and fire the main event. */
  private async _encode() {
    const generation = this._generation;
    try {
      if (this._discardRecording) return;
      const mime = this._recorder?.mimeType || this.mimeType;
      const blob = new Blob(this._chunks, { type: mime });
      const duration = this._elapsed;
      this._chunks = [];
      const base64 = await this._toBase64(blob);
      if (!this.isConnected || generation !== this._generation) return;
      this._lastBlob = blob;
      this._hasBlob = true;
      this._fire("recording-finished", {
        base64, mimeType: mime, durationSeconds: duration, sizeBytes: blob.size,
      });
    } catch (error) {
      this._fire("recording-error", { message: error instanceof Error ? error.message : "Audio encoding failed" });
    } finally {
      this._processing = false;
    }
  }

  private _toBase64(blob: Blob): Promise<string> {
    if (blob.size > this._maxBytes) return Promise.reject(new Error("Audio exceeds the 50 MiB limit"));
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve((reader.result as string).split(",", 2)[1]);
      reader.onerror = () => reject(new Error("Cannot read audio data"));
      reader.onabort = () => reject(new Error("Audio read cancelled"));
      reader.readAsDataURL(blob);
    });
  }

  private _stopPlayback() {
    if (this._playback) {
      this._playback.pause();
      this._playback.removeAttribute("src");
      this._playback.load();
      this._playback = null;
    }
    if (this._playbackUrl) URL.revokeObjectURL(this._playbackUrl);
    this._playbackUrl = null;
  }

  private async _play() {
    if (!this._lastBlob) return;
    this._stopPlayback();
    this._playbackUrl = URL.createObjectURL(this._lastBlob);
    const audio = new Audio(this._playbackUrl);
    this._playback = audio;
    audio.onended = () => this._stopPlayback();
    audio.onerror = () => {
      this._stopPlayback();
      this._fire("recording-error", { message: "Audio playback failed" });
    };
    try {
      await audio.play();
      this._fire("playback-started", {});
    } catch {
      this._stopPlayback();
      this._fire("recording-error", { message: "Audio playback unavailable or blocked" });
    }
  }

  // ─── File upload ───────────────────────────────────────────────

  private _triggerUpload() {
    (this.shadowRoot?.getElementById("fileInput") as HTMLInputElement | null)?.click();
  }

  private _duration(file: File): Promise<number> {
    return new Promise(resolve => {
      const audio = new Audio();
      const url = URL.createObjectURL(file);
      const finish = () => {
        clearTimeout(timer);
        const seconds = Number.isFinite(audio.duration) ? Math.round(audio.duration) : 0;
        audio.onloadedmetadata = null;
        audio.onerror = null;
        audio.removeAttribute("src");
        audio.load();
        URL.revokeObjectURL(url);
        resolve(Math.max(0, seconds));
      };
      const timer = window.setTimeout(finish, 5000);
      audio.onloadedmetadata = finish;
      audio.onerror = finish;
      audio.preload = "metadata";
      audio.src = url;
    });
  }

  private async _handleUpload(e: Event) {
    const input = e.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = "";
    if (!file || this._recording || this._starting || this._processing) return;
    this._processing = true;
    const generation = this._generation;
    try {
      const allowed = ["audio/webm", "audio/ogg", "audio/mp4", "audio/mpeg", "audio/wav", "audio/x-wav", "audio/aac", "audio/flac"];
      if (!allowed.includes(file.type.split(";", 1)[0].trim().toLowerCase())) {
        throw new Error("Unsupported or unknown audio MIME type");
      }
      const base64 = await this._toBase64(file);
      const duration = await this._duration(file);
      if (!this.isConnected || generation !== this._generation) return;
      this._stopPlayback();
      this._lastBlob = file;
      this._hasBlob = true;
      this._fire("recording-finished", {
        base64, mimeType: file.type, durationSeconds: duration, sizeBytes: file.size,
      });
    } catch (error) {
      this._fire("recording-error", { message: error instanceof Error ? error.message : "Audio upload failed" });
    } finally {
      this._processing = false;
    }
  }

  // ─── Canvas visualisation ─────────────────────────────────────────

  private _draw() {
    const cv = this.shadowRoot?.getElementById("cv") as HTMLCanvasElement | null;
    if (!cv || !this._analyser || !this._freqData) return;
    const g  = cv.getContext("2d")!;
    const gf = this.shadowRoot?.getElementById("gf") as HTMLElement | null;

    const loop = () => {
      if (!this._recording || this._paused) return;
      this._rafId = requestAnimationFrame(loop);
      this._analyser!.getByteFrequencyData(this._freqData!);

      // Gain bar
      let sum = 0;
      for (let i = 0; i < this._freqData!.length; i++) sum += this._freqData![i];
      const pct = Math.min(100, (sum / this._freqData!.length / 255) * 250);
      if (gf) gf.style.width = `${pct}%`;

      g.fillStyle = this.bgColor;
      g.fillRect(0, 0, cv.width, cv.height);

      this.visualMode === "bars" ? this._bars(g, cv) : this._wave(g, cv);
    };
    this._rafId = requestAnimationFrame(loop);
  }

  private _bars(g: CanvasRenderingContext2D, cv: HTMLCanvasElement) {
    const d = this._freqData!;
    const w = cv.width / d.length;
    const mid = cv.height / 2;

    for (let i = 0; i < d.length; i++) {
      const h = (d[i] / 255) * mid;
      const x = i * w;
      const grad = g.createLinearGradient(x, mid - h, x, mid + h);
      grad.addColorStop(0,   this.strokeColor);
      grad.addColorStop(0.5, this.gainColor);
      grad.addColorStop(1,   this.strokeColor);
      g.fillStyle = grad;
      g.fillRect(x, mid - h, Math.max(w - 1, 1), h);   // top half
      g.fillRect(x, mid,     Math.max(w - 1, 1), h);   // mirror
    }
  }

  private _wave(g: CanvasRenderingContext2D, cv: HTMLCanvasElement) {
    const td = new Uint8Array(this._analyser!.fftSize);
    this._analyser!.getByteTimeDomainData(td);
    g.lineWidth   = 2;
    g.strokeStyle = this.strokeColor;
    g.beginPath();
    const sw = cv.width / td.length;
    for (let i = 0; i < td.length; i++) {
      const y = (td[i] / 128) * cv.height / 2;
      i === 0 ? g.moveTo(0, y) : g.lineTo(i * sw, y);
    }
    g.lineTo(cv.width, cv.height / 2);
    g.stroke();
  }

  private _clearCanvas() {
    const cv = this.shadowRoot?.getElementById("cv") as HTMLCanvasElement | null;
    if (!cv) return;
    const g = cv.getContext("2d")!;
    g.fillStyle = this.bgColor;
    g.fillRect(0, 0, cv.width, cv.height);
    const gf = this.shadowRoot?.getElementById("gf") as HTMLElement | null;
    if (gf) gf.style.width = "0";
  }

  // ─── Timer ────────────────────────────────────────────────────────

  private _tick() {
    this._stopTick();
    this._timerId = window.setInterval(() => {
      this._elapsed++;
      this._fire("recording-tick", { elapsed: this._elapsed });
      if (this.maxDuration > 0 && this._elapsed >= this.maxDuration) this._stop();
    }, 1000);
  }
  private _stopTick() { if (this._timerId) { clearInterval(this._timerId); this._timerId = 0; } }
  private _fmt(s: number) {
    return `${String(Math.floor(s / 60)).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}`;
  }

  // ─── Cleanup ──────────────────────────────────────────────────────

  private _teardown() {
    this._source?.disconnect();   this._source  = null;
    if (this._ctx && this._ctx.state !== "closed") void this._ctx.close().catch(() => {});
    this._ctx = null;
    this._analyser = null;
    this._freqData = null;
    this._stream?.getTracks().forEach(t => t.stop());
    this._stream = null;
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    this._generation++;
    if (this._recorder) {
      this._recorder.onstop = null;
      this._recorder.ondataavailable = null;
      this._recorder.onerror = null;
      if (this._recorder.state !== "inactive") this._recorder.stop();
    }
    this._recorder = null;
    this._recording = false;
    this._paused = false;
    this._chunks = [];
    this._lastBlob = null;
    this._hasBlob = false;
    this._stopPlayback();
    this._teardown();
    this._stopTick();
    cancelAnimationFrame(this._rafId);
  }

  // ─── Events → Java ────────────────────────────────────────────────

  private _fire(name: string, detail: Record<string, unknown>) {
    this.dispatchEvent(new CustomEvent(name, { detail, bubbles: true, composed: true }));
  }
}
