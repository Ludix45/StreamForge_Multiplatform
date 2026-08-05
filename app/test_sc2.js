const domains = [
  "streamingcommunity.computer",
  "streamingcommunity.boston",
  "streamingcommunity.cz",
  "streamingcommunity.at",
  "streamingcommunity.foo",
  "streamingcommunity.broker"
];
async function check() {
  for (const d of domains) {
    try {
      const res = await fetch(`https://${d}/`, { headers: { "User-Agent": "Mozilla/5.0" }, redirect: 'follow' });
      console.log(d, res.status, res.url);
      const text = await res.text();
      console.log(d, text.substring(0, 50));
    } catch(e) {
      console.log(d, "error");
    }
  }
}
check();
