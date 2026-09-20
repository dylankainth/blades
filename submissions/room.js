/**
 * "The Room" — Klick's signature motif.
 *
 * A thousand faint dots (an event) and the introductions between them. It
 * plays in two beats:
 *
 *   1. THE HAIRBALL — what a system that precomputed a global shortlist
 *      would have to reason about. Lines everywhere, unreadable on purpose.
 *   2. THE COLLAPSE — the hairball burns off and a handful of amber
 *      connections remain: the pairs who actually crossed paths.
 *
 * It is the 499,500 -> 19 argument as motion rather than as a bullet, and
 * it is specific to this product in a way a stock gradient is not.
 *
 * Usage:  <canvas data-room data-dots="1000" data-links="5"></canvas>
 * Replay: canvas.__room.play()
 */

(function () {
    const REDUCED = matchMedia('(prefers-reduced-motion: reduce)').matches;

    function build(canvas) {
        const ctx = canvas.getContext('2d');
        const dots = +(canvas.dataset.dots || 1000);
        const realLinks = +(canvas.dataset.links || 5);
        // Enough strands to read as "impossibly many" without asking the
        // browser to stroke half a million lines.
        const hairball = +(canvas.dataset.hairball || 2200);

        let w = 0, h = 0, dpr = 1;
        let pts = [];
        let noise = [];
        let real = [];
        let raf = null;
        let t0 = 0;

        function layout() {
            dpr = Math.min(devicePixelRatio || 1, 2);
            const r = canvas.getBoundingClientRect();
            w = r.width; h = r.height;
            canvas.width = Math.round(w * dpr);
            canvas.height = Math.round(h * dpr);
            ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

            // Poisson-ish scatter: jittered grid keeps the field even without
            // the clumping a pure random scatter gives you.
            pts = [];
            const cols = Math.ceil(Math.sqrt(dots * (w / h)));
            const rows = Math.ceil(dots / cols);
            const cw = w / cols, ch = h / rows;
            for (let y = 0; y < rows; y++) {
                for (let x = 0; x < cols; x++) {
                    if (pts.length >= dots) break;
                    pts.push({
                        x: (x + 0.5 + (Math.random() - 0.5) * 0.85) * cw,
                        y: (y + 0.5 + (Math.random() - 0.5) * 0.85) * ch,
                        // Staggered by position so the field "develops"
                        // rather than popping in all at once.
                        d: (x / cols) * 0.35 + Math.random() * 0.25,
                    });
                }
            }

            noise = [];
            for (let i = 0; i < hairball; i++) {
                noise.push([
                    pts[(Math.random() * pts.length) | 0],
                    pts[(Math.random() * pts.length) | 0],
                ]);
            }

            // The survivors: pairs picked from nearby points, because the
            // whole claim is that real matches are ones who physically
            // crossed paths.
            real = [];
            let guard = 0;
            while (real.length < realLinks && guard++ < 4000) {
                const a = pts[(Math.random() * pts.length) | 0];
                const b = pts[(Math.random() * pts.length) | 0];
                if (a === b) continue;
                const dist = Math.hypot(a.x - b.x, a.y - b.y);
                if (dist > w * 0.06 && dist < w * 0.17) real.push([a, b]);
            }
        }

        function frame(now) {
            if (!t0) t0 = now;
            const t = (now - t0) / 1000;

            ctx.clearRect(0, 0, w, h);

            // Beat 1: the field appears.
            const fieldIn = clamp((t - 0.1) / 1.1);
            // Beat 2: the hairball floods in, then burns off.
            const flood = clamp((t - 0.7) / 0.9);
            const burn = clamp((t - 2.5) / 1.3);
            const noiseAlpha = flood * (1 - burn) * 0.5;
            // Beat 3: the survivors draw themselves.
            const draw = clamp((t - 3.1) / 1.1);

            if (noiseAlpha > 0.002) {
                ctx.strokeStyle = `rgba(28,27,26,${noiseAlpha * 0.055})`;
                ctx.lineWidth = 0.5;
                ctx.beginPath();
                const shown = (noise.length * flood) | 0;
                for (let i = 0; i < shown; i++) {
                    const [a, b] = noise[i];
                    ctx.moveTo(a.x, a.y);
                    ctx.lineTo(b.x, b.y);
                }
                ctx.stroke();
            }

            for (const p of pts) {
                const a = clamp((fieldIn - p.d) / 0.5);
                if (a <= 0) continue;
                ctx.fillStyle = `rgba(28,27,26,${0.17 * a})`;
                ctx.beginPath();
                ctx.arc(p.x, p.y, 1.25, 0, 6.2832);
                ctx.fill();
            }

            if (draw > 0) {
                for (let i = 0; i < real.length; i++) {
                    const [a, b] = real[i];
                    const local = clamp((draw - i * 0.11) / 0.45);
                    if (local <= 0) continue;

                    ctx.strokeStyle = `rgba(201,123,69,${0.85 * local})`;
                    ctx.lineWidth = 1.6;
                    ctx.beginPath();
                    ctx.moveTo(a.x, a.y);
                    ctx.lineTo(a.x + (b.x - a.x) * local, a.y + (b.y - a.y) * local);
                    ctx.stroke();

                    for (const p of [a, b]) {
                        ctx.fillStyle = `rgba(201,123,69,${local})`;
                        ctx.beginPath();
                        ctx.arc(p.x, p.y, 3.4, 0, 6.2832);
                        ctx.fill();
                        // Soft halo so the survivors read from the back row.
                        ctx.fillStyle = `rgba(201,123,69,${0.13 * local})`;
                        ctx.beginPath();
                        ctx.arc(p.x, p.y, 11, 0, 6.2832);
                        ctx.fill();
                    }
                }
            }

            if (t < 5.2) raf = requestAnimationFrame(frame);
        }

        function still() {
            // prefers-reduced-motion: the end state, no animation.
            ctx.clearRect(0, 0, w, h);
            for (const p of pts) {
                ctx.fillStyle = 'rgba(28,27,26,0.17)';
                ctx.beginPath();
                ctx.arc(p.x, p.y, 1.25, 0, 6.2832);
                ctx.fill();
            }
            for (const [a, b] of real) {
                ctx.strokeStyle = 'rgba(201,123,69,0.85)';
                ctx.lineWidth = 1.6;
                ctx.beginPath();
                ctx.moveTo(a.x, a.y);
                ctx.lineTo(b.x, b.y);
                ctx.stroke();
                for (const p of [a, b]) {
                    ctx.fillStyle = 'rgba(201,123,69,1)';
                    ctx.beginPath();
                    ctx.arc(p.x, p.y, 3.4, 0, 6.2832);
                    ctx.fill();
                }
            }
        }

        function play() {
            if (raf) cancelAnimationFrame(raf);
            t0 = 0;
            if (REDUCED) still();
            else raf = requestAnimationFrame(frame);
        }

        layout();
        canvas.__room = { play, layout };

        let rt;
        addEventListener('resize', () => {
            clearTimeout(rt);
            rt = setTimeout(() => { layout(); play(); }, 180);
        });

        return canvas.__room;
    }

    function clamp(v) { return v < 0 ? 0 : v > 1 ? 1 : v; }

    function init() {
        document.querySelectorAll('canvas[data-room]').forEach((c) => {
            const room = build(c);
            // Replay whenever its slide comes into view, so the argument
            // lands every time you walk back to it mid-Q&A.
            new IntersectionObserver((entries) => {
                entries.forEach((e) => { if (e.isIntersecting) room.play(); });
            }, { threshold: 0.5 }).observe(c);
        });
    }

    if (document.readyState === 'loading') {
        addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
