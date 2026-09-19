/**
 * Single shared Firebase Admin SDK instance. Import `db` / `messaging` from
 * here rather than calling initializeApp() in every function file.
 */
import { initializeApp, getApps } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";

if (getApps().length === 0) {
  initializeApp();
}

export const db = getFirestore();
export const messaging = getMessaging();
