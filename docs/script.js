/* ═══════════════════════════════════════════════
   NavigationApp — Website JavaScript
   Handles: navbar scroll, animations, mobile menu,
   GitHub API version fetch, smooth scroll
═══════════════════════════════════════════════ */

const GITHUB_REPO = 'Rohit8149/Navigation_App';
const GITHUB_API  = `https://api.github.com/repos/${GITHUB_REPO}/releases/latest`;

/* ── Navbar: Scroll Effect ── */
const navbar = document.getElementById('navbar');
window.addEventListener('scroll', () => {
  navbar.classList.toggle('scrolled', window.scrollY > 40);
}, { passive: true });

/* ── Mobile Hamburger Menu ── */
const hamburger = document.getElementById('hamburger');
const mobileNav = document.getElementById('mobileNav');

hamburger.addEventListener('click', () => {
  mobileNav.classList.toggle('open');
  const isOpen = mobileNav.classList.contains('open');
  hamburger.setAttribute('aria-expanded', isOpen);
  // Animate bars
  const bars = hamburger.querySelectorAll('span');
  if (isOpen) {
    bars[0].style.transform = 'rotate(45deg) translate(5px, 5px)';
    bars[1].style.opacity   = '0';
    bars[2].style.transform = 'rotate(-45deg) translate(5px, -5px)';
  } else {
    bars[0].style.transform = '';
    bars[1].style.opacity   = '';
    bars[2].style.transform = '';
  }
});

// Close mobile nav on link click
document.querySelectorAll('.mobile-nav-link').forEach(link => {
  link.addEventListener('click', () => {
    mobileNav.classList.remove('open');
    const bars = hamburger.querySelectorAll('span');
    bars[0].style.transform = '';
    bars[1].style.opacity   = '';
    bars[2].style.transform = '';
  });
});

/* ── Smooth Scroll for All Anchor Links ── */
document.querySelectorAll('a[href^="#"]').forEach(anchor => {
  anchor.addEventListener('click', e => {
    const targetId = anchor.getAttribute('href');
    if (targetId === '#') return;
    const target = document.querySelector(targetId);
    if (target) {
      e.preventDefault();
      const offset = 80; // navbar height
      const top = target.getBoundingClientRect().top + window.scrollY - offset;
      window.scrollTo({ top, behavior: 'smooth' });
    }
  });
});

/* ── Intersection Observer: Reveal on Scroll ── */
const revealObserver = new IntersectionObserver((entries) => {
  entries.forEach((entry, i) => {
    if (entry.isIntersecting) {
      // Stagger delay for grid items
      const delay = entry.target.dataset.delay || 0;
      setTimeout(() => {
        entry.target.classList.add('revealed');
      }, delay);
      revealObserver.unobserve(entry.target);
    }
  });
}, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });

// Assign staggered delays and observe
function initReveal() {
  // Feature cards — stagger within each row
  const featureCards = document.querySelectorAll('.feature-card[data-reveal]');
  featureCards.forEach((card, i) => {
    card.dataset.delay = (i % 4) * 80;
    revealObserver.observe(card);
  });

  // Steps — stagger sequentially
  const steps = document.querySelectorAll('.step[data-reveal]');
  steps.forEach((step, i) => {
    step.dataset.delay = i * 100;
    revealObserver.observe(step);
  });

  // Roadmap levels — stagger
  const levels = document.querySelectorAll('.roadmap-level[data-reveal]');
  levels.forEach((level, i) => {
    level.dataset.delay = i * 100;
    revealObserver.observe(level);
  });
}

/* ── GitHub API: Fetch Latest Release Info ── */
async function fetchLatestRelease() {
  const versionText = document.getElementById('version-text');
  const downloadBtn = document.getElementById('download-apk-btn');

  try {
    const response = await fetch(GITHUB_API, {
      headers: { 'Accept': 'application/vnd.github.v3+json' }
    });

    if (!response.ok) throw new Error(`GitHub API error: ${response.status}`);

    const data = await response.json();

    const tag         = data.tag_name  || 'v1.0';
    const name        = data.name      || tag;
    const publishedAt = data.published_at ? new Date(data.published_at) : null;
    const assets      = data.assets    || [];

    // Format date
    const dateStr = publishedAt
      ? publishedAt.toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' })
      : '';

    // Update version badge
    versionText.textContent = `Latest: ${tag}${dateStr ? '  ·  Released ' + dateStr : ''}`;

    // Find APK asset and get its size
    const apkAsset = assets.find(a => a.name.endsWith('.apk'));
    if (apkAsset) {
      const sizeMB = (apkAsset.size / (1024 * 1024)).toFixed(1);
      // Update download button label with size
      downloadBtn.innerHTML = `
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
          <polyline points="7 10 12 15 17 10"/>
          <line x1="12" y1="15" x2="12" y2="3"/>
        </svg>
        Download ${tag} APK &nbsp;<span style="opacity:0.7;font-size:0.85em">(${sizeMB} MB)</span>
      `;
      // Point directly at the actual asset URL
      downloadBtn.href = apkAsset.browser_download_url;
    }

  } catch (err) {
    // Fallback — keep static v1.0 info
    console.warn('Could not fetch GitHub release info:', err.message);
    versionText.textContent = 'Latest: v1.0  ·  Check GitHub for updates';
    // Keep the /releases/latest/download/ fallback URL already in the HTML
  }
}

/* ── Typed-text animation in hero badge ── */
function typewriterEffect() {
  const commands = [
    '"Open Wi-Fi settings"',
    '"Change font size"',
    '"Enable Dark Mode"',
    '"Open Battery Saver"',
    '"Go to Display settings"',
  ];
  // Find any .code-inline in the steps section
  const firstCode = document.querySelector('.step-content .code-inline');
  if (!firstCode) return;

  let cmdIndex = 0;
  let charIndex = 0;
  let deleting  = false;
  const speed   = { type: 60, delete: 35, pause: 1800 };

  function tick() {
    const current = commands[cmdIndex];
    if (!deleting) {
      firstCode.textContent = current.substring(0, charIndex + 1);
      charIndex++;
      if (charIndex === current.length) {
        deleting = true;
        setTimeout(tick, speed.pause);
        return;
      }
    } else {
      firstCode.textContent = current.substring(0, charIndex - 1);
      charIndex--;
      if (charIndex === 0) {
        deleting = false;
        cmdIndex = (cmdIndex + 1) % commands.length;
      }
    }
    setTimeout(tick, deleting ? speed.delete : speed.type);
  }
  setTimeout(tick, 1200);
}

/* ── Hero entrance animation (on load) ── */
function heroEntrance() {
  const badge    = document.querySelector('.hero-badge');
  const title    = document.querySelector('.hero-title');
  const subtitle = document.querySelector('.hero-subtitle');
  const actions  = document.querySelector('.hero-actions');
  const stats    = document.querySelector('.hero-stats');
  const visual   = document.querySelector('.hero-visual');

  const els = [badge, title, subtitle, actions, stats, visual].filter(Boolean);
  els.forEach((el, i) => {
    el.style.opacity   = '0';
    el.style.transform = 'translateY(24px)';
    el.style.transition = `all 0.7s cubic-bezier(0.4,0,0.2,1) ${i * 100}ms`;
    // Trigger on next frame
    requestAnimationFrame(() => requestAnimationFrame(() => {
      el.style.opacity   = '1';
      el.style.transform = 'translateY(0)';
    }));
  });
}

/* ── Active nav link highlight on scroll ── */
function initActiveNav() {
  const sections = ['features', 'how-it-works', 'roadmap', 'download'];
  const navMap = {
    'features': document.getElementById('nav-features'),
    'how-it-works': document.getElementById('nav-how'),
    'roadmap': document.getElementById('nav-roadmap'),
    'download': document.getElementById('nav-download'),
  };

  const obs = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        sections.forEach(id => {
          const link = navMap[id];
          if (link) link.style.color = '';
        });
        const activeLink = navMap[entry.target.id];
        if (activeLink) activeLink.style.color = 'var(--purple)';
      }
    });
  }, { threshold: 0.3 });

  sections.forEach(id => {
    const el = document.getElementById(id);
    if (el) obs.observe(el);
  });
}

/* ── Ripple effect on primary buttons ── */
function initRipple() {
  document.querySelectorAll('.btn-primary').forEach(btn => {
    btn.addEventListener('click', function(e) {
      const rect   = btn.getBoundingClientRect();
      const ripple = document.createElement('span');
      const size   = Math.max(rect.width, rect.height);
      ripple.style.cssText = `
        position:absolute;
        width:${size}px;height:${size}px;
        left:${e.clientX - rect.left - size/2}px;
        top:${e.clientY - rect.top - size/2}px;
        background:rgba(255,255,255,0.2);
        border-radius:50%;
        transform:scale(0);
        animation:ripple-anim 0.6s linear;
        pointer-events:none;
      `;
      btn.style.position = 'relative';
      btn.style.overflow = 'hidden';
      btn.appendChild(ripple);
      ripple.addEventListener('animationend', () => ripple.remove());
    });
  });

  // Add ripple keyframe dynamically
  const style = document.createElement('style');
  style.textContent = `
    @keyframes ripple-anim {
      to { transform: scale(4); opacity: 0; }
    }
  `;
  document.head.appendChild(style);
}

/* ── Init everything on DOMContentLoaded ── */
document.addEventListener('DOMContentLoaded', () => {
  heroEntrance();
  initReveal();
  fetchLatestRelease();
  typewriterEffect();
  initActiveNav();
  initRipple();
});
