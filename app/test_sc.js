const domains = [
  "streamingcommunity.computer",
  "streamingcommunity.sbs",
  "streamingcommunity.care",
  "streamingcommunity.boston",
  "streamingcommunity.bz",
  "streamingcommunity.cz",
  "streamingcommunity.co",
  "streamingcommunity.at",
  "streamingcommunity.foo",
  "streamingcommunity.broker",
  "streamingcommunity.vip",
  "streamingcommunity.tv",
  "streamingcommunity.re",
  "streamingcommunity.wtf"
];
async function check() {
  for (const d of domains) {
    try {
      const res = await fetch(`https://${d}/`, { headers: { "User-Agent": "Mozilla/5.0" }, redirect: 'follow' });
      const text = await res.text();
      if (text.includes("data-page")) {
        console.log("Found:", d, "URL:", res.url);
        return;
      }
    } catch(e) {}
  }
  console.log("None found");
}
check();
