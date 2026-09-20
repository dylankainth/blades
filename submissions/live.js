/**
 * Live deck numbers.
 *
 * The Ramp claim is a measurement, so the slide reads it off the same
 * Firestore collection the judge dashboard does, live, while you present.
 * If a negotiation happens mid-pitch the number moves on the screen.
 *
 * Failure is the design constraint: venue wifi dies, Firestore is slow,
 * anonymous auth gets rate-limited. Every element keeps whatever static
 * number is already written into the HTML, and only replaces it once real
 * data actually arrives. A dead network yields a deck that looks exactly
 * like a normal, correct deck.
 *
 *   <span data-live="run">19</span>
 *   <span data-live="avoided">99.996</span>
 *   <span data-live="tokens">—</span>
 *   <span data-live-dot></span>     ← goes amber only when truly live
 */

import { initializeApp } from "https://www.gstatic.com/firebasejs/10.7.0/firebase-app.js";
import { getAuth, signInAnonymously } from "https://www.gstatic.com/firebasejs/10.7.0/firebase-auth.js";
import {
    getFirestore, collection, onSnapshot, query, orderBy, limit,
} from "https://www.gstatic.com/firebasejs/10.7.0/firebase-firestore.js";

const firebaseConfig = {
    apiKey: "AIzaSyAMRDIwCL3B4Km3aneTKjiRCF-CFUWQH3E",
    authDomain: "blades-a38f5.firebaseapp.com",
    projectId: "blades-a38f5",
    storageBucket: "blades-a38f5.firebasestorage.app",
    messagingSenderId: "110415722590",
    appId: "1:110415722590:web:48cabe19aec737fe049ccf",
};

const VENUE_ATTENDEES = 1000;
const NAIVE_PAIRS = (VENUE_ATTENDEES * (VENUE_ATTENDEES - 1)) / 2;

const fmt = (n) => Math.round(n).toLocaleString("en-US");

/** Count up to a new value — a number that visibly moves gets looked at. */
function animateTo(el, target, render) {
    const from = Number(el.dataset.n || 0);
    if (from === target) { el.textContent = render(target); return; }
    el.dataset.n = target;
    const t0 = performance.now();
    const dur = 850;
    (function step(now) {
        const p = Math.min(1, (now - t0) / dur);
        // easeOutExpo: fast commit, soft landing.
        const e = p === 1 ? 1 : 1 - Math.pow(2, -10 * p);
        el.textContent = render(from + (target - from) * e);
        if (p < 1) requestAnimationFrame(step);
    })(t0);
}

function set(kind, value) {
    document.querySelectorAll(`[data-live="${kind}"]`).forEach((el) => {
        if (kind === "avoided") {
            animateTo(el, value, (v) => v.toFixed(3) + "%");
        } else {
            animateTo(el, value, (v) => fmt(v));
        }
    });
}

function markLive() {
    document.querySelectorAll("[data-live-dot]").forEach((el) => el.classList.add("is-live"));
}

try {
    const app = initializeApp(firebaseConfig);
    const db = getFirestore(app);
    await signInAnonymously(getAuth(app));

    const q = query(collection(db, "judge_feed"), orderBy("createdAt", "desc"), limit(500));

    onSnapshot(
        q,
        (snap) => {
            const docs = [];
            snap.forEach((d) => docs.push(d.data()));
            if (docs.length === 0) return;

            markLive();
            set("run", docs.length);
            set("avoided", (1 - docs.length / NAIVE_PAIRS) * 100);

            const used = docs.filter((d) => d.usage);
            if (used.length) {
                set("tokens", used.reduce((s, d) => s + (d.usage.totalTokens || 0), 0));
            }

            // Long Lake's proof slide: a real dismissal, showing exactly how
            // little we are willing to publish about it.
            const dismissed = docs.find((d) => d.status !== "confirmed");
            if (dismissed) {
                document.querySelectorAll("[data-live-dismissal]").forEach((el) => {
                    el.classList.add("is-live");
                });
            }
        },
        () => { /* keep the static fallbacks; see file header */ },
    );
} catch {
    /* keep the static fallbacks; see file header */
}
