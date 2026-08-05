import urllib.request
import json
url = 'https://api.github.com/search/repositories?q=Domains'
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req) as response:
    data = json.loads(response.read().decode())
    for item in data['items']:
        if 'Astrae' in item['full_name'] or 'astrae' in item['full_name']:
            print(item['full_name'])
