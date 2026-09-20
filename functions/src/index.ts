/**
 * Cloud Functions v2 entry point. Export every deployable function here.
 */
export { onboardingChat } from "./onboardingChat";
export { onCheckin } from "./onCheckin";
export { negotiateTwins } from "./negotiateTwins";
export { notifyMatch } from "./notifyMatch";
export { submitMatchApproval } from "./submitMatchApproval";
// Kindred badge (ESP32-S3-BOX-3) — see hardware/box/CONTRACT.md
export { boxState } from "./boxState";
export { boxEvent } from "./boxEvent";
export { pairBox, resetDemo } from "./boxPairing";
// Voice via Deepgram — see voice.ts
export { transcribeSpeech, speakText } from "./voice";
