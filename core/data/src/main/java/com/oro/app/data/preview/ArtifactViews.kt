package com.oro.app.data.preview

/**
 * The pages the dev server draws for companion files the agent writes:
 * an SVG studio (pan and zoom on a checkerboard, code view, copy, download),
 * CSV as a sortable, filterable table, JSON as a collapsible tree, and plain
 * text. Each page carries the raw source as escaped text, so the code view
 * and the copy button always match the file. Pure string building.
 */
object ArtifactViews {

    /** The page for [text], or null when [kind] has no viewer page. [rawPath] is the file's own URL path. */
    fun page(kind: ArtifactKind, fileName: String, rawPath: String, text: String): String? = when (kind) {
        ArtifactKind.SVG -> shell(fileName, svgBody(fileName, rawPath, text), SVG_JS)
        ArtifactKind.CSV -> shell(fileName, dataBody(fileName, "csv", text), CSV_JS)
        ArtifactKind.JSON -> shell(fileName, dataBody(fileName, "json", text), JSON_JS)
        ArtifactKind.TEXT -> shell(fileName, textBody(fileName, text), TEXT_JS)
        else -> null
    }

    fun escape(text: String): String = buildString(text.length + 16) {
        for (c in text) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }

    private fun shell(title: String, body: String, script: String): String =
        "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>${escape(title)}</title><style>$CSS</style></head><body>$body" +
            "<script>$COMMON_JS\n$script</script></body></html>"

    private fun bar(fileName: String, extra: String): String =
        "<div class=\"bar\"><b class=\"name\">${escape(fileName)}</b><span class=\"sp\"></span>$extra</div>"

    private fun svgBody(fileName: String, rawPath: String, svg: String): String =
        bar(
            fileName,
            "<button id=\"zout\" aria-label=\"Zoom out\">−</button><button id=\"zin\" aria-label=\"Zoom in\">+</button>" +
                "<button id=\"fit\">Fit</button><button id=\"bg\">Backdrop</button>" +
                "<button id=\"tab\">Code</button><button id=\"copy\">Copy</button>" +
                "<a class=\"btn\" href=\"${escape(rawPath)}\" download=\"${escape(fileName)}\">Download</a>"
        ) +
            "<div id=\"stage\" class=\"stage checker\"><div id=\"pan\"><img id=\"art\" alt=\"${escape(fileName)}\" src=\"${escape(rawPath)}\"></div>" +
            "<span id=\"zoom\" class=\"pill\">100%</span></div>" +
            "<pre id=\"src\" hidden>${escape(svg)}</pre>"

    private fun dataBody(fileName: String, kind: String, text: String): String =
        bar(
            fileName,
            (if (kind == "csv") "<input id=\"filter\" type=\"search\" placeholder=\"Filter rows\" aria-label=\"Filter rows\">" else "") +
                "<span id=\"stat\" class=\"pill\"></span>" +
                "<button id=\"tab\">Source</button><button id=\"copy\">Copy</button>"
        ) +
            "<main id=\"view\" data-kind=\"$kind\"></main>" +
            "<pre id=\"src\" hidden>${escape(text)}</pre>"

    private fun textBody(fileName: String, text: String): String =
        bar(fileName, "<button id=\"wrap\">Wrap</button><button id=\"copy\">Copy</button>") +
            "<pre id=\"src\" class=\"doc\">${escape(text)}</pre>"

    private const val CSS = """
:root{color-scheme:light dark;--bg:#fff;--fg:#1c1b1f;--bar:#f4f1f6;--line:#d9d4de;--accent:#5b4bc4;--muted:#6d6874;--chip:#e8e4ee}
@media(prefers-color-scheme:dark){:root{--bg:#121212;--fg:#e6e1e5;--bar:#1d1b20;--line:#38343c;--accent:#b4a7ff;--muted:#a39dab;--chip:#2b2831}}
*{box-sizing:border-box}html,body{margin:0;height:100%}
body{background:var(--bg);color:var(--fg);font:14px/1.5 system-ui,-apple-system,Roboto,sans-serif;display:flex;flex-direction:column}
.bar{display:flex;flex-wrap:wrap;align-items:center;gap:6px;padding:8px 10px;background:var(--bar);border-bottom:1px solid var(--line);position:sticky;top:0;z-index:2}
.name{max-width:48vw;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.sp{flex:1}
button,.btn{font:inherit;color:var(--fg);background:var(--chip);border:0;border-radius:16px;padding:6px 12px;min-height:32px;text-decoration:none;display:inline-flex;align-items:center}
button:active,.btn:active{background:var(--line)}
input{font:inherit;color:var(--fg);background:var(--bg);border:1px solid var(--line);border-radius:16px;padding:5px 12px;min-width:0;width:9em}
.pill{color:var(--muted);font-size:12px}
.stage{position:relative;flex:1;overflow:hidden;touch-action:none;display:flex;align-items:center;justify-content:center}
.checker{background-color:#fff;background-image:linear-gradient(45deg,#d8d8d8 25%,transparent 25%,transparent 75%,#d8d8d8 75%),linear-gradient(45deg,#d8d8d8 25%,transparent 25%,transparent 75%,#d8d8d8 75%);background-size:20px 20px;background-position:0 0,10px 10px}
.stage.light{background:#fff}.stage.dark{background:#09090b}
#pan{transform-origin:0 0;will-change:transform}#art{display:block;width:min(88vw,520px);height:auto;max-height:80vh;user-select:none;-webkit-user-drag:none}
.stage .pill{position:absolute;right:10px;bottom:8px;background:var(--bar);padding:2px 8px;border-radius:12px}
pre#src{margin:0;padding:12px;white-space:pre;overflow:auto;flex:1;font:12px/1.5 ui-monospace,Menlo,Consolas,monospace;tab-size:2}
pre#src[hidden]{display:none}pre.doc{white-space:pre-wrap;word-break:break-word;font-size:13px}pre.doc.nowrap{white-space:pre}
main{flex:1;overflow:auto;padding:0}main[hidden]{display:none}
table{border-collapse:separate;border-spacing:0;min-width:100%;font-size:13px}
th,td{padding:6px 12px;border-bottom:1px solid var(--line);text-align:left;white-space:nowrap;max-width:60vw;overflow:hidden;text-overflow:ellipsis}
th{position:sticky;top:0;background:var(--bar);cursor:pointer;user-select:none}td.num{text-align:right;font-variant-numeric:tabular-nums}
tr:nth-child(even) td{background:color-mix(in srgb,var(--bar) 55%,transparent)}
.tree{padding:10px 12px;font:13px/1.55 ui-monospace,Menlo,Consolas,monospace}
.tree details{margin-left:14px}.tree summary{cursor:pointer;list-style:none}.tree summary::before{content:"▸ ";color:var(--muted)}
.tree details[open]>summary::before{content:"▾ "}.tree .row{margin-left:14px}
.k{color:var(--accent)}.s{color:#2e7d32}.n{color:#c2410c}.b{color:#7c3aed}.z{color:var(--muted)}
@media(prefers-color-scheme:dark){.s{color:#81c995}.n{color:#fdba74}.b{color:#c4b5fd}}
.empty{padding:24px;color:var(--muted)}.err{padding:12px;color:#b3261e}
#toast{position:fixed;left:50%;bottom:24px;transform:translateX(-50%);background:var(--fg);color:var(--bg);padding:8px 16px;border-radius:20px;opacity:0;transition:opacity .2s;pointer-events:none}
#toast.on{opacity:.95}
@media print{.bar,#toast{display:none}body{display:block}.stage{height:auto}}
"""

    private const val COMMON_JS = """
(function(){
  var toast=document.createElement('div');toast.id='toast';document.body.appendChild(toast);var t;
  window.say=function(m){toast.textContent=m;toast.className='on';clearTimeout(t);t=setTimeout(function(){toast.className='';},1400);};
  window.srcText=function(){var e=document.getElementById('src');return e?e.textContent:'';};
  window.copyAll=function(){
    var text=window.srcText();
    function fallback(){var a=document.createElement('textarea');a.value=text;a.style.position='fixed';a.style.opacity='0';document.body.appendChild(a);a.select();var ok=false;try{ok=document.execCommand('copy');}catch(e){}document.body.removeChild(a);window.say(ok?'Copied ✓':'Copy failed');}
    if(navigator.clipboard&&navigator.clipboard.writeText){navigator.clipboard.writeText(text).then(function(){window.say('Copied ✓');},fallback);}else{fallback();}
  };
  var c=document.getElementById('copy');if(c)c.onclick=window.copyAll;
})();
"""

    private const val SVG_JS = """
(function(){
  var stage=document.getElementById('stage'),pan=document.getElementById('pan'),src=document.getElementById('src'),
      zl=document.getElementById('zoom'),tab=document.getElementById('tab');
  var s=1,x=0,y=0,pts={},last=0,backs=['checker','light','dark'],bi=0;
  function draw(){pan.style.transform='translate('+x+'px,'+y+'px) scale('+s+')';zl.textContent=Math.round(s*100)+'%';}
  function zoomAt(f,cx,cy){var n=Math.min(40,Math.max(0.1,s*f));f=n/s;x=cx-(cx-x)*f;y=cy-(cy-y)*f;s=n;draw();}
  function center(){var r=stage.getBoundingClientRect(),p=pan.getBoundingClientRect();x=(r.width-p.width/s)/2;y=(r.height-p.height/s)/2;s=1;draw();}
  stage.addEventListener('pointerdown',function(e){stage.setPointerCapture(e.pointerId);pts[e.pointerId]={x:e.clientX,y:e.clientY};last=0;});
  stage.addEventListener('pointermove',function(e){
    var p=pts[e.pointerId];if(!p)return;var ids=Object.keys(pts);
    if(ids.length===1){x+=e.clientX-p.x;y+=e.clientY-p.y;p.x=e.clientX;p.y=e.clientY;draw();}
    else if(ids.length===2){p.x=e.clientX;p.y=e.clientY;var a=pts[ids[0]],b=pts[ids[1]],d=Math.hypot(a.x-b.x,a.y-b.y);
      if(last){var r=stage.getBoundingClientRect();zoomAt(d/last,(a.x+b.x)/2-r.left,(a.y+b.y)/2-r.top);}last=d;}
  });
  function up(e){delete pts[e.pointerId];last=0;}
  stage.addEventListener('pointerup',up);stage.addEventListener('pointercancel',up);
  stage.addEventListener('wheel',function(e){e.preventDefault();var r=stage.getBoundingClientRect();zoomAt(e.deltaY<0?1.15:1/1.15,e.clientX-r.left,e.clientY-r.top);},{passive:false});
  stage.addEventListener('dblclick',center);
  function mid(){var r=stage.getBoundingClientRect();return[r.width/2,r.height/2];}
  document.getElementById('zin').onclick=function(){var m=mid();zoomAt(1.25,m[0],m[1]);};
  document.getElementById('zout').onclick=function(){var m=mid();zoomAt(0.8,m[0],m[1]);};
  document.getElementById('fit').onclick=function(){x=0;y=0;s=1;draw();};
  document.getElementById('bg').onclick=function(){bi=(bi+1)%backs.length;stage.className='stage '+backs[bi];window.say('Backdrop: '+backs[bi]);};
  tab.onclick=function(){var code=src.hidden;src.hidden=!code;stage.style.display=code?'none':'flex';tab.textContent=code?'Preview':'Code';};
  draw();
})();
"""

    private const val CSV_JS = """
(function(){
  var raw=window.srcText(),view=document.getElementById('view'),src=document.getElementById('src'),
      stat=document.getElementById('stat'),tab=document.getElementById('tab'),filter=document.getElementById('filter');
  var first=raw.split(/\r?\n/,1)[0]||'',d=',';
  [['\t',0],[';',0],[',',0]].forEach(function(p){p[1]=first.split(p[0]).length;});
  var best=[[',',first.split(',').length],[';',first.split(';').length],['\t',first.split('\t').length]].sort(function(a,b){return b[1]-a[1];})[0];
  if(best[1]>1)d=best[0];
  function parse(t){var rows=[],row=[],f='',q=false,i=0;
    for(;i<t.length;i++){var c=t[i];
      if(q){if(c==='"'){if(t[i+1]==='"'){f+='"';i++;}else q=false;}else f+=c;}
      else if(c==='"')q=true;else if(c===d){row.push(f);f='';}
      else if(c==='\n'||c==='\r'){if(c==='\r'&&t[i+1]==='\n')i++;row.push(f);f='';rows.push(row);row=[];}
      else f+=c;}
    if(f!==''||row.length){row.push(f);rows.push(row);}return rows;}
  var rows=parse(raw),head=rows.shift()||[],sortCol=-1,asc=true,LIMIT=1000;
  function isNum(v){return v!==''&&!isNaN(Number(v));}
  function render(){
    var q=filter?filter.value.toLowerCase():'',list=rows.filter(function(r){return !q||r.join('\u0001').toLowerCase().indexOf(q)>=0;});
    if(sortCol>=0)list=list.slice().sort(function(a,b){var x=a[sortCol]||'',y=b[sortCol]||'',r=isNum(x)&&isNum(y)?Number(x)-Number(y):x.localeCompare(y);return asc?r:-r;});
    if(!head.length){view.innerHTML='<div class="empty">This file is empty.</div>';stat.textContent='';return;}
    var h='<table><thead><tr>'+head.map(function(c,i){return'<th data-i="'+i+'">'+esc(c)+(i===sortCol?(asc?' ▲':' ▼'):'')+'</th>';}).join('')+'</tr></thead><tbody>';
    list.slice(0,LIMIT).forEach(function(r){h+='<tr>'+head.map(function(_,i){var v=r[i]===undefined?'':r[i];return'<td'+(isNum(v)?' class="num"':'')+'>'+esc(v)+'</td>';}).join('')+'</tr>';});
    view.innerHTML=h+'</tbody></table>';
    stat.textContent=rows.length+' rows × '+head.length+' cols'+(list.length>LIMIT?' (first '+LIMIT+' shown)':(q?' ('+list.length+' match)':''));
    Array.prototype.forEach.call(view.querySelectorAll('th'),function(th){th.onclick=function(){var i=+th.getAttribute('data-i');if(sortCol===i)asc=!asc;else{sortCol=i;asc=true;}render();};});
  }
  function esc(s){return String(s).replace(/[&<>"]/g,function(c){return{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c];});}
  if(filter)filter.oninput=render;
  tab.onclick=function(){var code=src.hidden;src.hidden=!code;view.hidden=code;tab.textContent=code?'Table':'Source';};
  render();
})();
"""

    private const val JSON_JS = """
(function(){
  var raw=window.srcText(),view=document.getElementById('view'),src=document.getElementById('src'),
      stat=document.getElementById('stat'),tab=document.getElementById('tab'),LIMIT=300;
  function esc(s){return String(s).replace(/[&<>"]/g,function(c){return{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c];});}
  function leaf(v){
    if(v===null)return'<span class="z">null</span>';
    if(typeof v==='string')return'<span class="s">"'+esc(v.length>400?v.slice(0,400)+'…':v)+'"</span>';
    if(typeof v==='number')return'<span class="n">'+v+'</span>';
    return'<span class="b">'+v+'</span>';}
  function build(k,v,open){
    var label=k===null?'':'<span class="k">'+esc(k)+'</span>: ';
    if(v===null||typeof v!=='object'){var r=document.createElement('div');r.className='row';r.innerHTML=label+leaf(v);return r;}
    var arr=Array.isArray(v),keys=Object.keys(v),det=document.createElement('details'),sum=document.createElement('summary'),done=false;
    sum.innerHTML=label+'<span class="z">'+(arr?'[ '+keys.length+' ]':'{ '+keys.length+' }')+'</span>';det.appendChild(sum);
    function fill(){if(done)return;done=true;keys.slice(0,LIMIT).forEach(function(key){det.appendChild(build(arr?'['+key+']':key,v[key],false));});
      if(keys.length>LIMIT){var m=document.createElement('div');m.className='row z';m.textContent='… '+(keys.length-LIMIT)+' more';det.appendChild(m);}}
    det.addEventListener('toggle',function(){if(det.open)fill();});if(open){det.open=true;fill();}return det;}
  var data;
  try{data=JSON.parse(raw);}catch(e){view.innerHTML='<div class="err">Invalid JSON: '+esc(e.message)+'</div>';stat.textContent='';}
  if(data!==undefined){var wrap=document.createElement('div');wrap.className='tree';wrap.appendChild(build(null,data,true));view.appendChild(wrap);
    stat.textContent=Array.isArray(data)?data.length+' items':(data&&typeof data==='object'?Object.keys(data).length+' keys':typeof data);
    try{src.textContent=JSON.stringify(data,null,2);}catch(e){}}
  tab.onclick=function(){var code=src.hidden;src.hidden=!code;view.hidden=code;tab.textContent=code?'Tree':'Source';};
})();
"""

    private const val TEXT_JS = """
(function(){var p=document.getElementById('src'),b=document.getElementById('wrap');
  b.onclick=function(){var off=p.classList.toggle('nowrap');b.textContent=off?'Wrap':'No wrap';};})();
"""
}
