/**
 * Cloud Functions v2 secret/param declarations.
 *
 * These are bound lazily — declaring them here does not require the values
 * to exist at deploy time for `functions:config` style, but they DO need to
 * be set via `firebase functions:secrets:set <NAME>` (or present in a local
 * `.env` file read by the emulator) before the functions that reference them
 * will run successfully. See functions/README.md.
 */
import { defineSecret } from "firebase-functions/params";

export const META_MODEL_API_KEY = defineSecret("META_MODEL_API_KEY");

/** Deepgram API key: speech-to-text + text-to-speech. See lib/deepgram.ts. */
export const DEEPGRAM_API_KEY = defineSecret("DEEPGRAM_API_KEY");

/**
 * Parallel AI Search API key (https://api.parallel.ai), used only by
 * importSocialContext.ts's Instagram provider (see lib/parallel.ts).
 * Replaced an earlier Instagram Business Login/Graph API branch that
 * only worked for tester-role Professional accounts — this is a plain
 * public web search keyed on the twin's own Instagram handle, so it
 * works for any user, no OAuth needed.
 */
export const PARALLEL_API_KEY = defineSecret("PARALLEL_API_KEY");
