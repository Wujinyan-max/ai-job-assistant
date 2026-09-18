const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle' });
  // Fill login
  await page.fill('input[placeholder="用户名"]', 'demo');
  await page.fill('input[placeholder="密码"]', '123456');
  await page.click('button:has-text("登 录")');
  await page.waitForURL('**/dashboard', { timeout: 8000 });
  await page.waitForTimeout(2000);
  await page.screenshot({ path: 'C:/Users/wujinyan/Desktop/ai-job-assistant/frontend/tests/shot-dashboard.png' });
  await page.goto('http://localhost:5173/board', { waitUntil: 'networkidle' });
  await page.waitForTimeout(1500);
  await page.screenshot({ path: 'C:/Users/wujinyan/Desktop/ai-job-assistant/frontend/tests/shot-board.png' });
  await page.goto('http://localhost:5173/applications', { waitUntil: 'networkidle' });
  await page.waitForTimeout(1500);
  await page.screenshot({ path: 'C:/Users/wujinyan/Desktop/ai-job-assistant/frontend/tests/shot-apps.png' });
  await browser.close();
  console.log('done');
})();
