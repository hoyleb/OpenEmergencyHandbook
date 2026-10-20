#!/usr/bin/env python3
"""Validate canonical content and generate Android assets + a portable HTML edition."""
import argparse
import copy
from datetime import date
import hashlib
import html
import json
from pathlib import Path
import re
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = {'power', 'cyber', 'earthquake', 'fallout', 'flood', 'pandemic', 'first-aid', 'water'}


def content_digest(article):
    return hashlib.sha256(json.dumps({k: article[k] for k in ('en', 'de', 'sources')}, sort_keys=True, ensure_ascii=False).encode()).hexdigest()


def validate(data):
    if data.get('schema_version') != 1:
        raise ValueError('Unsupported content schema')
    date.fromisoformat(data['edition'])
    seen = set()
    for article in data['articles']:
        aid = article['id']
        if not re.fullmatch(r'[a-z0-9-]+', aid) or aid in seen:
            raise ValueError(f'Invalid/duplicate article ID: {aid}')
        seen.add(aid)
        if article.get('status') not in {'draft', 'reviewed'}:
            raise ValueError(f'Missing review status: {aid}')
        if article['group'] not in {'urgent', 'disaster', 'essentials', 'about'}:
            raise ValueError(f'Unknown article group: {aid}')
        if article['status'] == 'reviewed':
            for field in ('professional_review', 'translation_review'):
                record = article.get(field)
                if not isinstance(record, dict) or not all(record.get(k) for k in ('reviewer', 'role', 'date', 'scope', 'evidence', 'content_sha256')):
                    raise ValueError(f'Unsubstantiated review: {aid}/{field}')
                date.fromisoformat(record['date'])
                if record['content_sha256'] != content_digest(article):
                    raise ValueError(f'Review does not match content: {aid}')
        for language in ('en', 'de'):
            for field in ('title', 'summary', 'keywords', 'body'):
                if not article.get(language, {}).get(field, '').strip():
                    raise ValueError(f'Missing {language}/{field}: {aid}')
            if '<script' in article[language]['body'].lower():
                raise ValueError(f'Executable content: {aid}')
        if not article.get('reuse') or not article.get('checked_at') or not article.get('sources') or not article.get('region'):
            raise ValueError(f'Missing provenance: {aid}')
        checked = date.fromisoformat(article['checked_at'])
        if date.fromisoformat(article['review_due']) < checked:
            raise ValueError(f'Invalid review date range: {aid}')
        if not article.get('revision_history'):
            raise ValueError(f'Missing revision history: {aid}')
        for url in article['sources']:
            parsed = urlparse(url)
            if parsed.scheme != 'https' or not parsed.netloc or parsed.username:
                raise ValueError(f'Invalid source URL: {aid}')
    missing = REQUIRED - seen
    if missing:
        raise ValueError(f'Missing required modules: {sorted(missing)}')
    for article in data['articles']:
        links = article.get('related', [])
        if len(set(links)) != len(links) or any(link not in seen or link == article['id'] for link in links):
            raise ValueError(f'Invalid related guide: {article["id"]}')


def coverage_report(data, matrix):
    if matrix.get('schema_version') != 1 or not matrix.get('requirements'):
        raise ValueError('Invalid coverage matrix')
    by_id = {a['id']: a for a in data['articles']}
    requirements = []
    names = set()
    for row in matrix['requirements']:
        if row['requirement'] in names:
            raise ValueError('Duplicate coverage requirement')
        names.add(row['requirement'])
        if any(aid not in by_id for aid in row['article_ids']):
            raise ValueError(f'Coverage references missing article: {row["requirement"]}')
        state = 'missing' if not row['article_ids'] else ('partial' if row['remaining'] else 'draft')
        if state == 'draft' and all(by_id[aid]['status'] == 'reviewed' for aid in row['article_ids']):
            state = 'reviewed'
        requirements.append({**row, 'status': state})
    blockers = [r['requirement'] for r in requirements if r['status'] != 'reviewed']
    return {'requirements': requirements, 'public_release_ready': not blockers, 'public_release_blockers': blockers}


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
    by_id = {a['id']: a for a in data['articles']}
    for article in data['articles']:
        for language in ('en', 'de'):
            a = article[language]
            sources = ''.join(f'<li><a href="{html.escape(url, quote=True)}">{html.escape(urlparse(url).netloc)}</a></li>' for url in article['sources'])
            label = 'Unreviewed development preview' if language == 'en' else 'Ungeprüfter Entwicklungsentwurf'
            related = ''.join(f'<li><a href="#{language}-{aid}">{html.escape(by_id[aid][language]["title"])}</a></li>' for aid in article.get('related', []))
            blocks.append(f'<article lang="{language}" data-keywords="{html.escape(a["keywords"], quote=True)}" id="{language}-{article["id"]}"><h2>{html.escape(a["title"])}</h2><p class="status">{label}</p><p>{html.escape(a["summary"])}</p>{markdown(a["body"])}<nav aria-label="Related guides"><h3>Related guides / Weitere Anleitungen</h3><ul>{related}</ul></nav><details><summary>Sources / Quellen</summary><ul>{sources}</ul><p>Source check / Quellenabgleich: {article["checked_at"]}. Next review / Nächste Prüfung: {article["review_due"]}. External links require internet. No professional review recorded.</p></details><p><a href="#top">Top / Anfang</a></p></article>')
    urgent_labels = {
        'en': [('first-aid', 'First aid'), ('water', 'Safe water'), ('power', 'Power outage'), ('flood', 'Flood'), ('earthquake', 'Earthquake'), ('fallout', 'Nuclear fallout'), ('cyber', 'Cyberattack'), ('pandemic', 'Pandemic')],
        'de': [('first-aid', 'Erste Hilfe'), ('water', 'Sicheres Wasser'), ('power', 'Stromausfall'), ('flood', 'Hochwasser'), ('earthquake', 'Erdbeben'), ('fallout', 'Nuklearer Fallout'), ('cyber', 'Cyberangriff'), ('pandemic', 'Pandemie')]
    }
    urgent = '<nav aria-label="Urgent offline guides / Soforthilfe">' + ''.join(
        f'<div class="urgent-group" data-urgent-lang="{language}" lang="{language}">' + ''.join(
            f'<a href="#{language}-{aid}">{html.escape(label)}</a>' for aid, label in urgent_labels[language]) + '</div>'
        for language in ('en', 'de')) + '</nav>'
    searchable = [{key: article[key] for key in ('id', 'group', 'en', 'de')} for article in data['articles']]
    search_data = json.dumps(searchable, ensure_ascii=False).replace('<', '\\u003c').replace('>', '\\u003e').replace('&', '\\u0026')
    scripts = (ROOT / 'tools/offline_search.js').read_text() + '\n' + (ROOT / 'tools/offline_controls.js').read_text()
    return '''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark"><title>Open Emergency Handbook — Preview</title>
<style>body{overflow-wrap:anywhere;max-width:850px;margin:auto;padding:24px;font:18px/1.6 system-ui;background:#eff5f2;color:#18372f}h1{font-size:1.1em;line-height:1.15;margin:.4em 0}.edition-status{font-size:.85em;line-height:1.35;margin:.4em 0}.urgent-group{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin:12px 0}.urgent-group a{display:flex;align-items:center;justify-content:center;box-sizing:border-box;min-height:52px;padding:10px;text-align:center;border:1px solid #55756b;border-radius:8px;background:white;font-weight:600;text-decoration:none}h2,h3{line-height:1.3}article{background:white;padding:22px;border-radius:18px;margin:20px 0;break-inside:avoid}.status{font-size:.8em;color:#885000}button,input,select{box-sizing:border-box;max-width:100%;min-height:48px;font:inherit;padding:12px;border:1px solid #55756b;border-radius:8px;margin:4px}input{max-width:90%}a{color:#086850}header{border-bottom:2px solid #9dbcb0;padding-bottom:20px}.banner{background:#fff0c8;padding:14px;border-radius:8px}@media(prefers-color-scheme:dark){body{background:#10221e;color:#eaf7f1}article,.urgent-group a{background:#1c3730}a{color:#9de5cd}.banner{background:#43391b}.status{color:#ffdf92}}[hidden]{display:none!important}select{max-width:100%}@media print{button,input,select,.interactive,.banner{display:none!important}body{background:white;color:black;font-size:11pt;padding:0}article{padding:0;break-inside:auto}details{display:block}a{color:black}}</style>
<header id="top"><h1>Open Emergency Handbook</h1><p class="edition-status" data-ui-lang="en">Offline · Unreviewed drafts</p><p class="edition-status" data-ui-lang="de">Offline · Ungeprüfte Entwürfe</p>
''' + urgent + '''
<div class="interactive" hidden><button id="en" type="button" aria-pressed="true">English</button><button id="de" type="button" aria-pressed="false">Deutsch</button><button id="print">Print / Drucken</button><br><label for="search">Search / Suche: </label><input id="search" type="search" maxlength="200" placeholder="water / Wasser"><button id="clear" type="button">Clear / Löschen</button><label for="category">Filter: </label><select id="category"><option value="all">All guides</option><option value="aid">First aid</option><option value="disaster">Disasters</option><option value="prepare">Preparation</option></select><p id="count" role="status" aria-live="polite"></p><p id="empty" hidden>No matches / Keine Treffer. <button id="reset">Reset search / Suche zurücksetzen</button></p></div><noscript><p>JavaScript is disabled: all English and German guides are shown. Use the contents or your browser’s Find command. / Ohne JavaScript: alle Anleitungen in beiden Sprachen; Inhaltsverzeichnis oder Browsersuche verwenden.</p></noscript><details id="limitations"><summary>Limitations / Grenzen</summary><p class="banner">Development preview. Incomplete coverage; no professional review of summaries or translations. Follow emergency services. / Entwicklungsentwurf: unvollständig und ungeprüft; Rettungsdiensten folgen.</p></details><p>All guide text is included in this file. Source links need internet. This file has no live alerts. Keep a printed copy.</p></header>
''' + '<nav id="contents" aria-label="Contents">' + ''.join(f'<p lang="{lang}" class="contents" id="contents-{lang}-{a["id"]}"><a href="#{lang}-{a["id"]}">{html.escape(a[lang]["title"])}</a></p>' for lang in ('en','de') for a in data['articles']) + '</nav><main id="guides">' + '\n'.join(blocks) + '</main>' + '''<footer>GPLv3 · Fork of ligi/SurvivalManual · <a href="https://github.com/hoyleb/OpenEmergencyHandbook">Source code and feedback</a></footer>
<script id="guide-data" type="application/json">''' + search_data + '''</script><script>''' + scripts + '''</script></html>'''


def recovery_files(data):
    """Independent per-guide copies; index hashes detect accidental corruption."""
    files = {}
    index = {'schema_version': 1, 'edition': data['edition'], 'primary_sha256': hashlib.sha256((ROOT / 'content/handbook.json').read_bytes()).hexdigest(), 'articles': []}
    for article in data['articles']:
        payload = json.dumps(article, ensure_ascii=False, indent=2).encode() + b'\n'
        files[article['id'] + '.json'] = payload
        index['articles'].append({'id': article['id'], 'sha256': hashlib.sha256(payload).hexdigest()})
    files['index.json'] = json.dumps(index, indent=2).encode() + b'\n'
    return files


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true', help='Fail if generated Android assets are stale')
    parser.add_argument('--public-release', action='store_true', help='Also require every coverage requirement to have matching qualified and translation review records')
    args = parser.parse_args()
    source = ROOT / 'content/handbook.json'
    data = json.loads(source.read_text())
    validate(data)
    matrix = json.loads((ROOT / 'content/coverage.json').read_text())
    coverage = coverage_report(data, matrix)
    asset = ROOT / 'android/src/main/offlineAssets/handbook/articles.json'
    payload = source.read_bytes()
    if args.check:
        if not asset.is_file() or asset.read_bytes() != payload:
            raise SystemExit('Generated Android content is stale. Run tools/build_handbook.py')
    else:
        asset.parent.mkdir(parents=True, exist_ok=True)
        asset.write_bytes(payload)
    recovery = asset.parent / 'recovery'
    expected = recovery_files(data)
    if args.check:
        if not recovery.is_dir() or {p.name for p in recovery.glob('*.json')} != set(expected) or any((recovery / name).read_bytes() != payload for name, payload in expected.items()):
            raise SystemExit('Generated recovery guides are stale. Run tools/build_handbook.py')
    else:
        recovery.mkdir(parents=True, exist_ok=True)
        for old in recovery.glob('*.json'):
            if old.name not in expected: old.unlink()
        for name, payload in expected.items(): (recovery / name).write_bytes(payload)
    cases = ROOT / 'tools/search-cases.json'
    generated_cases = asset.parent / 'search-cases.json'
    if args.check:
        if not generated_cases.is_file() or generated_cases.read_bytes() != cases.read_bytes():
            raise SystemExit('Generated search vectors are stale. Run tools/build_handbook.py')
    else: generated_cases.write_bytes(cases.read_bytes())
    out = ROOT / 'dist'
    out.mkdir(exist_ok=True)
    (out / 'OpenEmergencyHandbook-offline.html').write_text(make_html(data))
    report = {
        'edition': data['edition'], 'articles_per_language': len(data['articles']),
        'languages': ['en', 'de'], 'required_modules_present': sorted(REQUIRED),
        'professionally_reviewed_articles': sum(a['status'] == 'reviewed' for a in data['articles']),
        'complete_survival_library': False,
        'content_sha256': hashlib.sha256(payload).hexdigest(),
        **coverage,
        'article_reviews': [{'id': a['id'], 'status': a['status'], 'content_sha256': content_digest(a), 'professional_review': a['professional_review'], 'translation_review': a['translation_review']} for a in data['articles']],
    }
    (out / 'coverage-report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))
    if args.public_release and not report['public_release_ready']:
        raise SystemExit('Public release blocked: missing/partial/unreviewed requirements. See dist/coverage-report.json')


if __name__ == '__main__':
    main()
