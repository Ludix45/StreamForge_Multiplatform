const fs = require('fs');
const html = fs.readFileSync('app/ddg.html', 'utf8');
const regex = /class="result-snippet"[^>]*>([^<]+)/gi;
let match;
while ((match = regex.exec(html)) !== null) {
  console.log(match[1]);
}
