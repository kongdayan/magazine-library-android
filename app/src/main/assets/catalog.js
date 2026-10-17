JSON.stringify((() => {
  const text = e => (e?.textContent || '').replace(/\s+/g, ' ').trim();
  const site = href => { try { const u = new URL(href, location.href); return u.protocol === 'https:' && ['magazinelib.com','www.magazinelib.com'].includes(u.hostname) && !u.username && !u.password; } catch { return false; } };
  const image = e => { const i=e?.querySelector('.entry-featured-media img,.vkwpb_img_wrap img,img.wp-post-image'); return i ? [i.getAttribute('data-src'),i.currentSrc,i.src].find(s=>s&&/^https:/.test(s)) || '' : ''; };
  const categories = e => Array.from(e?.querySelector('.entry-categories')?.querySelectorAll('a') || []).map(a=>text(a)).filter(s=>s&&s!=='All');
  const item = e => { const a=e.querySelector('.entry-title a'); if(!a||!site(a.href))return null; return {url:a.href,title:text(a),image:image(e),date:e.querySelector('time')?.getAttribute('datetime')||'',categories:categories(e)}; };
  const uniq = list => Array.from(new Map(list.filter(Boolean).map(x=>[x.url,x])).values());
  const title=document.title||'', body=text(document.body);
  const challenge=/just a moment|attention required|security verification/i.test(title)||!!document.querySelector('#challenge-running,#challenge-stage,.cf-challenge')||/^(?:.{0,160})verify you are human/i.test(body);
  if(challenge)return {url:location.href,challenge:true};
  const root=document.querySelector('#primary')||document.querySelector('main')||document.body;
  const h1=root.querySelector('article h1.entry-title');
  const article=h1?.closest('article');
  const pdfs=article?uniq(Array.from(article.querySelectorAll('.entry-content a[href]')).filter(a=>/action=vkpd_file|\.pdf(?:[?#]|$)/i.test(a.getAttribute('data-mzl-href')||a.href)).map(a=>({url:new URL(a.getAttribute('data-mzl-href')||a.href,location.href).href,title:text(a)||a.title||''}))):[];
  const options=uniq(Array.from(document.querySelectorAll('#g1-primary-nav a,.g1-primary-nav a,#g1-primary-nav-menu a,.entry-categories a')).filter(a=>site(a.href)&&new URL(a.href).pathname!=='/').map(a=>({url:a.href,title:text(a)}))).slice(0,180);
  let months=uniq(Array.from(document.querySelectorAll('.widget_archive a')).filter(a=>site(a.href)&&/\/\d{4}\/\d{2}\/$/.test(new URL(a.href).pathname)).map(a=>{const c=a.cloneNode(true);c.querySelectorAll('span').forEach(s=>s.remove());return {url:a.href,title:text(c)};})).slice(0,180);
  const pager=root.querySelector('.g1-pagination');
  const pageLinks=Array.from(pager?.querySelectorAll('a')||[]).filter(a=>site(a.href));
  const page=Number(text(pager?.querySelector('.g1-pagination-item-current')))||Number(location.pathname.match(/\/page\/(\d+)/)?.[1])||Number(new URL(location.href).searchParams.get('paged'))||1;
  const next=pageLinks.find(a=>a.classList.contains('next')||text(a)==='Next')?.href||'';
  const previous=pageLinks.find(a=>a.classList.contains('prev')||/Previous|Prev/.test(text(a)))?.href||'';
  const items=h1?uniq(Array.from(document.querySelectorAll('.g1-related-entries article')).map(item)).slice(0,12):uniq(Array.from(root.querySelectorAll('article')).map(item)).slice(0,50);
  if(!months.length){const dates=[...items.map(i=>i.date),article?.querySelector('time')?.getAttribute('datetime')||''];months=uniq(dates.filter(d=>/^\d{4}-\d{2}/.test(d)).map(d=>{const ym=d.slice(0,7);return {url:new URL('/'+ym.replace('-','/')+'/',location.origin).href,title:new Intl.DateTimeFormat('en-US',{month:'long',year:'numeric',timeZone:'UTC'}).format(new Date(ym+'-15T12:00:00Z'))};}));}
  const heading=h1?text(h1):text(root.querySelector('.g1-archive-title,.archive-title,.page-title'))||'Latest magazines';
  const empty=/nothing found|no results|no posts|no matches|couldn.t find|sorry.{0,100}(?:posts|find|results)/i.test(body);
  return {url:location.href,challenge:false,detail:!!h1,heading,image:image(article),date:article?.querySelector('time')?.getAttribute('datetime')||'',categories:categories(article),description:Array.from(article?.querySelectorAll('.entry-content > p')||[]).slice(0,1).map(text).join('\n').slice(0,1400),pdfs,items,options,months,page,next,previous,empty};
})())
