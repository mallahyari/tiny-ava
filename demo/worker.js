// tiny-ava Web Worker: 100% client-side Persian TTS (persian-g2p-tiny ONNX + tiny-ava-parisa ONNX)
// Total model download: ~29 MB, cached in CacheStorage after first visit.

const ORT_CDN = "https://cdn.jsdelivr.net/npm/onnxruntime-web@1.20.1/dist/";
importScripts(ORT_CDN + "ort.wasm.min.js");

const CACHE_NAME = "tiny-ava-v2";
const SAMPLE_RATE = 24000;
const PEAK = 0.99;

// Exact 46-symbol Persian IPA table (matches models/parisa/tiny-ava-parisa.json)
const SYMBOLS = [
  "<pad>", "<bos>", "<eos>", "<sp>", "<unk>",
  "،", ".", "؟", "!", "؛", ":", "«", "»", "(", ")", "—", "…",
  "p", "b", "t", "d", "k", "ɡ", "q", "ʔ", "f", "v", "s", "z", "ʃ", "ʒ", "x", "h", "tʃ", "dʒ", "m", "n", "l", "r", "j",
  "a", "e", "o", "i", "u", "ɑ",
];
const PAD = "<pad>", BOS = "<bos>", EOS = "<eos>", SP = "<sp>", UNK = "<unk>";
const PUNCTUATION = new Set(["،", ".", "؟", "!", "؛", ":", "«", "»", "(", ")", "—", "…"]);
const VOWELS = new Set(["a", "e", "o", "i", "u", "ɑ"]);
const SYMBOL_TO_ID = Object.fromEntries(SYMBOLS.map((s, i) => [s, i]));
const PHONE_SET = new Set(SYMBOLS.slice(17));
const REPR1_TO_IPA = {
  a: "a", A: "ɑ", e: "e", o: "o", i: "i", u: "u",
  p: "p", b: "b", t: "t", d: "d", k: "k", g: "ɡ", q: "q", "?": "ʔ",
  f: "f", v: "v", s: "s", z: "z", S: "ʃ", Z: "ʒ", ";": "ʒ", x: "x", h: "h",
  C: "tʃ", j: "dʒ", m: "m", n: "n", l: "l", r: "r", y: "j",
};

const CHAR_MAP = {
  "\u064a": "\u06cc", "\u0649": "\u06cc", "\u0643": "\u06a9", "\u0629": "\u0647",
  "\u06c0": "\u0647", "\u0623": "\u0627", "\u0625": "\u0627", "\u0671": "\u0627",
  "\u0640": "", "\u200b": "", "\u200d": "", "\ufeff": "", "\u00a0": " ",
  ",": "،", "?": "؟", ";": "؛", "–": "—", "―": "—", '"': "«",
};

const utf8Enc = new TextEncoder();
const utf8Dec = new TextDecoder("utf-8", { fatal: false });

let g2pEncSess = null;
let g2pDecSess = null;
let ttsSess = null;
const symbolToId = SYMBOL_TO_ID;
const phoneSet = PHONE_SET;
const maxTokens = 512;
let cancelledId = -1;

function sendStatus(text, progress = null) {
  postMessage({ type: "status", text, progress });
}

async function fetchCached(url, label, baseProg, spanProg) {
  let store = null;
  try {
    store = await caches.open(CACHE_NAME);
    const hit = await store.match(url);
    if (hit) {
      sendStatus(`${label} (cached)`, baseProg + spanProg);
      return new Uint8Array(await hit.arrayBuffer());
    }
  } catch (_) {
    store = null;
  }

  // Default credentials ("same-origin") is required so cross-origin redirects to
  // HF Xet/CDN (which respond with Access-Control-Allow-Origin: *) pass CORS cleanly.
  const resp = await fetch(url);
  if (!resp.ok) {
    if (resp.status === 401 || resp.status === 403) {
      throw new Error(
        `Private Space auth blocked in iframe (HTTP ${resp.status}). Open the direct Space URL in its own tab, or make the Space public.`
      );
    }
    throw new Error(`Failed to fetch ${url} (HTTP ${resp.status})`);
  }
  const ct = (resp.headers.get("content-type") || "").toLowerCase();
  if (ct.includes("text/plain") || ct.includes("text/html")) {
    throw new Error(
      `Blocked by private Space auth (got ${ct}). Open the direct .static.hf.space URL in its own tab, or make the Space public.`
    );
  }
  const total = Number(resp.headers.get("content-length")) || 0;
  if (!resp.body || !total) {
    const buf = new Uint8Array(await resp.arrayBuffer());
    if (store) store.put(url, new Response(buf)).catch(() => {});
    sendStatus(label, baseProg + spanProg);
    return buf;
  }

  const reader = resp.body.getReader();
  const chunks = [];
  let received = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value);
    received += value.length;
    sendStatus(label, baseProg + spanProg * Math.min(1, received / total));
  }
  const out = new Uint8Array(received);
  let off = 0;
  for (const c of chunks) { out.set(c, off); off += c.length; }
  if (store) store.put(url, new Response(out)).catch(() => {});
  return out;
}

async function loadAll() {
  if (ttsSess && g2pEncSess && g2pDecSess) {
    postMessage({ type: "ready" });
    return;
  }
  ort.env.wasm.wasmPaths = ORT_CDN;
  ort.env.wasm.numThreads = 1;

  const encBytes = await fetchCached("assets/g2p/g2p_encoder.onnx", "Downloading Persian G2P encoder (3.5 MB)", 0.0, 0.14);
  const decBytes = await fetchCached("assets/g2p/g2p_decoder.onnx", "Downloading Persian G2P decoder (5.3 MB)", 0.14, 0.18);
  const ttsBytes = await fetchCached("assets/tiny-ava-parisa.onnx", "Downloading tiny-ava Parisa (20.4 MB)", 0.32, 0.58);

  sendStatus("Initializing ONNX Runtime Web...", 0.93);
  g2pEncSess = await ort.InferenceSession.create(encBytes, { executionProviders: ["wasm"] });
  g2pDecSess = await ort.InferenceSession.create(decBytes, { executionProviders: ["wasm"] });
  ttsSess = await ort.InferenceSession.create(ttsBytes, { executionProviders: ["wasm"] });

  sendStatus("Ready", 1.0);
  postMessage({ type: "ready" });
}

// --- Persian text normalization & sentence splitting ---
function normalizePersian(text) {
  let s = text.normalize("NFC");
  let mapped = "";
  for (const ch of s) mapped += (ch in CHAR_MAP) ? CHAR_MAP[ch] : ch;
  // Keep only short vowels َ ُ ِ and shadda ّ
  mapped = mapped.replace(/[\u064b-\u064d\u0652-\u065f\u0670]/g, "");
  mapped = mapped.replace(/\.{3,}/g, "…");
  mapped = mapped.replace(/-{2,}|\s-\s/g, "—");
  mapped = mapped.replace(/\u200c+/g, "\u200c");
  mapped = mapped.replace(/\s+\u200c|\u200c\s+/g, " ");
  return mapped.replace(/\s+/g, " ").trim();
}

function splitSentences(text) {
  const cleaned = text.replace(/[\s\ufeff]+/g, " ").trim();
  if (!cleaned) return [];
  const raw = cleaned.split(/(?<=[.!؟?…]["')\]»]?)\s+/);
  return raw.map((s) => s.trim()).filter((s) => /[\p{L}\p{N}]/u.test(s));
}

function ensureTerminalMark(text) {
  const s = text.trim();
  if (!s) return s;
  const last = s.slice(-1);
  if (".!؟?…،,;؛:".includes(last)) return s;
  return s + ".";
}

function splitPunctChunks(text) {
  const pieces = [];
  let start = 0;
  for (let i = 0; i < text.length; i++) {
    if (!PUNCTUATION.has(text[i])) continue;
    const seg = text.slice(start, i).trim();
    if (seg) pieces.push([false, seg]);
    pieces.push([true, text[i]]);
    start = i + 1;
  }
  const tail = text.slice(start).trim();
  if (tail) pieces.push([false, tail]);
  return pieces;
}

// --- ByT5 ONNX G2P (zero external tokenizer!) ---
function byt5Encode(text) {
  const bytes = utf8Enc.encode(text);
  const out = new BigInt64Array(bytes.length + 1);
  for (let i = 0; i < bytes.length; i++) out[i] = BigInt(bytes[i] + 3);
  out[bytes.length] = 1n; // EOS
  return out;
}

function byt5Decode(ids) {
  const bytes = [];
  for (const id of ids) {
    if (id >= 3 && id <= 258) bytes.push(id - 3);
  }
  return utf8Dec.decode(new Uint8Array(bytes)).trim();
}

async function runG2PClause(clause, maxLen = 256) {
  const inIds = byt5Encode(clause);
  const srcLen = inIds.length;
  const attn = new BigInt64Array(srcLen).fill(1n);
  const encOut = await g2pEncSess.run({
    input_ids: new ort.Tensor("int64", inIds, [1, srcLen]),
    attention_mask: new ort.Tensor("int64", attn, [1, srcLen]),
  });
  const encHidden = encOut.encoder_hidden_states;
  const attnTensor = new ort.Tensor("int64", attn, [1, srcLen]);

  const decIds = [0]; // decoder_start_token_id = 0
  for (let step = 0; step < maxLen; step++) {
    const dTensor = new ort.Tensor("int64", BigInt64Array.from(decIds.map(BigInt)), [1, decIds.length]);
    const decOut = await g2pDecSess.run({
      decoder_input_ids: dTensor,
      encoder_hidden_states: encHidden,
      encoder_attention_mask: attnTensor,
    });
    const logits = decOut.logits.data;
    const vocab = decOut.logits.dims[2];
    const offset = (decIds.length - 1) * vocab;
    let bestId = 0, bestVal = -Infinity;
    for (let v = 0; v < vocab; v++) {
      const val = logits[offset + v];
      if (val > bestVal) { bestVal = val; bestId = v; }
    }
    if (bestId === 1) break; // EOS
    decIds.push(bestId);
  }
  return byt5Decode(decIds.slice(1));
}

function repr1ToWords(repr1) {
  const words = [];
  for (const rawW of repr1.trim().split(/\s+/)) {
    if (!rawW) continue;
    const m = rawW.match(/^(.*?)-(ye|e)$/);
    let stem = m && m[1] ? m[1].replace(/-/g, "") : rawW.replace(/-/g, "");
    const ez = m && m[1] ? m[2] : "";

    // Drop redundant word-initial '?' before vowel (matches src/tiny_voca/text.py)
    if (stem.length >= 2 && stem[0] === "?" && "aAeoiu".includes(stem[1])) {
      stem = stem.slice(1);
    }

    const phones = [];
    for (let i = 0; i < stem.length; i++) {
      if (i + 1 < stem.length && stem[i] === "z" && stem[i + 1] === "h") {
        phones.push(["ʒ", 0]);
        i++;
        continue;
      }
      const ipa = REPR1_TO_IPA[stem[i]];
      if (ipa) phones.push([ipa, 0]);
    }
    // Primary lexical stress on last vowel of stem
    for (let i = phones.length - 1; i >= 0; i--) {
      if (VOWELS.has(phones[i][0])) { phones[i][1] = 1; break; }
    }
    if (ez) {
      for (const ch of ez) {
        const ipa = REPR1_TO_IPA[ch];
        if (ipa) phones.push([ipa, VOWELS.has(ipa) ? 2 : 0]);
      }
    }
    if (phones.length) words.push(phones);
  }
  return words;
}

async function phonemizeSentence(sentence) {
  const norm = normalizePersian(ensureTerminalMark(sentence));
  const chunks = splitPunctChunks(norm);
  const syms = [BOS];
  const stress = [0];

  for (const [isPunct, val] of chunks) {
    if (isPunct) {
      if (syms[syms.length - 1] !== val && val in symbolToId) {
        syms.push(val);
        stress.push(0);
      }
      continue;
    }
    const repr1 = await runG2PClause(val);
    const words = repr1ToWords(repr1);
    for (const w of words) {
      const prev = syms[syms.length - 1];
      if (phoneSet.has(prev) || prev === UNK) {
        syms.push(SP);
        stress.push(0);
      }
      for (const [ph, lvl] of w) {
        if (phoneSet.has(ph)) {
          syms.push(ph);
          stress.push(lvl);
        } else {
          syms.push(UNK);
          stress.push(0);
        }
      }
    }
  }
  syms.push(EOS);
  stress.push(0);

  if (!syms.some((s) => phoneSet.has(s))) return null;
  const ids = syms.map((s) => symbolToId[s]);
  return { syms, ids, stress };
}

async function synthTokens(ph, speed, pitchShift, pitchRange) {
  const n = ph.ids.length;
  const out = await ttsSess.run({
    tokens: new ort.Tensor("int64", BigInt64Array.from(ph.ids.map(BigInt)), [1, n]),
    stress: new ort.Tensor("int64", BigInt64Array.from(ph.stress.map(BigInt)), [1, n]),
    voice: new ort.Tensor("int64", BigInt64Array.from([0n]), [1]),
    speed: new ort.Tensor("float32", Float32Array.from([speed]), [1]),
    pitch_shift: new ort.Tensor("float32", Float32Array.from([pitchShift]), [1]),
    pitch_range: new ort.Tensor("float32", Float32Array.from([pitchRange]), [1]),
  });
  return out.audio.data;
}

function splitLongSentence(sentence) {
  if (sentence.length <= 180) return [sentence];
  const clauses = sentence.split(/(?<=[،,؛;:—])\s+/).filter(Boolean);
  if (clauses.length > 1) return clauses;
  const words = sentence.split(/\s+/);
  const half = Math.ceil(words.length / 2);
  return [words.slice(0, half).join(" "), words.slice(half).join(" ")];
}

function limitPeak(wave) {
  let maxAbs = 0;
  for (let i = 0; i < wave.length; i++) {
    const a = Math.abs(wave[i]);
    if (a > maxAbs) maxAbs = a;
  }
  if (maxAbs <= PEAK || maxAbs === 0) return wave;
  const scale = PEAK / maxAbs;
  const out = new Float32Array(wave.length);
  for (let i = 0; i < wave.length; i++) out[i] = wave[i] * scale;
  return out;
}

const CACHE_URLS = [
  "assets/g2p/g2p_encoder.onnx",
  "assets/g2p/g2p_decoder.onnx",
  "assets/tiny-ava-parisa.onnx",
];

async function isCached() {
  try {
    const store = await caches.open(CACHE_NAME);
    const hits = await Promise.all(CACHE_URLS.map((u) => store.match(u)));
    return hits.every(Boolean);
  } catch (_) {
    return false;
  }
}

self.onmessage = async (ev) => {
  const msg = ev.data;
  try {
    if (msg.type === "check_cache") {
      const cached = await isCached();
      postMessage({ type: "cache_status", cached });
      if (cached) await loadAll();
      return;
    }
    if (msg.type === "load") {
      await loadAll();
      return;
    }
    if (msg.type === "cancel") {
      cancelledId = msg.id;
      return;
    }
    if (msg.type === "clear_cache") {
      await caches.delete(CACHE_NAME);
      g2pEncSess = null;
      g2pDecSess = null;
      ttsSess = null;
      postMessage({ type: "cache_cleared" });
      return;
    }
    if (msg.type === "speak") {
      const { id, text, speed = 1.0, pitchShift = 0.0, pitchRange = 1.05 } = msg;
      await loadAll();

      const sents = splitSentences(text).flatMap(splitLongSentence);
      if (!sents.length) {
        postMessage({ type: "error", id, message: "Please enter some Persian text." });
        return;
      }

      const tStart = performance.now();
      let g2pMs = 0, ttsMs = 0;
      const waves = [];
      const allPhones = [];

      for (let i = 0; i < sents.length; i++) {
        if (cancelledId === id) return;
        sendStatus(`Synthesizing sentence ${i + 1} / ${sents.length}...`);

        const t0 = performance.now();
        const ph = await phonemizeSentence(sents[i]);
        g2pMs += performance.now() - t0;
        if (!ph) continue;
        if (ph.ids.length > maxTokens) {
          throw new Error("Sentence too long; please add a period or comma.");
        }

        const t1 = performance.now();
        const rawWave = await synthTokens(ph, speed, pitchShift, pitchRange);
        ttsMs += performance.now() - t1;

        const cleanWave = limitPeak(rawWave);
        waves.push(cleanWave);
        allPhones.push(ph.syms.join(" "));

        // Stream each sentence back immediately for low-latency playback
        const copy = new Float32Array(cleanWave);
        postMessage({ type: "sentence", id, index: i, total: sents.length, audio: copy, sampleRate: SAMPLE_RATE }, [copy.buffer]);
      }

      if (!waves.length) {
        postMessage({ type: "error", id, message: "Nothing pronounceable found in Persian text." });
        return;
      }

      // Join sentences with a short 120ms silence gap
      const gap = Math.round(SAMPLE_RATE * 0.12);
      let totalLen = 0;
      waves.forEach((w, idx) => { totalLen += w.length + (idx < waves.length - 1 ? gap : 0); });
      const full = new Float32Array(totalLen);
      let off = 0;
      waves.forEach((w, idx) => {
        full.set(w, off);
        off += w.length + (idx < waves.length - 1 ? gap : 0);
      });

      const totalMs = performance.now() - tStart;
      postMessage(
        {
          type: "done",
          id,
          audio: full,
          sampleRate: SAMPLE_RATE,
          stats: {
            audioSec: totalLen / SAMPLE_RATE,
            totalSec: totalMs / 1000,
            g2pSec: g2pMs / 1000,
            ttsSec: ttsMs / 1000,
            sentences: waves.length,
          },
          phones: allPhones.join("\n"),
        },
        [full.buffer]
      );
    }
  } catch (err) {
    postMessage({ type: "error", id: msg.id, message: String(err && err.message ? err.message : err) });
  }
};
