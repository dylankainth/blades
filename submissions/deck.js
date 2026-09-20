/**
 * Deck runtime: keyboard navigation and per-slide entrance timing.
 * Shared by ramp.html and long-lake.html.
 */
(function () {
    const slides = [...document.querySelectorAll('.slide')];
    if (!slides.length) return;

    let i = 0;

    function go(n) {
        i = Math.max(0, Math.min(slides.length - 1, n));
        slides[i].scrollIntoView({ behavior: 'smooth' });
    }

    addEventListener('keydown', (e) => {
        const k = e.key;
        if (k === 'ArrowRight' || k === ' ' || k === 'PageDown') { e.preventDefault(); go(i + 1); }
        else if (k === 'ArrowLeft' || k === 'PageUp') { e.preventDefault(); go(i - 1); }
        else if (k === 'Home') { e.preventDefault(); go(0); }
        else if (k === 'End') { e.preventDefault(); go(slides.length - 1); }
        else if (k.toLowerCase() === 'p') { e.preventDefault(); print(); }
    });

    // Entrance: mark a slide "seen" once it is actually on screen, which
    // both drives the stagger and keeps the index honest when someone
    // scrolls by hand instead of using the arrows.
    const spy = new IntersectionObserver((entries) => {
        entries.forEach((en) => {
            if (!en.isIntersecting) return;
            i = slides.indexOf(en.target);
            en.target.classList.add('seen');
        });
    }, { threshold: 0.45 });

    slides.forEach((s) => spy.observe(s));

    // First slide is visible before the observer's first callback.
    slides[0].classList.add('seen');

    // Printing must never capture a half-played entrance.
    addEventListener('beforeprint', () => slides.forEach((s) => s.classList.add('seen')));
})();
