#!/usr/bin/env python3
"""Validate canonical content and generate Android assets + a portable HTML edition."""
import argparse
import copy
import hashlib
import html
import json
from pathlib import Path
import re
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = {'power', 'cyber', 'earthquake', 'fallout', 'flood', 'pandemic', 'first-aid', 'water'}


def validate(data):
    if data.get('schema_version') != 1:
        raise ValueError('Unsupported content schema')
    seen = set()
    for article in data['articles']:
        aid = article['id']
        if not re.fullmatch(r'[a-z0-9-]+', aid) or aid in seen:
            raise ValueError(f'Invalid/duplicate article ID: {aid}')
        seen.add(aid)
        if article.get('status') not in {'draft', 'reviewed'}:
            raise ValueError(f'Missing review status: {aid}')
        if article['status'] == 'reviewed' and not article.get('professional_review'):
            raise ValueError(f'Unsubstantiated review: {aid}')
        for language in ('en', 'de'):
            for field in ('title', 'summary', 'keywords', 'body'):
                if not article.get(language, {}).get(field, '').strip():
                    raise ValueError(f'Missing {language}/{field}: {aid}')
            if '<script' in article[language]['body'].lower():
                raise ValueError(f'Executable content: {aid}')
        if not article.get('reuse') or not article.get('checked_at') or not article.get('sources'):
            raise ValueError(f'Missing provenance: {aid}')
        for url in article['sources']:
            parsed = urlparse(url)
            if parsed.scheme != 'https' or not parsed.netloc or parsed.username:
                raise ValueError(f'Invalid source URL: {aid}')
    missing = REQUIRED - seen
    if missing:
        raise ValueError(f'Missing required modules: {sorted(missing)}')


def markdown(text):
    """Small safe renderer for authored headings, paragraphs and bold text."""
    blocks = []
    for paragraph in text.split('\n\n'):
        escaped = html.escape(paragraph)
        escaped = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', escaped)
        if escaped.startswith('## '):
            blocks.append('<h3>' + escaped[3:] + '</h3>')
        else:
            blocks.append('<p>' + escaped.replace('\n', '<br>') + '</p>')
    return '\n'.join(blocks)


def make_html(data):
    blocks = []
    for article in data['articles']:
        for language in ('en', 'de'):
            a = article[language]
            sources = ''.join(f'<li><a href="{html.escape(url, quote=True)}">{html.escape(urlparse(url).netloc)}</a></li>' for url in article['sources'])
            label = 'Unreviewed development preview' if language == 'en' else 'Ungeprüfter Entwicklungsentwurf'
            blocks.append(f'<article lang="{language}" id="{language}-{article["id"]}"><h2>{html.escape(a["title"])}</h2><p class="status">{label}</p><p>{html.escape(a["summary"])}</p>{markdown(a["body"])}<details><summary>Sources / Quellen</summary><ul>{sources}</ul><p>Checked / Geprüft: {data["edition"]}. External links require internet. No professional review recorded.</p></details></article>')
    return '''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark"><title>Open Emergency Handbook — Preview</title>
<style>body{max-width:850px;margin:auto;padding:24px;font:18px/1.6 system-ui;background:#eff5f2;color:#18372f}h1{line-height:1.15}h2,h3{line-height:1.3}article{background:white;padding:22px;border-radius:18px;margin:20px 0;break-inside:avoid}.status{font-size:.8em;color:#885000}button,input{font:inherit;padding:12px;border:1px solid #55756b;border-radius:8px;margin:4px}input{max-width:90%}a{color:#086850}header{border-bottom:2px solid #9dbcb0;padding-bottom:20px}.banner{background:#fff0c8;padding:14px;border-radius:8px}@media(prefers-color-scheme:dark){body{background:#10221e;color:#eaf7f1}article{background:#1c3730}a{color:#9de5cd}.banner{background:#43391b}.status{color:#ffdf92}}@media print{button,input,.banner{display:none}body{background:white;color:black;font-size:11pt;padding:0}article{padding:0;break-inside:auto}details{display:block}a{color:black}}</style>
<header><p>FREE · OPEN SOURCE · OFFLINE</p><h1>Open Emergency Handbook</h1><p class="banner">Development preview. Incomplete coverage; no professional review of summaries or translations. Test the interface; follow emergency services. / Entwicklungsentwurf: unvollständig und ungeprüft.</p>
<button id="en">English</button><button id="de">Deutsch</button><button id="print">Print / Drucken</button><br><label for="search">Search / Suche: </label><input id="search" type="search" placeholder="water / Wasser"><p id="count" aria-live="polite"></p><p>All guide text is included in this file. Source links need internet. This file has no live alerts. Keep a printed copy.</p></header>
''' + '\n'.join(blocks) + '''<footer>GPLv3 · Fork of ligi/SurvivalManual · <a href="https://github.com/hoyleb/OpenEmergencyHandbook">Source code and feedback</a></footer>
<script>let lang='en';const q=document.querySelector('#search');const norm=s=>s.toLowerCase().replaceAll('ß','ss').normalize('NFD').replace(/[\\u0300-\\u036f]/g,'');function show(){let n=0;const words=norm(q.value).trim().split(/\\s+/).filter(Boolean);document.querySelectorAll('article').forEach(a=>{a.hidden=a.lang!==lang||!words.every(w=>norm(a.textContent).includes(w));if(!a.hidden)n++});document.documentElement.lang=lang;document.querySelector('#count').textContent=n+(lang==='en'?' guides':' Anleitungen')};q.addEventListener('input',show);document.querySelector('#en').onclick=()=>{lang='en';show()};document.querySelector('#de').onclick=()=>{lang='de';show()};document.querySelector('#print').onclick=()=>window.print();show();</script></html>'''


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true', help='Fail if generated Android assets are stale')
    args = parser.parse_args()
    source = ROOT / 'content/handbook.json'
    data = json.loads(source.read_text())
    validate(data)
    asset = ROOT / 'android/src/main/offlineAssets/handbook/articles.json'
    payload = source.read_bytes()
    if args.check:
        if not asset.is_file() or asset.read_bytes() != payload:
            raise SystemExit('Generated Android content is stale. Run tools/build_handbook.py')
    else:
        asset.parent.mkdir(parents=True, exist_ok=True)
        asset.write_bytes(payload)
    out = ROOT / 'dist'
    out.mkdir(exist_ok=True)
    (out / 'OpenEmergencyHandbook-offline.html').write_text(make_html(data))
    report = {
        'edition': data['edition'], 'articles_per_language': len(data['articles']),
        'languages': ['en', 'de'], 'required_modules_present': sorted(REQUIRED),
        'professionally_reviewed_articles': sum(a['status'] == 'reviewed' for a in data['articles']),
        'complete_survival_library': False,
        'content_sha256': hashlib.sha256(payload).hexdigest(),
    }
    (out / 'coverage-report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
