/**
 * Centaurix landing page — static GitHub Pages client.
 * Change these values when the repository moves.
 */
const GITHUB = Object.freeze({
  owner: "1337Detailing",
  repo: "Centaurix"
});

const PROJECT_URL = `https://github.com/${GITHUB.owner}/${GITHUB.repo}`;
const API_URL = `https://api.github.com/repos/${GITHUB.owner}/${GITHUB.repo}`;
const $ = (selector) => document.querySelector(selector);

const navigation = $("#nav-links");
const menuToggle = $(".menu-toggle");

menuToggle?.addEventListener("click", () => {
  const expanded = menuToggle.getAttribute("aria-expanded") === "true";
  menuToggle.setAttribute("aria-expanded", String(!expanded));
  menuToggle.setAttribute("aria-label", expanded ? "Open menu" : "Close menu");
  navigation.classList.toggle("is-open", !expanded);
});
navigation?.querySelectorAll("a").forEach((link) => {
  link.addEventListener("click", () => {
    navigation.classList.remove("is-open");
    menuToggle?.setAttribute("aria-expanded", "false");
  });
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    navigation?.classList.remove("is-open");
    menuToggle?.setAttribute("aria-expanded", "false");
  }
});
$("#year").textContent = String(new Date().getFullYear());

const revealItems = document.querySelectorAll(".reveal");
if ("IntersectionObserver" in window) {
  const observer = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (entry.isIntersecting) {
        entry.target.classList.add("is-visible");
        observer.unobserve(entry.target);
      }
    }
  }, { threshold: 0.10, rootMargin: "0px 0px -25px 0px" });
  revealItems.forEach((item) => observer.observe(item));
} else {
  revealItems.forEach((item) => item.classList.add("is-visible"));
}

const screenshotStrip = $("#screenshots-strip");
$("#prev-shot")?.addEventListener("click", () => {
  screenshotStrip?.scrollBy({ left: -Math.max(225, screenshotStrip.clientWidth * .55), behavior: "smooth" });
});
$("#next-shot")?.addEventListener("click", () => {
  screenshotStrip?.scrollBy({ left: Math.max(225, screenshotStrip.clientWidth * .55), behavior: "smooth" });
});

// Lightweight pointer parallax; disabled for motion-sensitive users.
const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
const finePointer = window.matchMedia("(pointer: fine)");
const visual = $(".hero-visual");
const heroImage = $(".device-art");
if (visual && heroImage && finePointer.matches && !reduceMotion.matches) {
  visual.addEventListener("pointermove", (event) => {
    const box = visual.getBoundingClientRect();
    const x = (event.clientX - box.left) / box.width - .5;
    const y = (event.clientY - box.top) / box.height - .5;
    heroImage.style.translate = `${(x * 10).toFixed(1)}px ${(y * 9).toFixed(1)}px`;
  });
  visual.addEventListener("pointerleave", () => {
    heroImage.style.translate = "0px 0px";
  });
}

/** No API key or backend required; public GitHub endpoints only. */
async function githubJson(path, timeoutMs = 9000) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(`${API_URL}${path}`, {
      headers: { Accept: "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28" },
      signal: controller.signal
    });
    if (!response.ok) {
      const error = new Error(response.status === 403 ? "GitHub API rate limit reached" : `GitHub API HTTP ${response.status}`);
      error.status = response.status;
      throw error;
    }
    return await response.json();
  } finally {
    clearTimeout(timeout);
  }
}

function formatSize(size) {
  if (!Number.isFinite(size) || size < 0) return "Unknown size";
  return `${(size / (1024 * 1024)).toLocaleString("en-US", { maximumFractionDigits: 1 })} MB`;
}

function formatDate(value) {
  const date = value ? new Date(value) : null;
  return date && !Number.isNaN(date.getTime())
    ? new Intl.DateTimeFormat("en-US", { day: "numeric", month: "long", year: "numeric" }).format(date)
    : "Unknown date";
}

function latestRelease(releases) {
  if (!Array.isArray(releases)) return null;
  return releases
    .filter((release) => release && !release.draft)
    .flatMap((release) => (release.assets || [])
      .filter((asset) => /\.apk$/i.test(asset.name || "") && asset.browser_download_url)
      .map((asset) => ({ release, asset })))
    .sort((left, right) =>
      new Date(right.release.published_at || right.release.created_at || 0) -
      new Date(left.release.published_at || left.release.created_at || 0))[0] || null;
}

function latestArtifact(payload) {
  if (!Array.isArray(payload?.artifacts)) return null;
  return payload.artifacts
    .filter((artifact) => artifact && !artifact.expired && /^Centaurix-SIGNED-APK-/i.test(artifact.name || "") && artifact.workflow_run?.id)
    .sort((left, right) => new Date(right.created_at || 0) - new Date(left.created_at || 0))[0] || null;
}

function putText(id, value) {
  const element = document.getElementById(id);
  if (element) element.textContent = value;
}

function setLink(node, url) {
  if (!node) return;
  const safe = typeof url === "string" && url.startsWith("https://github.com/") ? url : PROJECT_URL;
  node.setAttribute("href", safe);
}

function renderRelease(found) {
  const { release, asset } = found;
  putText("release-name", release.name || release.tag_name || asset.name);
  putText("release-meta", `APK · ${formatSize(asset.size)} · ${formatDate(release.published_at || release.created_at)}`);
  const temporary = release.tag_name === "ci-preview";
  putText("release-status", temporary
    ? "Temporary test-signed APK. Install it once, then later builds signed with the same key can update it. Builds signed with an older key require a one-time reinstall."
    : "APK ready for direct download from GitHub Releases.");
  putText("release-badge", temporary ? "TEST BETA" : (release.prerelease ? "BETA" : "RELEASE"));
  putText("release-cta", temporary ? "Download test APK" : "Download APK");
  setLink($("#release-download"), asset.browser_download_url);
  setLink($("#release-fallback"), `${PROJECT_URL}/releases`);
  $("#release-fallback").firstChild.textContent =  "All Releases ";
}

function renderArtifact(artifact) {
  const workflowPage = `${PROJECT_URL}/actions/runs/${artifact.workflow_run.id}`;
  putText("release-name", artifact.name);
  putText("release-meta", `ZIP archive · ${formatSize(artifact.size_in_bytes)} · ${formatDate(artifact.created_at)}`);
  putText("release-status", "No APK Release yet. This build is available through GitHub Actions: open the run and download its ZIP artifact (GitHub sign-in may be required).");
  putText("release-badge", "ACTIONS");
  putText("release-cta", "View build");
  setLink($("#release-download"), workflowPage);
  setLink($("#release-fallback"), `${PROJECT_URL}/actions`);
}

function renderFallback(hadApiError) {
  putText("release-name", "Centaurix for Android");
  putText("release-meta", "GitHub builds and releases");
  putText("release-status", hadApiError
    ? "Unable to reach GitHub right now (network issue or API rate limit). You can still find builds in the repository."
    : "No release-signed APK is available yet. Check GitHub Actions after release signing has been configured.");
  putText("release-badge", "GITHUB");
  putText("release-cta", "Browse versions");
  setLink($("#release-download"), `${PROJECT_URL}/actions`);
  setLink($("#release-fallback"), `${PROJECT_URL}/releases`);
}

async function loadDownloads() {
  const [releases, artifacts] = await Promise.allSettled([
    githubJson("/releases?per_page=30"),
    githubJson("/actions/artifacts?per_page=50")
  ]);
  const published = releases.status === "fulfilled" ? latestRelease(releases.value) : null;
  if (published) {
    renderRelease(published);
    return;
  }
  const artifact = artifacts.status === "fulfilled" ? latestArtifact(artifacts.value) : null;
  if (artifact) {
    renderArtifact(artifact);
    return;
  }
  renderFallback(releases.status === "rejected" || artifacts.status === "rejected");
}

loadDownloads().catch(() => renderFallback(true));
