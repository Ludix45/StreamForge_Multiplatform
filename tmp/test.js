const https = require('https');

https.get('https://api4devs.com/apis/movies', (res) => {
  let data = '';
  res.on('data', chunk => data += chunk);
  res.on('end', () => console.log('apis/movies:', data.slice(0, 1000)));
});
