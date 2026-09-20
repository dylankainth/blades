/**
 * Voice in and out of the app, via Deepgram.
 *
 *  - transcribeSpeech: the onboarding chat's mic button. The app records one
 *    utterance, we return its text, and the app sends that text through the
 *    normal onboardingChat flow — so a spoken turn and a typed turn are
 *    indistinguishable to everything downstream.
 *  - speakText: the twin's voice. Used to read onboarding replies back, and to
 *    whisper "your twin found someone, here's why" into earbuds when a match
 *    lands (the badge stays silent on purpose: it faces strangers).
 *
 * Both are callable by signed-in users only and hard-capped in size, because
 * each call spends Deepgram credit.
 */
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { DEEPGRAM_API_KEY } from "./lib/secrets";
import { speak, transcribe } from "./lib/deepgram";

/** ~60 s of 32 kbps AAC is ~240 KB; this leaves generous headroom. */
const MAX_AUDIO_BYTES = 2 * 1024 * 1024;
/** Deepgram's own limit is 2000; a whispered reason is one or two sentences. */
const MAX_SPEAK_CHARS = 600;
const ALLOWED_AUDIO_TYPES = ["audio/mp4", "audio/aac", "audio/webm", "audio/ogg", "audio/wav"];

interface TranscribeRequest {
  audioBase64: string;
  mimeType?: string;
}

export const transcribeSpeech = onCall<TranscribeRequest, Promise<{ text: string }>>(
  { secrets: [DEEPGRAM_API_KEY], memory: "256MiB", timeoutSeconds: 60 },
  async (request) => {
    if (!request.auth?.uid) {
      throw new HttpsError("unauthenticated", "Sign in to use voice.");
    }
    const { audioBase64, mimeType = "audio/mp4" } = request.data ?? {};
    if (typeof audioBase64 !== "string" || audioBase64.length === 0) {
      throw new HttpsError("invalid-argument", "audioBase64 is required.");
    }
    if (!ALLOWED_AUDIO_TYPES.includes(mimeType)) {
      throw new HttpsError("invalid-argument", `Unsupported audio type ${mimeType}.`);
    }
    const audio = Buffer.from(audioBase64, "base64");
    if (audio.length === 0 || audio.length > MAX_AUDIO_BYTES) {
      throw new HttpsError("invalid-argument", "Recording is empty or too long.");
    }

    try {
      return { text: await transcribe(DEEPGRAM_API_KEY.value(), audio, mimeType) };
    } catch (err) {
      console.error("transcribeSpeech failed:", err);
      throw new HttpsError("internal", "Couldn't hear that. Try again or type it.");
    }
  },
);

interface SpeakRequest {
  text: string;
}

export const speakText = onCall<SpeakRequest, Promise<{ audioBase64: string; mimeType: string }>>(
  { secrets: [DEEPGRAM_API_KEY], memory: "256MiB", timeoutSeconds: 60 },
  async (request) => {
    if (!request.auth?.uid) {
      throw new HttpsError("unauthenticated", "Sign in to use voice.");
    }
    const text = typeof request.data?.text === "string" ? request.data.text.trim() : "";
    if (text.length === 0 || text.length > MAX_SPEAK_CHARS) {
      throw new HttpsError("invalid-argument", `text must be 1-${MAX_SPEAK_CHARS} characters.`);
    }

    try {
      const audio = await speak(DEEPGRAM_API_KEY.value(), text);
      return { audioBase64: audio.toString("base64"), mimeType: "audio/mpeg" };
    } catch (err) {
      console.error("speakText failed:", err);
      throw new HttpsError("internal", "Couldn't generate speech.");
    }
  },
);
