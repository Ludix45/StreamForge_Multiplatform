const https = require('https');
https.get('https://api.github.com/repos/AstraeLabs/VibraVid/contents/', {headers: {'User-Agent': 'Node'}}, (res) => {
  let data = '';
  res.on('data', chunk => data += chunk);
  res.on('end', () => {
    try {
      console.log(JSON.parse(data));
    } catch(e) {}
  });
});
