const $ = (id) => document.getElementById(id);

async function readJSON(res) {
  const text = await res.text();
  let body = {};
  try { body = text ? JSON.parse(text) : {}; } catch { body = { error: text }; }
  if (!res.ok) throw new Error(body.error || res.statusText);
  return body;
}

$("search-form").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  const data = new FormData(ev.target);
  const params = new URLSearchParams();
  params.set("q", data.get("q"));
  params.set("mode", data.get("mode"));
  if (data.get("language")) params.set("language", data.get("language"));
  if (data.get("mode") === "hybrid") params.set("alpha", data.get("alpha"));
  $("search-error").hidden = true;
  $("search-loading").hidden = false;
  $("results").innerHTML = "";
  try {
    const body = await readJSON(await fetch("/api/v1/search?" + params.toString()));
    $("search-meta").hidden = false;
    $("search-meta").textContent = `${body.total_results} results in ${body.query_time_ms.toFixed(2)} ms · mode ${body.mode}${body.cached ? " · cached" : ""}`;
    $("results").innerHTML = (body.results || []).map((hit) => `
      <li class="hit">
        <header>${hit.repository} · ${hit.file_path}:${hit.start_line}-${hit.end_line}</header>
        <div class="muted">${hit.language}${hit.symbol_name ? " · " + hit.symbol_name : ""} · score ${hit.score.toFixed(4)}</div>
        <pre><code>${hit.snippet}</code></pre>
      </li>`).join("");
  } catch (err) {
    $("search-error").hidden = false;
    $("search-error").textContent = err.message;
  } finally {
    $("search-loading").hidden = true;
  }
});

$("repo-form").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  $("repo-error").hidden = true;
  try {
    const url = new FormData(ev.target).get("url");
    const body = await readJSON(await fetch("/api/v1/repositories", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ url }),
    }));
    pollJob(body.job_id, body.repository_id);
    loadRepos();
  } catch (err) {
    $("repo-error").hidden = false;
    $("repo-error").textContent = err.message;
  }
});

async function pollJob(jobId, repoId) {
  $("job-progress").hidden = false;
  for (let i = 0; i < 60; i++) {
    const job = await readJSON(await fetch("/api/v1/jobs/" + jobId));
    $("job-progress").textContent = `Job ${job.status} · added ${job.files_added} modified ${job.files_modified} deleted ${job.files_deleted}`;
    if (job.status === "succeeded" || job.status === "failed") {
      loadRepos();
      return;
    }
    await new Promise((r) => setTimeout(r, 1000));
  }
}

async function loadRepos() {
  const repos = await readJSON(await fetch("/api/v1/repositories"));
  $("repo-list").innerHTML = (repos || []).map((repo) => `
    <li>${repo.owner}/${repo.name} · ${repo.status} · ${repo.document_count} files
      <button data-reindex="${repo.id}">Reindex</button>
    </li>`).join("");
}

$("repo-list").addEventListener("click", async (ev) => {
  const id = ev.target.getAttribute("data-reindex");
  if (!id) return;
  const body = await readJSON(await fetch("/api/v1/repositories/" + id + "/reindex", { method: "POST" }));
  pollJob(body.job_id, id);
});

async function loadStatus() {
  try {
    const [live, ready, status] = await Promise.all([
      readJSON(await fetch("/health/live")),
      fetch("/health/ready").then((r) => r.json()),
      readJSON(await fetch("/api/v1/status")),
    ]);
    $("status-body").textContent = JSON.stringify({ live, ready, status }, null, 2);
  } catch (err) {
    $("status-body").textContent = err.message;
  }
}

loadRepos().catch(() => {});
loadStatus();
setInterval(loadStatus, 5000);
