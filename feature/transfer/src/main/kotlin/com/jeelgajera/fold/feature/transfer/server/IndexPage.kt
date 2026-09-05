package com.jeelgajera.fold.feature.transfer.server

/**
 * The page a browser on the LAN gets.
 *
 * Self-contained on purpose: no CDN, no web font, no analytics tag, nothing that
 * reaches outside the local network. The app's claim is that it makes no network
 * calls, and a page that pulls a stylesheet from a CDN would quietly break that
 * claim on the user's behalf every time somebody opened it.
 *
 * It is also deliberately plain. This is a transfer surface, not a second copy
 * of the app: list, download, upload, and a PIN box.
 */
object IndexPage {

    fun html(requirePin: Boolean): String = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>FOLD</title>
<style>
  :root { color-scheme: dark; }
  body {
    margin: 0; background: #0c0b0b; color: #f3f2f2;
    font: 14px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif;
  }
  header {
    display: flex; align-items: baseline; gap: 8px;
    padding: 0 16px; height: 56px;
    border-bottom: 1px solid rgba(243,242,242,.16);
  }
  .wordmark { font-weight: 800; font-size: 19px; letter-spacing: -.02em; }
  .meta { font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
          font-size: 11px; letter-spacing: .12em; color: rgba(243,242,242,.5); }
  main { padding: 16px; max-width: 900px; }
  .row {
    display: flex; align-items: center; gap: 12px;
    min-height: 64px; padding: 10px 0;
    border-bottom: 1px solid rgba(243,242,242,.12);
  }
  .badge {
    width: 38px; height: 38px; flex: none;
    border: 1px solid rgba(243,242,242,.35);
    display: flex; align-items: center; justify-content: center;
    font-family: ui-monospace, monospace; font-size: 10px;
  }
  .name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .size { font-family: ui-monospace, monospace; font-size: 11px; color: rgba(243,242,242,.62); }
  a { color: #ff9783; text-decoration: none; }
  a:hover { text-decoration: underline; }
  input, button {
    font: inherit; background: rgba(243,242,242,.06); color: #f3f2f2;
    border: 1px solid rgba(243,242,242,.16); border-radius: 0;
    min-height: 48px; padding: 0 14px;
  }
  button { background: #ec3013; color: #0c0b0b; font-weight: 800;
           letter-spacing: .1em; border-color: #ec3013; cursor: pointer; }
  .error { color: #ff9783; font-size: 13px; min-height: 20px; }
  .crumbs { font-family: ui-monospace, monospace; font-size: 12px;
            color: rgba(243,242,242,.6); margin-bottom: 12px; }
</style>
</head>
<body>
<header>
  <span class="wordmark">FOLD</span>
  <span class="meta" id="status">CONNECTING</span>
</header>
<main>
  <section id="gate" ${if (requirePin) "" else "hidden"}>
    <p>Enter the ${ServerAuth.PIN_DIGITS}-digit PIN shown on the phone.</p>
    <form id="pin-form">
      <input id="pin" inputmode="numeric" autocomplete="off"
             maxlength="${ServerAuth.PIN_DIGITS}" placeholder="000000" aria-label="PIN">
      <button type="submit">UNLOCK</button>
    </form>
    <p class="error" id="gate-error" role="alert"></p>
  </section>

  <section id="browser" ${if (requirePin) "hidden" else ""}>
    <div class="crumbs" id="crumbs">/</div>
    <div id="listing"></div>
  </section>
</main>

<script>
  // No framework, no bundle, no network beyond this phone.
  let token = ${if (requirePin) "null" else "'open'"};
  let path = '';

  const el = (id) => document.getElementById(id);

  async function authenticate(pin) {
    const response = await fetch('/api/auth', { method: 'POST', headers: { 'X-Fold-Pin': pin } });
    const body = await response.json();
    if (!response.ok || !body.token) {
      el('gate-error').textContent = body.message || 'Wrong PIN.';
      return false;
    }
    token = body.token;
    el('gate').hidden = true;
    el('browser').hidden = false;
    return true;
  }

  async function list(next) {
    const response = await fetch('/api/list?path=' + encodeURIComponent(next), {
      headers: { 'X-Fold-Token': token },
    });
    if (!response.ok) { el('status').textContent = 'REFUSED'; return; }
    const body = await response.json();
    path = body.path;
    el('status').textContent = body.entries.length + ' ITEMS';
    el('crumbs').textContent = '/' + body.path;
    render(body);
  }

  function render(body) {
    const listing = el('listing');
    listing.textContent = '';
    if (body.parent !== null) listing.append(row('..', body.parent, true, null));
    for (const entry of body.entries) {
      listing.append(row(entry.name, entry.path, entry.isDirectory, entry.size, entry.name));
    }
  }

  function row(label, target, isDirectory, size, badgeSource) {
    const div = document.createElement('div');
    div.className = 'row';

    const badge = document.createElement('div');
    badge.className = 'badge';
    badge.textContent = isDirectory ? 'DIR' : extensionOf(badgeSource || label);
    div.append(badge);

    const link = document.createElement('a');
    link.className = 'name';
    // textContent, never innerHTML: a file named "<img onerror=...>" is a file
    // name, not markup, and this page is served from the user's own device.
    link.textContent = label;
    link.href = '#';
    link.onclick = (event) => {
      event.preventDefault();
      if (isDirectory) { list(target); }
      else { download(target, label); }
      return false;
    };
    div.append(link);

    if (size !== null && size !== undefined) {
      const bytes = document.createElement('span');
      bytes.className = 'size';
      bytes.textContent = human(size);
      div.append(bytes);
    }
    return div;
  }

  async function download(target, name) {
    const response = await fetch('/api/download?path=' + encodeURIComponent(target), {
      headers: { 'X-Fold-Token': token },
    });
    if (!response.ok) return;
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = name;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  function extensionOf(name) {
    const dot = name.lastIndexOf('.');
    return dot > 0 ? name.slice(dot + 1).toUpperCase().slice(0, 4) : '?';
  }

  function human(bytes) {
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let size = bytes, unit = 0;
    while (size >= 1000 && unit < units.length - 1) { size /= 1000; unit++; }
    return (size >= 100 || unit === 0 ? size.toFixed(0) : size.toFixed(1)) + ' ' + units[unit];
  }

  const form = el('pin-form');
  if (form) {
    form.onsubmit = async (event) => {
      event.preventDefault();
      if (await authenticate(el('pin').value)) list('');
      return false;
    };
  }

  if (token) list('');
</script>
</body>
</html>
    """.trimIndent()
}
