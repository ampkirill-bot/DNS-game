#!/usr/bin/env python3
"""Собирает docs/index.html (GitHub Pages, офлайн-версия для iPhone) из web/obhod.html.
Запуск: python3 web/build_docs.py"""
from pathlib import Path

root = Path(__file__).resolve().parent.parent
body = (root / "web" / "obhod.html").read_text(encoding="utf-8")

head = """<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="theme-color" content="#1f4e79">
<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
<link rel="manifest" href="manifest.webmanifest">
<link rel="apple-touch-icon" href="apple-touch-icon.png">
<link rel="icon" href="icon-192.png">
<style>:root{padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}body{margin:0}img{max-width:100%}[hidden]{display:none!important}</style>
</head>
<body>
"""
tail = """
<script>
if ("serviceWorker" in navigator) {
  window.addEventListener("load", () => navigator.serviceWorker.register("sw.js").catch(() => {}));
}
</script>
</body>
</html>
"""
(root / "docs" / "index.html").write_text(head + body + tail, encoding="utf-8")
print("docs/index.html written")
