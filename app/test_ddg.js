async function check() {
    try {
      const res = await fetch("https://lite.duckduckgo.com/lite/", { 
        method: "POST",
        headers: { 
            "User-Agent": "Mozilla/5.0",
            "Content-Type": "application/x-www-form-urlencoded" 
        },
        body: "q=streamingcommunity+nuovo+indirizzo+2026"
      });
      const text = await res.text();
      console.log(text);
    } catch(e) {
      console.log(e);
    }
}
check();
