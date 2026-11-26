// tiny-ava v2 Web Worker: Unified 4-Voice Bilingual (FA + EN) 100% In-Browser TTS
// Models: tiny-ava-v2.onnx (34.4 MB) + persian-g2p-tiny int8 ONNX (8.4 MB) + lexicon_en.tsv (3.3 MB)

const ORT_CDN = "https://cdn.jsdelivr.net/npm/onnxruntime-web@1.20.1/dist/";
importScripts(ORT_CDN + "ort.wasm.min.js");

const CACHE_NAME = "tiny-ava-v2-prod-1";
const CACHE_URLS = [
  "assets/g2p/g2p_encoder.onnx",
  "assets/g2p/g2p_decoder.onnx",
  "assets/lexicon_en.tsv",
  "assets/tiny-ava-v2.onnx",
];
const SAMPLE_RATE = 24000;
const PEAK = 0.99;

// Exact 84-symbol unified FA + EN table (matches results/v2_bilingual/tiny-ava-v2.json)
const SYMBOLS = [
  "<pad>", "<bos>", "<eos>", "<sp>", "<unk>",
  ",", ".", "?", "!", ";", ":", '"', "'", "(", ")", "—", "…",
  "p", "b", "t", "d", "k", "ɡ", "f", "v", "θ", "ð", "s", "z", "ʃ", "ʒ", "h",
  "m", "n", "ŋ", "l", "ɹ", "j", "w", "tʃ", "dʒ", "ɾ", "ʔ", "q", "x", "r",
  "ə", "ɐ", "ɚ", "ᵻ", "ɪ", "i", "iː", "ɛ", "æ", "ʌ", "ɑː", "ɔ", "ɔː", "ʊ", "uː", "ɜː",
  "eɪ", "aɪ", "ɔɪ", "oʊ", "aʊ", "iə", "oː",
  "ɑːɹ", "ɔːɹ", "oːɹ", "ɪɹ", "ɛɹ", "ʊɹ", "aɪɚ", "aɪə", "əl", "n̩",
  "a", "e", "o", "u", "ɑ",
];
const BOS = "<bos>", EOS = "<eos>", SP = "<sp>", UNK = "<unk>";
const PUNCTUATION = new Set([",", ".", "?", "!", ";", ":", '"', "'", "(", ")", "—", "…"]);
const SYMBOL_TO_ID = Object.fromEntries(SYMBOLS.map((s, i) => [s, i]));
const PHONE_SET = new Set(SYMBOLS.slice(17));
const VOWELS = new Set(SYMBOLS.slice(46));

const VOICES = ["parisa", "voa_man", "mana", "en_f2"];

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
  "،": ",", "؟": "?", "؛": ";", "«": '"', "»": '"',
  "‘": "'", "’": "'", "“": '"', "”": '"', "–": "—", "―": "—",
};

const EN_LETTERS = {
  a: "ˈeɪ", b: "b ˈiː", c: "s ˈiː", d: "d ˈiː", e: "ˈiː", f: "ˈɛ f", g: "dʒ ˈiː", h: "ˈeɪ tʃ",
  i: "ˈaɪ", j: "dʒ ˈeɪ", k: "k ˈeɪ", l: "ˈɛ l", m: "ˈɛ m", n: "ˈɛ n", o: "ˈoʊ", p: "p ˈiː",
  q: "k j ˈuː", r: "ˈɑːɹ", s: "ˈɛ s", t: "t ˈiː", u: "j ˈuː", v: "v ˈiː", w: "d ˈʌ b əl j uː",
  x: "ˈɛ k s", y: "w ˈaɪ", z: "z ˈiː",
};

const utf8Enc = new TextEncoder();
const utf8Dec = new TextDecoder("utf-8", { fatal: false });

let g2pEncSess = null, g2pDecSess = null, ttsSess = null, enLex = null;
let cancelledId = -1;

function sendStatus(text, progress = null) {
  postMessage({ type: "status", text, progress });
}

async function isCached() {
  try {
    const store = await caches.open(CACHE_NAME);
    const hits = await Promise.all(CACHE_URLS.map((u) => store.match(u)));
    return hits.every(Boolean);
  } catch (_) { return false; }
}

async function fetchBytes(url, label, baseP, spanP) {
  let store = null;
  try {
    store = await caches.open(CACHE_NAME);
    const hit = await store.match(url);
    if (hit) {
      sendStatus(`${label} (cached)`, baseP + spanP);
      return new Uint8Array(await hit.arrayBuffer());
    }
  } catch (_) { store = null; }

  const resp = await fetch(url);
  if (!resp.ok) throw new Error(`HTTP ${resp.status} fetching ${url}`);
  const total = Number(resp.headers.get("content-length")) || 0;
  if (!resp.body || !total) {
    const buf = new Uint8Array(await resp.arrayBuffer());
    if (store) store.put(url, new Response(buf)).catch(() => {});
    sendStatus(label, baseP + spanP);
    return buf;
  }
  const reader = resp.body.getReader();
  const chunks = [];
  let got = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value);
    got += value.length;
    sendStatus(label, baseP + spanP * Math.min(1, got / total));
  }
  const out = new Uint8Array(got);
  let off = 0;
  for (const c of chunks) { out.set(c, off); off += c.length; }
  if (store) store.put(url, new Response(out)).catch(() => {});
  return out;
}

async function loadAll() {
  if (ttsSess && g2pEncSess && g2pDecSess && enLex) {
    postMessage({ type: "ready" });
    return;
  }
  ort.env.wasm.wasmPaths = ORT_CDN;
  ort.env.wasm.numThreads = 1;

  const encBytes = await fetchBytes("assets/g2p/g2p_encoder.onnx", "Loading Persian G2P encoder (3.5 MB)", 0.0, 0.12);
  const decBytes = await fetchBytes("assets/g2p/g2p_decoder.onnx", "Loading Persian G2P decoder (5.3 MB)", 0.12, 0.16);
  const lexBytes = await fetchBytes("assets/lexicon_en.tsv", "Loading English lexicon (3.3 MB)", 0.28, 0.10);
  const ttsBytes = await fetchBytes("assets/tiny-ava-v2.onnx", "Loading tiny-ava v2 4-voice model (34.4 MB)", 0.38, 0.54);

  sendStatus("Initializing ONNX Runtime Web...", 0.94);
  enLex = new Map();
  for (const line of utf8Dec.decode(lexBytes).split("\n")) {
    const t = line.indexOf("\t");
    if (t > 0) enLex.set(line.slice(0, t), line.slice(t + 1).trim());
  }
  g2pEncSess = await ort.InferenceSession.create(encBytes, { executionProviders: ["wasm"] });
  g2pDecSess = await ort.InferenceSession.create(decBytes, { executionProviders: ["wasm"] });
  ttsSess = await ort.InferenceSession.create(ttsBytes, { executionProviders: ["wasm"] });

  sendStatus("Ready — all 4 voices loaded locally.", 1.0);
  postMessage({ type: "ready" });
}

function normalizeText(text) {
  let s = text.normalize("NFC");
  let out = "";
  for (const ch of s) out += (ch in CHAR_MAP) ? CHAR_MAP[ch] : ch;
  out = out.replace(/[\u064b-\u064d\u0652-\u065f\u0670]/g, "");
  out = out.replace(/\.{3,}/g, "…").replace(/-{2,}|\s-\s/g, "—");
  out = out.replace(/\u200c+/g, "\u200c").replace(/\s+\u200c|\u200c\s+/g, " ");
  return out.replace(/\s+/g, " ").trim();
}

function splitSentences(text) {
  const cleaned = text.replace(/[\s\ufeff]+/g, " ").trim();
  if (!cleaned) return [];
  const raw = cleaned.split(/(?<=[.!؟?…]["')\]»]?)\s+/).map(s => s.trim()).filter(s => /[\p{L}\p{N}]/u.test(s));
  // Split long sentences (> 95 chars) at commas/semicolons so first audio streams in ~120ms!
  const out = [];
  for (const s of raw) {
    if (s.length <= 95) { out.push(s); continue; }
    const clauses = s.split(/(?<=[،,؛;:—])\s+/).map(c => c.trim()).filter(c => /[\p{L}\p{N}]/u.test(c));
    if (clauses.length > 1) out.push(...clauses);
    else out.push(s);
  }
  return out;
}

function ensureMark(text) {
  const s = text.trim();
  if (!s) return s;
  return ".!؟?…،,;؛:".includes(s.slice(-1)) ? s : s + ".";
}

function splitPunctChunks(text) {
  const pieces = [];
  let start = 0;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (!PUNCTUATION.has(ch)) continue;
    const prev = i > 0 ? text[i - 1] : "";
    const nxt = i + 1 < text.length ? text[i + 1] : "";
    if (ch === "'" && /[A-Za-z0-9]/.test(prev) && /[A-Za-z0-9]/.test(nxt)) continue;
    if (".,:".includes(ch) && /\d/.test(prev) && /\d/.test(nxt)) continue;
    const seg = text.slice(start, i).trim();
    if (seg) pieces.push([false, seg]);
    pieces.push([true, ch]);
    start = i + 1;
  }
  const tail = text.slice(start).trim();
  if (tail) pieces.push([false, tail]);
  return pieces;
}

function splitLangSpans(piece) {
  const hasFa = /[\u0600-\u06FF]/.test(piece);
  const hasEn = /[A-Za-z]/.test(piece);
  if (hasFa && !hasEn) return [["fa", piece]];
  if (hasEn && !hasFa) return [["en", piece]];
  if (!hasFa && !hasEn) return [["fa", piece]];
  const spans = [];
  let cur = null, buf = [];
  for (const tok of piece.split(/\s+/)) {
    const lang = /[\u0600-\u06FF]/.test(tok) ? "fa" : (/[A-Za-z]/.test(tok) ? "en" : (cur || "fa"));
    if (cur && lang !== cur) { spans.push([cur, buf.join(" ")]); buf = []; }
    cur = lang; buf.push(tok);
  }
  if (buf.length && cur) spans.push([cur, buf.join(" ")]);
  return spans;
}

function byt5Encode(text) {
  const bytes = utf8Enc.encode(text);
  const out = new BigInt64Array(bytes.length + 1);
  for (let i = 0; i < bytes.length; i++) out[i] = BigInt(bytes[i] + 3);
  out[bytes.length] = 1n;
  return out;
}

function byt5Decode(ids) {
  const bytes = [];
  for (const id of ids) if (id >= 3 && id <= 258) bytes.push(id - 3);
  return utf8Dec.decode(new Uint8Array(bytes)).trim();
}

async function runFaG2P(clause, maxLen = 256) {
  const inIds = byt5Encode(clause);
  const srcLen = inIds.length;
  const attn = new BigInt64Array(srcLen).fill(1n);
  const encOut = await g2pEncSess.run({
    input_ids: new ort.Tensor("int64", inIds, [1, srcLen]),
    attention_mask: new ort.Tensor("int64", attn, [1, srcLen]),
  });
  const encHidden = encOut.encoder_hidden_states;
  const attnTensor = new ort.Tensor("int64", attn, [1, srcLen]);
  const decIds = [0];
  for (let step = 0; step < maxLen; step++) {
    const dTensor = new ort.Tensor("int64", BigInt64Array.from(decIds.map(BigInt)), [1, decIds.length]);
    const decOut = await g2pDecSess.run({
      decoder_input_ids: dTensor, encoder_hidden_states: encHidden, encoder_attention_mask: attnTensor,
    });
    const logits = decOut.logits.data;
    const vocab = decOut.logits.dims[2];
    const off = (decIds.length - 1) * vocab;
    let bestId = 0, bestVal = -Infinity;
    for (let v = 0; v < vocab; v++) {
      if (logits[off + v] > bestVal) { bestVal = logits[off + v]; bestId = v; }
    }
    if (bestId === 1) break;
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
    if (stem.length >= 2 && stem[0] === "?" && "aAeoiu".includes(stem[1])) stem = stem.slice(1);
    const phones = [];
    for (let i = 0; i < stem.length; i++) {
      if (i + 1 < stem.length && stem[i] === "z" && stem[i + 1] === "h") { phones.push(["ʒ", 0]); i++; continue; }
      const ipa = REPR1_TO_IPA[stem[i]];
      if (ipa) phones.push([ipa, 0]);
    }
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

function enRuleFallback(word) {
  const out = [];
  for (let i = 0; i < word.length;) {
    const r = word.slice(i);
    if (r.startsWith("tion")) { out.push("ʃ", "ə", "n"); i += 4; }
    else if (r.startsWith("ch")) { out.push("tʃ"); i += 2; }
    else if (r.startsWith("sh")) { out.push("ʃ"); i += 2; }
    else if (r.startsWith("th")) { out.push("θ"); i += 2; }
    else if (r.startsWith("ph")) { out.push("f"); i += 2; }
    else if (r.startsWith("ng")) { out.push("ŋ"); i += 2; }
    else if (r.startsWith("ee") || r.startsWith("ea")) { out.push("iː"); i += 2; }
    else if (r.startsWith("oo")) { out.push("uː"); i += 2; }
    else if (r.startsWith("ou") || r.startsWith("ow")) { out.push("aʊ"); i += 2; }
    else if (r.startsWith("ai") || r.startsWith("ay")) { out.push("eɪ"); i += 2; }
    else if (r.startsWith("ck")) { out.push("k"); i += 2; }
    else {
      const m = { a:"æ",b:"b",c:"k",d:"d",e:"ɛ",f:"f",g:"ɡ",h:"h",i:"ɪ",j:"dʒ",k:"k",l:"l",m:"m",n:"n",o:"ɑː",p:"p",q:"k",r:"ɹ",s:"s",t:"t",u:"ʌ",v:"v",w:"w",x:"k s",y:"j",z:"z" }[word[i]];
      if (m) out.push(...m.split(" "));
      i++;
    }
  }
  return out.join(" ");
}

function englishToWords(span) {
  const spaced = span.replace(/([a-z])([A-Z])/g, "$1 $2");
  const words = [];
  for (const m of spaced.matchAll(/[A-Za-z]+(?:'[A-Za-z]+)?/g)) {
    const w = m[0], low = w.toLowerCase();
    let ipaStr = enLex.get(low);
    if (!ipaStr) {
      if ((w.length >= 2 && w.length <= 4 && w === w.toUpperCase() && !/[AEIOU]/.test(w)) || ["AI","UI","UX","ID","OS","TV","PC","OK","FT"].includes(w)) {
        ipaStr = [...low].map(c => EN_LETTERS[c] || "").filter(Boolean).join(" ");
      } else if (low.endsWith("s") && enLex.has(low.slice(0, -1))) {
        ipaStr = enLex.get(low.slice(0, -1)) + " z";
      } else if (low.endsWith("ing") && (enLex.has(low.slice(0, -3)) || enLex.has(low.slice(0, -3) + "e"))) {
        ipaStr = (enLex.get(low.slice(0, -3)) || enLex.get(low.slice(0, -3) + "e")) + " ɪ ŋ";
      } else if (low.endsWith("ed") && (enLex.has(low.slice(0, -2)) || enLex.has(low.slice(0, -2) + "e"))) {
        ipaStr = (enLex.get(low.slice(0, -2)) || enLex.get(low.slice(0, -2) + "e")) + " d";
      } else {
        ipaStr = enRuleFallback(low);
      }
    }
    const units = [];
    for (let u of ipaStr.split(/\s+/)) {
      if (!u) continue;
      let lvl = 0;
      if (u.startsWith("ˈ")) { lvl = 1; u = u.slice(1); }
      else if (u.startsWith("ˌ")) { lvl = 2; u = u.slice(1); }
      if (PHONE_SET.has(u)) units.push([u, lvl]);
    }
    if (units.length) words.push(units);
  }
  return words;
}

async function phonemizeSentence(sentence) {
  const norm = normalizeText(ensureMark(sentence));
  const pChunks = splitPunctChunks(norm);
  const syms = [BOS], stress = [0];
  for (const [isPunct, val] of pChunks) {
    if (isPunct) {
      if (syms[syms.length - 1] !== val && val in SYMBOL_TO_ID) { syms.push(val); stress.push(0); }
      continue;
    }
    for (const [lang, span] of splitLangSpans(val)) {
      const words = lang === "fa" ? repr1ToWords(await runFaG2P(span)) : englishToWords(span);
      for (const w of words) {
        const prev = syms[syms.length - 1];
        if (PHONE_SET.has(prev) || prev === UNK) { syms.push(SP); stress.push(0); }
        for (const [ph, lvl] of w) {
          syms.push(PHONE_SET.has(ph) ? ph : UNK);
          stress.push(PHONE_SET.has(ph) ? lvl : 0);
        }
      }
    }
  }
  syms.push(EOS); stress.push(0);
  if (!syms.some(s => PHONE_SET.has(s))) return null;
  return { syms, ids: syms.map(s => SYMBOL_TO_ID[s]), stress };
}

function limitPeak(wave) {
  let m = 0;
  for (let i = 0; i < wave.length; i++) { const a = Math.abs(wave[i]); if (a > m) m = a; }
  if (m <= PEAK || m === 0) return wave;
  const sc = PEAK / m, out = new Float32Array(wave.length);
  for (let i = 0; i < wave.length; i++) out[i] = wave[i] * sc;
  return out;
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
    if (msg.type === "clear_cache") {
      await caches.delete(CACHE_NAME);
      g2pEncSess = null; g2pDecSess = null; ttsSess = null; enLex = null;
      postMessage({ type: "cache_cleared" });
      return;
    }
    if (msg.type === "load") { await loadAll(); return; }
    if (msg.type === "cancel") { cancelledId = msg.id; return; }
    if (msg.type === "speak") {
      const { id, text, voice = "parisa", speed = 1.0, pitchShift = 0.0, pitchRange = 1.05 } = msg;
      await loadAll();
      const voiceId = BigInt(Math.max(0, VOICES.indexOf(voice)));
      const sents = splitSentences(text);
      if (!sents.length) { postMessage({ type: "error", id, message: "Please enter text." }); return; }

      const tStart = performance.now();
      let g2pMs = 0, ttsMs = 0, firstAudioMs = null;
      const waves = [], allPhones = [];

      for (let i = 0; i < sents.length; i++) {
        if (cancelledId === id) return;
        sendStatus(`Streaming chunk ${i + 1} / ${sents.length}...`);
        const t0 = performance.now();
        const ph = await phonemizeSentence(sents[i]);
        g2pMs += performance.now() - t0;
        if (!ph) continue;

        const t1 = performance.now();
        const n = ph.ids.length;
        const out = await ttsSess.run({
          tokens: new ort.Tensor("int64", BigInt64Array.from(ph.ids.map(BigInt)), [1, n]),
          stress: new ort.Tensor("int64", BigInt64Array.from(ph.stress.map(BigInt)), [1, n]),
          voice: new ort.Tensor("int64", BigInt64Array.from([voiceId]), [1]),
          speed: new ort.Tensor("float32", Float32Array.from([speed]), [1]),
          pitch_shift: new ort.Tensor("float32", Float32Array.from([pitchShift]), [1]),
          pitch_range: new ort.Tensor("float32", Float32Array.from([pitchRange]), [1]),
        });
        ttsMs += performance.now() - t1;
        const cleanWave = limitPeak(out.audio.data);
        waves.push(cleanWave);
        allPhones.push(ph.syms.join(" "));

        if (firstAudioMs === null) firstAudioMs = performance.now() - tStart;
        // Stream chunk immediately to main thread for instant Web Audio playback!
        const chunkCopy = new Float32Array(cleanWave);
        postMessage(
          {
            type: "chunk",
            id,
            index: i,
            total: sents.length,
            audio: chunkCopy,
            sampleRate: SAMPLE_RATE,
            ttfbSec: firstAudioMs / 1000,
          },
          [chunkCopy.buffer]
        );
      }

      if (!waves.length) {
        postMessage({ type: "error", id, message: "Nothing pronounceable found in text." });
        return;
      }

      const gap = Math.round(SAMPLE_RATE * 0.10);
      let totalLen = 0;
      waves.forEach((w, idx) => { totalLen += w.length + (idx < waves.length - 1 ? gap : 0); });
      const full = new Float32Array(totalLen);
      let off = 0;
      waves.forEach((w, idx) => { full.set(w, off); off += w.length + (idx < waves.length - 1 ? gap : 0); });
      const totalMs = performance.now() - tStart;

      postMessage({
        type: "done", id, audio: full, sampleRate: SAMPLE_RATE,
        stats: {
          audioSec: totalLen / SAMPLE_RATE,
          totalSec: totalMs / 1000,
          ttfbSec: (firstAudioMs || totalMs) / 1000,
          g2pSec: g2pMs / 1000,
          ttsSec: ttsMs / 1000,
          sentences: waves.length,
          voice,
        },
        phones: allPhones.join("\n"),
      }, [full.buffer]);
    }
  } catch (err) {
    postMessage({ type: "error", id: msg.id, message: String(err && err.message ? err.message : err) });
  }
};
