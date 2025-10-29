function copyToClipboard(id) {
    const el = document.getElementById(id);
    if (!el) return;
    el.select(); el.setSelectionRange(0, 99999);
    document.execCommand('copy');
}
