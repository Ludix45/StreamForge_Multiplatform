const https = require('https');
https.get('https://api.github.com/search/repositories?q=Domains', {headers: {'User-Agent': 'Node'}}, (res) => {
  let data = '';
  res.on('data', chunk => data += chunk);
  res.on('end', () => {
    try {
      const items = JSON.parse(data).items;
      items.forEach(i => {
        if(i.full_name.toLowerCase().includes('astrae')) console.log(i.full_name);
      });
    } catch(e) {}
  });
});
