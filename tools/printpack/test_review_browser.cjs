/* Standalone reviewer handoff: no scripts, responsive bilingual text, no network. */
const {chromium, firefox, webkit} = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const {pathToFileURL} = require('node:url');
const assert = require('node:assert/strict');

(async () => {
  const root = path.resolve(__dirname, '../..');
  const source = JSON.parse(fs.readFileSync(path.join(root,'content/handbook.json'),'utf8'));
  const output = path.join(root,'print-pack-evidence');
  fs.mkdirSync(output,{recursive:true});
  const evidence = {engines:[],external_requests:0,page_errors:0};
  for (const [name, engine] of Object.entries({chromium,firefox,webkit})) {
    const browser = await engine.launch({headless:true});
    try {
      const context = await browser.newContext({javaScriptEnabled:false});
      context.setDefaultTimeout(15000);
      const page = await context.newPage();
      const external = [], errors = [];
      await page.route(/^https?:/, route => {external.push(route.request().url()); return route.abort();});
      page.on('pageerror', error => errors.push(String(error)));
      await page.goto(pathToFileURL(path.join(root,'docs/packs/OpenEmergencyHandbook-review.html')).href);
      assert.equal(await page.locator('main > article').count(),source.articles.length);
      for (const a of source.articles) {
        for (const lang of ['en','de']) {
          const text = await page.locator(`#${a.id} section[lang="${lang}"]`).innerText();
          assert(text.includes(a[lang].title));
          assert(text.includes(a[lang].summary));
          assert(text.replace(/\s+/g,' ').includes(a[lang].body.split('\n\n').at(-1).replace(/^## /,'').replace(/\*\*/g,'').replace(/\s+/g,' ')));
        }
      }
      await page.setViewportSize({width:1280,height:900});
      await page.locator('nav a[href="#first-aid"]').click();
      assert(page.url().endsWith('#first-aid'));
      await page.screenshot({path:path.join(output,`review-${name}-desktop.png`)});
      console.log(`${name}: all bilingual text and desktop anchor checks passed`);
      await page.setViewportSize({width:360,height:800});
      // JavaScript stays disabled in the artifact; temporary test CSS is injected by the harness.
      // addStyleTag waits for page load events that can stall with scripts disabled.
      // A direct harness evaluation changes only the existing body's style.
      await page.evaluate(() => { document.body.style.fontSize='36px'; });
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth+1));
      const sections = await page.locator('#first-aid .parallel > section').first().boundingBox();
      assert(sections.width <= 312);
      await page.screenshot({path:path.join(output,`review-${name}-mobile-large-text.png`)});
      await page.locator('#first-aid details summary').click();
      assert(await page.locator('#first-aid details').getAttribute('open') !== null);
      assert.equal(external.length,0); assert.equal(errors.length,0);
      console.log(`${name}: mobile 200% text, source disclosure and offline checks passed`);
      evidence.engines.push({engine:name,guides:source.articles.length,languages:2,javascript:false,
                            desktop:true,mobile_200_percent_css_text:true,external_requests:0,page_errors:0});
      await context.close();
    } finally {await browser.close();}
  }
  fs.writeFileSync(path.join(output,'REVIEW-BROWSER-TESTS.json'),JSON.stringify(evidence,null,2)+'\n');
  console.log('Reviewer pack passed in Chromium, Firefox and WebKit without JavaScript.');
})().catch(error => {console.error(error);process.exitCode=1;});
