const https = require('https');
const options = {
  hostname: 'api.github.com',
  path: '/search/code?q=api_key+in:file+filename:tmdb+language:python',
  headers: {'User-Agent': 'Node'}
};
https.get(options, (res) => {
  let data = '';
  res.on('data', chunk => data += chunk);
  res.on('end', () => {
    try { console.log(JSON.parse(data)); } catch(e) {}
  });
});
