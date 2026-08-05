const https = require('https');
https.get('https://html.duckduckgo.com/html/?q=streamingcommunity+nuovo+link+2026', (res) => {
  let data = '';
  res.on('data', chunk => data += chunk);
  res.on('end', () => {
    const matches = data.match(/streamingcommunity\.[a-z]+/gi);
    if(matches) console.log(Array.from(new Set(matches)));
    else console.log('no matches');
  });
});
