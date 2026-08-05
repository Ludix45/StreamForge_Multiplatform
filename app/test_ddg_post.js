const https = require('https');
const querystring = require('querystring');

const postData = querystring.stringify({ q: 'streamingcommunity nuovo indirizzo 2026' });

const options = {
  hostname: 'lite.duckduckgo.com',
  path: '/lite/',
  method: 'POST',
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
    'Content-Length': Buffer.byteLength(postData),
    'User-Agent': 'Mozilla/5.0'
  }
};

const req = https.request(options, (res) => {
  let data = '';
  res.on('data', (chunk) => { data += chunk; });
  res.on('end', () => {
    console.log(data);
  });
});

req.on('error', (e) => { console.error(e); });
req.write(postData);
req.end();
