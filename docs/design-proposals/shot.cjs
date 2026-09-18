const { chromium } = require(process.env.PW_PATH);
(async () => {
  const browser = await chromium.launch({ headless: true });
  for (const [name, file] of [['a-editorial','a-editorial.html'],['b-console','b-console.html'],['c-polished','c-polished.html']]) {
    const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
    await page.goto('file:///C:/Users/wujinyan/Desktop/ai-job-assistant/docs/design-proposals/' + file, { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: name + '.png' });
    await page.close();
    console.log(name, 'done');
  }
  await browser.close();
})();
