#!/usr/bin/env python3
"""Generate paper cards and a reviewer handoff without altering canonical guidance.

All guide bodies are copied in full; a layout overflow is an error, never a reason
to silently truncate text or shrink the minimum body type. No network is used.
"""
import argparse
import csv
from datetime import date
import hashlib
import html
import io
import json
from pathlib import Path
import re
import sys
from urllib.parse import urlparse
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from build_handbook import ROOT, content_digest, coverage_report, validate

CARD_IDS = ('first-aid', 'adult-cpr', 'child-cpr', 'adult-choking', 'child-choking', 'water',
            'power', 'flood', 'earthquake', 'fallout', 'cyber', 'pandemic', 'communications', 'water-advisories')
CARD_COUNT = len(CARD_IDS) + 2
SHEET_COUNT = CARD_COUNT // 2
FONT_SIZE = 10.4
INK = '#18372f'
LABELS = {
    'en': {
        'draft': 'UNREVIEWED DRAFT - incomplete emergency reference',
        'sources': 'Full sources and related guides: companion handbook PDF.',
        'plan': 'Household contact card', 'care': 'Support and essentials',
        'private': 'Private once completed. Keep a copy where your household can find it.',
        'fields': [('Names / household', 2), ('Home address / location', 2),
                   ('Contact nearby - name and number', 2), ('Contact outside the area - name and number', 2),
                   ('Meeting point near home', 2), ('Alternative meeting point / address', 2),
                   ('Written / checked on', 1)],
        'care_fields': [('Help needed / who can help', 2), ('Medicines / care-plan location / care contact', 3),
                        ('Equipment / power arrangements', 2), ('Children / pets / dependants', 2),
                        ('Emergency bag / paper map / document locations', 2), ('Other notes', 3)],
        'print': 'Print A4 landscape at 100% / actual size. Single-sided. Cut on the centre line.',
        'card': 'Card', 'edition': 'Edition', 'checked': 'Source comparison',
    },
    'de': {
        'draft': 'UNGEPRÜFTER ENTWURF - unvollständige Notfallhilfe',
        'sources': 'Vollständige Quellen und weitere Anleitungen: Handbuch-PDF.',
        'plan': 'Kontaktkarte für den Haushalt', 'care': 'Unterstützung und Ausrüstung',
        'private': 'Ausgefüllt privat. Eine Kopie für den Haushalt zugänglich aufbewahren.',
        'fields': [('Namen / Haushalt', 2), ('Wohnadresse / Standort', 2),
                   ('Kontakt in der Nähe - Name und Nummer', 2), ('Überregionaler Kontakt - Name und Nummer', 2),
                   ('Treffpunkt in Hausnähe', 2), ('Alternativer Treffpunkt / Adresse', 2),
                   ('Ausgefüllt / geprüft am', 1)],
        'care_fields': [('Benötigte Hilfe / wer helfen kann', 2), ('Medikamente / Ort des Versorgungsplans / Kontakt', 3),
                        ('Geräte / Stromversorgung', 2), ('Kinder / Haustiere / Angehörige', 2),
                        ('Notgepäck / Papierkarte / Aufbewahrungsorte', 2), ('Weitere Notizen', 3)],
        'print': 'A4 quer, 100 % / tatsächliche Größe, einseitig drucken. An der Mittellinie schneiden.',
        'card': 'Karte', 'edition': 'Ausgabe', 'checked': 'Quellenabgleich',
    },
}


def digest(payload):
    return hashlib.sha256(payload).hexdigest()


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + '\n').encode('utf-8')


def rich(text):
    return re.sub(r'\*\*(.+?)\*\*', r'<b>\1</b>', html.escape(text)).replace('\n', '<br/>')


def review_markdown(text):
    rendered = []
    for block in text.split('\n\n'):
        if block.startswith('## '):
            heading, _, body = block[3:].partition('\n')
            rendered.append('<h4>' + rich(heading) + '</h4>')
            if body: rendered.append('<p>' + rich(body) + '</p>')
        else: rendered.append('<p>' + rich(block) + '</p>')
    return ''.join(rendered)


def plain_blocks(text):
    """Stable, exact canonical paragraph blocks, including their section headings."""
    return [{'block_id': f'B{index:03d}', 'text': block,
             'kind': 'section' if block.startswith('## ') else 'paragraph'}
            for index, block in enumerate(text.split('\n\n'), 1)]


def review_blocks(article, language):
    translated = article[language]
    return ([{'block_id': key, 'kind': field, 'text': translated[field]}
             for key,field in [('T000','title'), ('S000','summary'), ('K000','keywords')]]
            + plain_blocks(translated['body']))


def csv_cell(value):
    """CSV quoting alone does not prevent spreadsheet formula evaluation."""
    value = str(value)
    return "'" + value if value.lstrip().startswith(('=', '+', '-', '@')) or value.startswith(('\t', '\r', '\n')) else value


def source_inventory(data, as_of):
    by_url = {}
    for article in data['articles']:
        for url in article['sources']:
            by_url.setdefault(url, []).append(article['id'])
    return {
        'schema_version': 1, 'as_of': as_of.isoformat(),
        'note': 'Inventory only. URLs were not fetched; availability, editions, rights and claims are not verified.',
        'sources': [{'url': url, 'article_ids': sorted(set(ids))} for url, ids in sorted(by_url.items())],
        'reviews': [{'article_id': a['id'], 'status': a['status'], 'review_due': a['review_due'],
                     'due_on_or_before_as_of': date.fromisoformat(a['review_due']) <= as_of,
                     'content_sha256': content_digest(a)} for a in data['articles']],
    }


def review_html(data, as_of):
    articles = []
    for a in data['articles']:
        languages = ''.join(f'<section lang="{lang}"><h3>{html.escape(a[lang]["title"])}</h3>'
                            f'<p><strong>{html.escape(a[lang]["summary"])}</strong></p>{review_markdown(a[lang]["body"])}</section>'
                            for lang in ('en', 'de'))
        sources = ''.join(f'<li><a href="{html.escape(url, quote=True)}">{html.escape(url)}</a></li>' for url in a['sources'])
        articles.append(f'<article id="{a["id"]}"><h2>{html.escape(a["en"]["title"])}</h2>'
                        f'<p><b>Recorded status: {html.escape(a["status"])}</b> | ID: {a["id"]} | '
                        f'Source comparison: {a["checked_at"]} | Review due: {a["review_due"]}</p>'
                        f'<p class="hash">Content SHA-256: {content_digest(a)}</p><div class="parallel">{languages}</div>'
                        f'<h3>Sources supplied by the draft; not a claim-level mapping</h3><ul>{sources}</ul>'
                        f'<p>Region: {html.escape(a["region"])}. Reuse note: {html.escape(a["reuse"])}</p>'
                        f'<details><summary>Existing review records (not independently verified)</summary><pre>{html.escape(json.dumps({k:a.get(k) for k in ("professional_review", "translation_review")}, ensure_ascii=False, indent=2))}</pre></details>'
                        '<p><a href="#top">Back to contents</a></p></article>')
    contents = ''.join(f'<li><a href="#{a["id"]}">{html.escape(a["en"]["title"])} / {html.escape(a["de"]["title"])}</a></li>' for a in data['articles'])
    return ('''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Open Emergency Handbook - bilingual reviewer pack</title><style>
body{max-width:1200px;margin:auto;padding:24px;font:18px/1.6 system-ui;color:#18372f;background:#fff;overflow-wrap:anywhere}
h1,h2,h3,h4{line-height:1.25}a{color:#086850}article{margin:40px 0;border-top:2px solid #47635b;padding-top:20px}
.parallel{display:grid;grid-template-columns:1fr 1fr;gap:32px}.hash,pre{font:14px/1.5 monospace;white-space:pre-wrap;overflow-wrap:anywhere}
.notice{padding:16px;border:2px solid #735300}details{padding:12px;border:1px solid #47635b}
@media(max-width:750px){.parallel{grid-template-columns:1fr}}@media print{body{font-size:10pt}.parallel{display:block}article{break-before:page}nav{break-after:page}}
</style><header id="top"><h1>Bilingual reviewer pack</h1><p class="notice"><b>REVIEW WORKSHEET - NOT APPROVED EMERGENCY GUIDANCE.</b>
Source organisations do not endorse these adaptations. No review is inferred from this generated pack. External source links require internet.</p>'''
            + f'<p>Content edition: {data["edition"]}. Inventory date: {as_of.isoformat()}. '
            'Full English/German bodies are shown without shortening. Use the accompanying CSV to record paragraph/section evidence, '
            'and the JSON templates to prepare real review records. Blank templates are not approvals.</p></header>'
            + '<nav aria-label="Review contents"><ol>' + contents + '</ol></nav><main>' + ''.join(articles)
            + '</main><footer>GPLv3. Fork of ligi/SurvivalManual. No agency endorsement.</footer></html>')


def review_outputs(data, matrix, as_of):
    stream = io.StringIO(newline='')
    fields = ['article_id', 'language', 'block_id', 'kind', 'content_sha256', 'exact_draft_text',
              'candidate_source_urls_not_mapped', 'verified_source_url', 'source_edition_date',
              'page_section_or_claim_locator', 'finding_or_correction', 'reviewer', 'evidence']
    writer = csv.writer(stream, lineterminator='\n'); writer.writerow(fields)
    for a in data['articles']:
        for language in ('en', 'de'):
            for block in review_blocks(a, language):
                writer.writerow([csv_cell(v) for v in [a['id'], language, block['block_id'], block['kind'],
                                 content_digest(a), block['text'], '\n'.join(a['sources']), '', '', '', '', '', '']])
    templates = {'schema_version': 1, 'note': 'Blank work templates only; do not import as sign-off. Complete actual qualified and translation reviews first.',
                 'articles': [{'article_id': a['id'], 'expected_content_sha256': content_digest(a),
                               **{kind: dict(reviewer='', role='', date='', scope='', evidence='', content_sha256=content_digest(a))
                                  for kind in ('professional_review', 'translation_review')}} for a in data['articles']]}
    return {
        'OpenEmergencyHandbook-review.html': review_html(data, as_of).encode('utf-8'),
        'source-mapping-worksheet.csv': stream.getvalue().encode('utf-8-sig'),
        'review-record-templates.json': json_bytes(templates),
        'source-maintenance-inventory.json': json_bytes(source_inventory(data, as_of)),
        'coverage-report.json': json_bytes(coverage_report(data, matrix)),
        'handbook-review-snapshot.json': json_bytes(data),
    }


def card_pdf(data, language):
    # Lazy imports let the review-only generation/tests run without PDF packages.
    from reportlab.lib import colors
    from reportlab.lib.pagesizes import A4, landscape
    from reportlab.lib.styles import ParagraphStyle
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
    from reportlab.pdfgen import canvas
    from reportlab.platypus import Paragraph

    fonts = Path('/usr/share/fonts/truetype/dejavu')
    for face, filename in [('Card', 'DejaVuSans.ttf'), ('Card-Bold', 'DejaVuSans-Bold.ttf')]:
        if face not in pdfmetrics.getRegisteredFontNames():
            pdfmetrics.registerFont(TTFont(face, str(fonts / filename)))
    pdfmetrics.registerFontFamily('Card', normal='Card', bold='Card-Bold', italic='Card', boldItalic='Card-Bold')
    page_w, page_h = landscape(A4); card_w = page_w / 2; margin = 22
    width = card_w - 2 * margin; labels = LABELS[language]
    styles = {
        'body': ParagraphStyle('body', fontName='Card', fontSize=FONT_SIZE, leading=12.4, textColor=colors.HexColor(INK), spaceAfter=4),
        'head': ParagraphStyle('head', fontName='Card-Bold', fontSize=11, leading=13, textColor=colors.HexColor(INK), spaceAfter=2),
        'title': ParagraphStyle('title', fontName='Card-Bold', fontSize=18, leading=22, textColor=colors.HexColor(INK), spaceAfter=8),
        'small': ParagraphStyle('small', fontName='Card', fontSize=8, leading=10, textColor=colors.HexColor(INK)),
    }
    by_id = {a['id']: a for a in data['articles']}
    missing = set(CARD_IDS) - set(by_id)
    if missing: raise ValueError(f'Missing quick-card guides: {sorted(missing)}')
    out = io.BytesIO(); c = canvas.Canvas(out, pagesize=(page_w, page_h), invariant=1, pageCompression=1)
    c.setTitle(f'Open Emergency Handbook - {language.upper()} - unreviewed A5 quick cards')
    c.setAuthor('Open Emergency Handbook contributors')

    def paragraph(text, style, x, y):
        p = Paragraph(text, styles[style]); _, h = p.wrap(width, page_h)
        p.drawOn(c, x, y-h)
        return y-h-styles[style].spaceAfter

    def chrome(index, title):
        x = (index % 2) * card_w + margin
        c.setFillColor(colors.HexColor(INK)); c.setFont('Card-Bold', 8)
        c.drawString(x, page_h-25, 'OPEN EMERGENCY HANDBOOK')
        c.setFont('Card', 8); c.drawRightString(x+width, page_h-25, f'{labels["card"]} {index+1}/{CARD_COUNT}')
        y = paragraph(html.escape(title), 'title', x, page_h-38)
        y = paragraph('<b>' + html.escape(labels['draft']) + '</b>', 'small', x, y)
        c.setStrokeColor(colors.HexColor('#899a94')); c.line(x, y-3, x+width, y-3)
        c.setFont('Card', 7.5)
        c.drawString(x, 20, f'{labels["edition"]}: {data["edition"]} | {language.upper()} | GPLv3')
        return x, y-12

    for index, aid in enumerate(CARD_IDS):
        a = by_id[aid]; x, y = chrome(index, a[language]['title'])
        y = paragraph(rich(a[language]['summary']), 'head', x, y)
        for block in a[language]['body'].split('\n\n'):
            if block.startswith('## '):
                heading, _, rest = block[3:].partition('\n')
                y = paragraph(rich(heading), 'head', x, y-2)
                if rest: y = paragraph(rich(rest), 'body', x, y)
            else: y = paragraph(rich(block), 'body', x, y)
        if y < 61:
            raise ValueError(f'Quick-card overflow: {aid}/{language}; {y:.1f}pt remains. Split/revise layout, never remove guidance.')
        paragraph(html.escape(labels['sources']), 'small', x, 55)
        paragraph(f'{labels["checked"]}: {a["checked_at"]} | {aid} | {content_digest(a)[:12]}', 'small', x, 42)
        if index % 2:
            c.setDash(3, 3); c.line(card_w, 12, card_w, page_h-12); c.setDash(); c.showPage()
    for offset, (title_key, fields_key) in enumerate([('plan','fields'), ('care','care_fields')], len(CARD_IDS)):
        x, y = chrome(offset, labels[title_key]); y = paragraph(html.escape(labels['private']), 'small', x, y)
        for title, lines in labels[fields_key]:
            y = paragraph(html.escape(title), 'head', x, y-7)
            for _ in range(lines):
                y -= 16; c.line(x, y, x+width, y)
        if y < 70: raise ValueError(f'Household-card overflow: {language}/{title_key}')
        paragraph(html.escape(labels['print']), 'small', x, 55)
    c.setDash(3, 3); c.line(card_w, 12, card_w, page_h-12); c.setDash(); c.showPage(); c.save()
    return out.getvalue()


def outputs(data, matrix, as_of):
    validate(data)
    result = review_outputs(data, matrix, as_of)
    result['COPYING.txt'] = (ROOT/'COPYING').read_bytes()
    result['READ_ME_FIRST.txt'] = (f'''OPEN EMERGENCY HANDBOOK - PAPER AND REVIEW PACK
Content edition / Inhaltsausgabe: {data['edition']}
Inventory date / Inventardatum: {as_of.isoformat()}

UNREVIEWED DEVELOPMENT DRAFTS / UNGEPRUEFTE ENTWUERFE
All summaries and translations still require qualified review. Generating or
hashing this pack is not approval. Follow emergency services/current official
instructions. This selection is not the complete handbook or first-aid training.
Alle Zusammenfassungen und Uebersetzungen sind fachlich ungeprueft. Dieser
Auszug ist kein vollstaendiges Handbuch und ersetzt keinen Erste-Hilfe-Kurs.

PRINT / DRUCK
Choose the English (-en.pdf) or German (-de.pdf) quick cards. Print A4 landscape,
100% / actual size, single-sided. Cut on the dashed centre line. Eight sheets
make fourteen complete guide cards and two blank household/support cards.
A4 quer, 100% / tatsaechliche Groesse, einseitig. An der gestrichelten Mittellinie
schneiden: acht Blaetter, vierzehn Anleitungen und zwei Haushaltskarten.
Write household details by hand. Completed sheets can contain private data;
do not upload them to public issues or PRs. Test a real printed copy for legibility.
Haushaltsdaten handschriftlich ergaenzen und privat aufbewahren; nicht oeffentlich
hochladen. Lesbarkeit und Druckraender an einem echten Ausdruck pruefen.

REVIEW / PRUEFUNG
Open OpenEmergencyHandbook-review.html locally in a browser. It needs no
JavaScript/network to show the full bilingual text. Source links need internet.
The CSV has exact draft blocks and blank verified-source/edition/locator/finding/
reviewer/evidence fields. Candidate links are not verified claim mappings.
The JSON review templates are blank work aids, never completed sign-off.
Complete real professional and translation review before editing canonical
content/review records; recheck hashes after changes. Do not import blank forms.
Die HTML-Datei zeigt alle dreissig Anleitungen zweisprachig und ohne JavaScript.
CSV und JSON sind Arbeitsvorlagen, keine Freigaben. Quellenzuordnungen, Ausgaben,
Befunde und qualifizierte Pruefung muessen tatsaechlich erarbeitet werden.

INTEGRITY / INTEGRITAET
SHA256SUMS.txt covers the other inner files. PACK-MANIFEST.json identifies the
edition, selected cards and hashes. Hashes detect changes, not authenticity or
accuracy. The source inventory did not fetch URLs or verify rights/editions.
Pruefsummen erkennen Aenderungen; sie belegen keine Echtheit oder Fachpruefung.
No household data or private keys are included / Keine Haushaltsdaten oder Schluessel.

Full companion handbook PDFs, project source and review process:
https://github.com/hoyleb/OpenEmergencyHandbook
docs/PAPER_AND_REVIEW_PACKS.md and docs/CONTENT_REVIEW.md
GPLv3; see COPYING.txt. Fork of ligi/SurvivalManual. No source-organisation endorsement.
''').encode('utf-8')
    for lang in ('en', 'de'):
        result[f'OpenEmergencyHandbook-quick-cards-{lang}.pdf'] = card_pdf(data, lang)
    manifest = {
        'schema_version': 1, 'content_edition': data['edition'], 'inventory_as_of': as_of.isoformat(),
        'canonical_content_sha256': digest(json_bytes(data)),
        'note': 'Content snapshot uses deterministic sorted JSON. Hashes detect changes, not clinical approval or authenticity.',
        'paper_layout': {'paper': 'A4 landscape', 'sheets_per_language': SHEET_COUNT, 'cards_per_language': CARD_COUNT,
                         'guide_ids': list(CARD_IDS), 'guide_body_minimum_points': FONT_SIZE,
                         'single_sided': True, 'scale_percent': 100},
        'files': {name: {'bytes':len(payload), 'sha256':digest(payload)} for name,payload in sorted(result.items())},
    }
    result['PACK-MANIFEST.json'] = json_bytes(manifest)
    result['SHA256SUMS.txt'] = ''.join(digest(payload)+'  '+name+'\n' for name,payload in sorted(result.items())).encode('ascii')
    # A transport container cannot include its own checksum. Inner files carry
    # the complete manifest; fixed ZIP metadata keeps the download reproducible.
    bundle = io.BytesIO()
    with zipfile.ZipFile(bundle, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name,payload in sorted(result.items()):
            info = zipfile.ZipInfo(name, date_time=(2000,1,1,0,0,0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, payload, compresslevel=9)
    result['OpenEmergencyHandbook-paper-review-pack.zip'] = bundle.getvalue()
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'dist/print-pack')
    parser.add_argument('--as-of', type=date.fromisoformat, required=True, help='Explicit inventory date; does not change canonical review dates')
    parser.add_argument('--check', action='store_true', help='Fail rather than write if any generated file is missing/stale')
    args = parser.parse_args()
    data = json.loads((ROOT/'content/handbook.json').read_text()); matrix = json.loads((ROOT/'content/coverage.json').read_text())
    generated = outputs(data, matrix, args.as_of)
    if args.check:
        stale = [name for name,payload in generated.items() if not (args.output/name).is_file() or (args.output/name).read_bytes()!=payload]
        if stale: raise SystemExit('Missing/stale print pack: '+', '.join(stale))
    else:
        args.output.mkdir(parents=True, exist_ok=True)
        for name,payload in generated.items(): (args.output/name).write_bytes(payload)
    print(json.dumps({'files': len(generated), 'sheets_per_language': SHEET_COUNT, 'guide_cards_per_language': len(CARD_IDS),
                      'as_of': args.as_of.isoformat(), 'checked': args.check}))


if __name__ == '__main__':
    main()
