#!/usr/bin/env python3
"""Print editions from the canonical bilingual source, never a second content copy.

Install tools/requirements-pdf.txt and fonts-dejavu-core, then run this script.
The PDFs are development drafts with the same limitations as the Android app.
"""
import html
import json
from pathlib import Path
import re

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import BaseDocTemplate, Frame, PageTemplate, Paragraph, PageBreak, Spacer
from reportlab.platypus.tableofcontents import TableOfContents

from build_handbook import ROOT, validate

INK = colors.HexColor('#18372f')
MUTED = colors.HexColor('#47635b')


def safe(text):
    text = text.replace('\u2011', '-').replace('\u2013', '-').replace('\u2014', '-')
    return html.escape(text)


def rich(text):
    return re.sub(r'\*\*(.+?)\*\*', r'<b>\1</b>', safe(text)).replace('\n', '<br/>')


class HandbookDocument(BaseDocTemplate):
    def __init__(self, path, language, edition):
        super().__init__(str(path), pagesize=A4, leftMargin=44, rightMargin=44, topMargin=57, bottomMargin=48,
                         title=f'Open Emergency Handbook - {language.upper()} - Development preview',
                         author='Open Emergency Handbook contributors')
        self.language, self.edition = language, edition
        frame = Frame(self.leftMargin, self.bottomMargin, self.width, self.height, id='body', leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)
        self.addPageTemplates(PageTemplate(id='handbook', frames=frame, onPage=self.page_chrome))

    def page_chrome(self, canvas, doc):
        canvas.saveState()
        canvas.setFont('Handbook', 8)
        canvas.setFillColor(MUTED)
        canvas.drawString(44, A4[1] - 30, 'OPEN EMERGENCY HANDBOOK')
        canvas.drawRightString(A4[0] - 44, A4[1] - 30, f'{self.edition} | {self.language.upper()}')
        canvas.setStrokeColor(colors.HexColor('#c5d3cb'))
        canvas.line(44, 39, A4[0] - 44, 39)
        label = 'UNREVIEWED DEVELOPMENT PREVIEW' if self.language == 'en' else 'UNGEPRÜFTER ENTWURF'
        canvas.drawString(44, 26, label)
        canvas.drawRightString(A4[0] - 44, 26, str(doc.page))
        canvas.restoreState()

    def afterFlowable(self, flowable):
        key = getattr(flowable, 'bookmark', None)
        if key:
            self.canv.bookmarkPage(key)
            self.canv.addOutlineEntry(flowable.getPlainText(), key, level=0)
            self.notify('TOCEntry', (0, flowable.getPlainText(), self.page, key))


def make_pdf(data, language, output):
    font_dir = Path('/usr/share/fonts/truetype/dejavu')
    for face, file in [('Handbook', 'DejaVuSans.ttf'), ('Handbook-Bold', 'DejaVuSans-Bold.ttf')]:
        if face not in pdfmetrics.getRegisteredFontNames():
            pdfmetrics.registerFont(TTFont(face, str(font_dir / file)))
    pdfmetrics.registerFontFamily('Handbook', normal='Handbook', bold='Handbook-Bold', italic='Handbook', boldItalic='Handbook-Bold')
    styles = getSampleStyleSheet()
    body = ParagraphStyle('Body', fontName='Handbook', fontSize=10.4, leading=15.2, spaceAfter=8, textColor=INK, alignment=TA_LEFT)
    heading = ParagraphStyle('Section', parent=body, fontName='Handbook-Bold', fontSize=12, leading=16, spaceBefore=8, spaceAfter=4, keepWithNext=True)
    title = ParagraphStyle('Guide', parent=heading, fontSize=23, leading=28, spaceBefore=0, spaceAfter=12)
    small = ParagraphStyle('Source', parent=body, fontSize=8, leading=11, textColor=MUTED, wordWrap='CJK')
    cover_title = ParagraphStyle('Cover', parent=title, fontSize=34, leading=40)
    toc_style = ParagraphStyle('Contents', parent=body, fontSize=10.5, leading=17, spaceBefore=3)
    de = language == 'de'
    story = [Spacer(1, 45), Paragraph('Open Emergency<br/>Handbook', cover_title),
             Paragraph('Deutsch' if de else 'English', title),
             Paragraph('Kostenlos. Quelloffen. Offline lesbar.' if de else 'Free. Open source. Readable offline.', heading),
             Paragraph('Entwicklungsentwurf - unvollständig und ungeprüft.' if de else 'Development preview - incomplete and unreviewed.', heading),
             Paragraph('Alle Zusammenfassungen und Übersetzungen sind Entwürfe ohne fachliche Prüfung. Zum Testen und Vorbereiten; aktuelle amtliche Anweisungen und Rettungsdienste beachten. Dies ist weder eine vollständige Überlebensbibliothek noch ein Ersatz für Erste-Hilfe-Ausbildung.' if de else 'Every summary and translation is a draft without professional review. Use for testing and preparation; follow emergency services and current official instructions. This is neither a complete survival library nor a replacement for first-aid training.', body),
             Paragraph('Bei Lebensgefahr in Deutschland/EU: <b>112</b>. Im Ausland örtliche Nummern prüfen.' if de else 'Life-threatening emergency in Germany/EU: <b>112</b>. Check local numbers when travelling.', body),
             Paragraph(f'{len(data["articles"])} ' + ('Anleitungen. Ausgabe: ' if de else 'guides. Edition: ') + safe(data['edition']), body),
             Paragraph('Alle Anweisungstexte sind enthalten. Quellenlinks benötigen Internet. Keine Live-Warnungen, Strahlungsmessung oder geprüften Fluchtwege.' if de else 'All instruction text is included. Source links require internet. No live alerts, radiation measurements or verified escape routes.', body),
             Paragraph('GPLv3. Fork von ligi/SurvivalManual. Quellenorganisationen unterstützen diese Zusammenfassungen nicht offiziell.' if de else 'GPLv3. Fork of ligi/SurvivalManual. Source organisations do not endorse these summaries.', small),
             Paragraph('<link href="https://github.com/hoyleb/OpenEmergencyHandbook">github.com/hoyleb/OpenEmergencyHandbook</link>', small), PageBreak(),
             Paragraph('Inhalt' if de else 'Contents', title)]
    toc = TableOfContents()
    toc.levelStyles = [toc_style]
    story.extend([toc, PageBreak()])
    by_id = {a['id']: a for a in data['articles']}
    for index, article in enumerate(data['articles']):
        translated = article[language]
        head = Paragraph(safe(translated['title']), title)
        head.bookmark = article['id']
        story.extend([head, Paragraph(safe(translated['summary']), heading)])
        for block in translated['body'].split('\n\n'):
            if block.startswith('## '):
                first, _, rest = block[3:].partition('\n')
                story.append(Paragraph(rich(first), heading))
                if rest: story.append(Paragraph(rich(rest), body))
            else:
                story.append(Paragraph(rich(block), body))
        if article.get('related'):
            story.append(Paragraph('Weitere Offline-Anleitungen' if de else 'Related offline guides', heading))
            links = ['<link href="#' + aid + '">' + safe(by_id[aid][language]['title']) + '</link>' for aid in article['related']]
            story.append(Paragraph(' / '.join(links), small))
        story.append(Paragraph('Quellen und Grenzen' if de else 'Sources and limitations', heading))
        story.append(Paragraph(('Quellenabgleich: ' if de else 'Source comparison: ') + article['checked_at'] + ('. Keine fachliche Prüfung dokumentiert.' if de else '. No professional review recorded.'), small))
        for url in article['sources']:
            escaped = safe(url)
            story.append(Paragraph(f'<link href="{escaped}">{escaped}</link>', small))
        if index < len(data['articles']) - 1:
            story.append(PageBreak())
    HandbookDocument(output, language, data['edition']).multiBuild(story)


if __name__ == '__main__':
    data = json.loads((ROOT / 'content/handbook.json').read_text())
    validate(data)
    (ROOT / 'dist').mkdir(exist_ok=True)
    for language in ('en', 'de'):
        output = ROOT / f'dist/OpenEmergencyHandbook-{language}.pdf'
        make_pdf(data, language, output)
        print(output)
