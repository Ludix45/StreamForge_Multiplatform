const tlds = ["broker", "vip", "network", "cloud", "vet", "re", "co", "bz", "boston", "computer", "care", "at", "li", "to", "ru", "sbs", "foo", "agency", "uno", "link"];
async function check() {
  for (const tld of tlds) {
    const d = `streamingcommunity.${tld}`;
    try {
      const res = await fetch(`https://${d}/`, { headers: { "User-Agent": "Mozilla/5.0" } });
      const text = await res.text();
      if (text.includes("data-page") || text.includes("inertia")) {
        console.log("FOUND ACTIVE DOMAIN:", d);
        return;
      }
    } catch(e) {}
  }
  console.log("None found");
}
check();
