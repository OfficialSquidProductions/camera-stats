from pathlib import Path
import json,base64
root=Path(__file__).resolve().parents[1]
files={}
for path,mime in [('index.html','text/html; charset=utf-8'),('privacy.html','text/html; charset=utf-8'),('assets/SP_GREEN.png','image/png'),('assets/squid-banner.png','image/png')]:
 files['/'+path]={'type':mime,'bytes':base64.b64encode((root/path).read_bytes()).decode()}
source='const files='+json.dumps(files,separators=(',',':'))+';\n'
source+='''export default { async fetch(request) {
  const url=new URL(request.url);
  if(url.hostname==='camerastats.squidproductions.org') {
    url.protocol='https:';url.hostname='shotreporter.squidproductions.org';url.port='';
    return Response.redirect(url.toString(),308);
  }
  if(!['GET','HEAD'].includes(request.method)) return new Response('Method not allowed',{status:405,headers:{Allow:'GET, HEAD'}});
  const file=files[url.pathname==='/'?'/index.html':url.pathname];
  if(!file) return new Response('Not found',{status:404});
  const bytes=Uint8Array.from(atob(file.bytes),c=>c.charCodeAt(0));
  return new Response(request.method==='HEAD'?null:bytes,{headers:{'Content-Type':file.type,'X-Content-Type-Options':'nosniff','Referrer-Policy':'strict-origin-when-cross-origin','Cache-Control':file.type.startsWith('text/html')?'public, max-age=300':'public, max-age=86400'}});
}};\n'''
(root/'worker/index.js').write_text(source)
print('Generated self-contained website Worker with legacy-host redirect and four retained public assets.')
