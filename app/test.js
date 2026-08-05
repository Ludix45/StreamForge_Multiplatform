const https = require('https');

https.get('https://api4devs.com/movies', (res) => {
  let data = '';
  res.on('data', chunk => data += chunk);
  res.on('end', () => console.log(data));
}).on('error', err => console.log(err));

https.get('https://api4devs.com/apis/movies', (res) => {
  let data = '';
  res.on('data', chunk => data += chunk);
  res.on('end', () => console.log(data.slice(0, 1000)));
}).on('error', err => console.log(err));
