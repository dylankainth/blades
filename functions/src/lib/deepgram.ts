/**
 * Thin client for Deepgram's REST API: speech-to-text for voice onboarding and
 * text-to-speech so a twin can whisper a match reason into its human's
 * earbuds. Plain fetch, no SDK — two endpoints, both one request each.
 *
 * Muse Spark stays the brain. Deepgram is only ears and mouth: we never use
 * its end-to-end Voice Agent API, which would replace the model that the rest
 * of the product (and the Meta track story) is built on.
 */
const DEEPGRAM_BASE_URL = "https://api.deepgram.com/v1";
const STT_MODEL = "nova-3";
const TTS_VOICE = "aura-2-thalia-en";
/** Speech only needs to be intelligible in an earbud; keep the payload small. */
const TTS_BIT_RATE = 32000;

interface ListenResponse {
  results?: {
    channels?: { alternatives?: { transcript?: string }[] }[];
  };
}

async function failureDetail(response: Response): Promise<string> {
  const body = await response.text().catch(() => "");
  return `${response.status} ${body.slice(0, 300)}`;
}

/** Transcribes one short utterance. Returns "" when nothing was said. */
export async function transcribe(
  apiKey: string,
  audio: Buffer,
  mimeType: string,
): Promise<string> {
  const response = await fetch(
    `${DEEPGRAM_BASE_URL}/listen?model=${STT_MODEL}&smart_format=true`,
    {
      method: "POST",
      headers: { Authorization: `Token ${apiKey}`, "Content-Type": mimeType },
      body: audio,
    },
  );
  if (!response.ok) {
    throw new Error(`Deepgram listen failed: ${await failureDetail(response)}`);
  }
  const data = (await response.json()) as ListenResponse;
  return data.results?.channels?.[0]?.alternatives?.[0]?.transcript?.trim() ?? "";
}

/** Synthesizes speech and returns MP3 bytes. */
export async function speak(apiKey: string, text: string): Promise<Buffer> {
  const response = await fetch(
    `${DEEPGRAM_BASE_URL}/speak?model=${TTS_VOICE}&encoding=mp3&bit_rate=${TTS_BIT_RATE}`,
    {
      method: "POST",
      headers: {
        Authorization: `Token ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ text }),
    },
  );
  if (!response.ok) {
    throw new Error(`Deepgram speak failed: ${await failureDetail(response)}`);
  }
  return Buffer.from(await response.arrayBuffer());
}
