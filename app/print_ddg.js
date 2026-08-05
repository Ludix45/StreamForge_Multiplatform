const fs = require('fs');
const html = fs.readFileSync('app/ddg.html', 'utf8');
const text = html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');
console.log(text.substring(0, 4000));
