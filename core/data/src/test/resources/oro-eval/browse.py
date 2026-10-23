"""Headless-Chromium probe for Oro's live eval (Playwright).

  browse.py shot <url> <width> <height> <out.png|-> <full 0|1>
  browse.py audit <url>
  browse.py interact <url> <text>
  browse.py play <url>

Prints one JSON object. The metrics and audit scripts are the ones Oro's
in-app previewer runs (WebViewPagePreviewer), so the model sees the same facts.
"""
import json, sys, time
from playwright.sync_api import sync_playwright

SEL = "function shown(e){if(e.checkVisibility)return e.checkVisibility({opacityProperty:true,visibilityProperty:true});var n=e;while(n){var s=getComputedStyle(n);if(s.opacity==='0'||s.visibility==='hidden'||s.display==='none')return false;n=n.parentElement;}return true;}var cx=document.createElement('canvas').getContext('2d');function rgba(c){if(!c||!cx)return null;cx.clearRect(0,0,1,1);cx.fillStyle='rgba(0,0,0,0)';cx.fillStyle=c;cx.fillRect(0,0,1,1);var d=cx.getImageData(0,0,1,1).data;return [d[0],d[1],d[2],d[3]/255];}function L(c){var a=c.map(function(v){v=v/255;return v<=0.03928?v/12.92:Math.pow((v+0.055)/1.055,2.4);});return 0.2126*a[0]+0.7152*a[1]+0.0722*a[2];}function bgc(e){var layers=[];while(e){var cs=getComputedStyle(e);if(cs.backgroundImage&&cs.backgroundImage!=='none')return null;var c=rgba(cs.backgroundColor);if(c&&c[3]>0){layers.push(c);if(c[3]>=0.99)break;}e=e.parentElement;}var b=[255,255,255];for(var i=layers.length-1;i>=0;i--){var l=layers[i];b=[l[0]*l[3]+b[0]*(1-l[3]),l[1]*l[3]+b[1]*(1-l[3]),l[2]*l[3]+b[2]*(1-l[3])];}return b;}function inl(e){if(getComputedStyle(e).display!=='inline'||!e.parentElement)return false;var n=0;e.parentElement.childNodes.forEach(function(c){if(c.nodeType===3)n+=c.textContent.trim().length;});return n>2;}function tiny(r){return r.width>0&&(Math.min(r.width,r.height)<32||(r.width<40&&r.height<40));}function sel(e){if(!e||!e.tagName)return '';var s=e.tagName.toLowerCase();if(e.id)s+='#'+e.id;else if(e.className&&typeof e.className==='string'){var c=e.className.trim().split(/\\s+/).slice(0,2).join('.');if(c)s+='.'+c;}return s;}function lowc(){var res=[],checked=0;document.querySelectorAll('p,span,a,li,h1,h2,h3,h4,button,label,td').forEach(function(e){if(checked>200||!e.textContent.trim())return;var s=getComputedStyle(e);if(s.backgroundClip==='text'||s.getPropertyValue('-webkit-background-clip')==='text')return;var tf=rgba(s.getPropertyValue('-webkit-text-fill-color')||s.color);if(!tf||tf[3]<0.05||!shown(e))return;checked++;var g=bgc(e);if(!g)return;var t=[tf[0]*tf[3]+g[0]*(1-tf[3]),tf[1]*tf[3]+g[1]*(1-tf[3]),tf[2]*tf[3]+g[2]*(1-tf[3])],f=L(t),b=L(g);var r=(Math.max(f,b)+0.05)/(Math.min(f,b)+0.05),fs=parseFloat(s.fontSize),large=fs>=24||(fs>=18.66&&parseInt(s.fontWeight,10)>=700);if(r<(large?3:4.5))res.push([e,r]);});return res;}"
METRICS_JS = "(function(){" + SEL + """
var vw=document.documentElement.clientWidth,touch=vw<1024,de=document.documentElement,wide=[],out=new Set(),st=0,sta=[],sx=0,sxa=[],bi=0;
function clipped(e){for(var p=e.parentElement;p;p=p.parentElement){if(getComputedStyle(p).overflowX!=='visible')return true;}return false;}
function lab(e){var t=(e.textContent||e.getAttribute('aria-label')||'').trim().replace(/\\s+/g,' ').slice(0,18);return sel(e)+(t?' "'+t+'"':'');}
document.querySelectorAll('body *').forEach(function(e){var r=e.getBoundingClientRect();
if(r.right>vw+2&&r.width>0&&getComputedStyle(e).position!=='fixed'&&!clipped(e))out.add(e);
if(touch&&(e.tagName==='A'||e.tagName==='BUTTON')&&tiny(r)&&!inl(e)&&shown(e)){st++;if(sta.length<3)sta.push(lab(e)+' '+Math.round(r.width)+'x'+Math.round(r.height));}
if(e.childNodes.length&&e.childNodes[0].nodeType===3&&e.textContent.trim().length>2){var fs=parseFloat(getComputedStyle(e).fontSize);if(fs<12&&shown(e)){sx++;if(sxa.length<3)sxa.push(lab(e)+' '+fs+'px');}}});
out.forEach(function(e){if(wide.length<6&&!out.has(e.parentElement)){var r=e.getBoundingClientRect();wide.push(sel(e)+' ('+Math.round(r.width)+'px wide, right edge at '+Math.round(r.right)+'px)');}});
var lc=lowc(),lca=lc.slice(0,3).map(function(x){return lab(x[0])+' '+x[1].toFixed(1)+':1';});
document.querySelectorAll('img').forEach(function(i){if(i.complete&&i.naturalWidth===0)bi++;});
var hs=0;document.querySelectorAll('[hidden]').forEach(function(e){if(getComputedStyle(e).display!=='none')hs++;});
return {vw:vw,sw:de.scrollWidth,sh:de.scrollHeight,wide:wide,smallTargets:st,smallTargetsAt:sta,smallText:sx,smallTextAt:sxa,lowContrast:lc.length,lowContrastAt:lca,brokenImages:bi,hiddenShown:hs,title:document.title,
 textChars:(document.body?document.body.innerText.length:0),elements:document.getElementsByTagName('*').length};})()"""
AUDIT_JS = "(function(){" + SEL + """
var out=[];function add(c,m,e){if(out.length<60)out.push({cat:c,msg:m,sel:e?sel(e):''});}
if(!document.title)add('seo','Missing <title>');
if(!document.querySelector('meta[name=description]'))add('seo','Missing <meta name="description">');
if(!document.querySelector('meta[name=viewport]'))add('seo','Missing viewport meta tag (page will not scale on phones)');
if(!document.documentElement.lang)add('accessibility','Missing lang attribute on <html>');
var h1=document.querySelectorAll('h1').length;if(h1===0)add('seo','No <h1> heading');if(h1>1)add('seo',h1+' <h1> headings; use one');
var last=0;document.querySelectorAll('h1,h2,h3,h4,h5,h6').forEach(function(h){var l=+h.tagName[1];if(last&&l>last+1)add('accessibility','Heading level skips from h'+last+' to h'+l,h);last=l;});
document.querySelectorAll('img').forEach(function(i){if(!i.hasAttribute('alt'))add('accessibility','Image without alt text',i);if(i.naturalWidth>2*i.clientWidth&&i.naturalWidth>1200)add('performance','Image much larger than shown ('+i.naturalWidth+'px wide)',i);if(!i.loading)add('performance','Image without loading="lazy"',i);});
document.querySelectorAll('input,select,textarea').forEach(function(f){if(f.type==='hidden')return;var id=f.id;if(!(id&&document.querySelector('label[for="'+id+'"]'))&&!f.getAttribute('aria-label')&&!f.closest('label'))add('accessibility','Form field without a label',f);});
document.querySelectorAll('a,button').forEach(function(b){if(!b.textContent.trim()&&!b.getAttribute('aria-label')&&!b.querySelector('img[alt]'))add('accessibility','Link or button with no text',b);var r=b.getBoundingClientRect();if(innerWidth<1024&&tiny(r)&&!inl(b)&&shown(b))add('accessibility','Tap target too small for a finger ('+Math.round(r.width)+'x'+Math.round(r.height)+'px)',b);});
lowc().forEach(function(x){add('accessibility','Low contrast text ('+x[1].toFixed(1)+':1)',x[0]);});
document.querySelectorAll('head script[src]:not([defer]):not([async])').forEach(function(s){add('performance','Render-blocking script in <head>: '+s.getAttribute('src'));});
var n=document.getElementsByTagName('*').length;if(n>1500)add('performance','Large DOM ('+n+' elements)');
if(document.documentElement.scrollWidth>document.documentElement.clientWidth+1)add('mobile','Page scrolls sideways on a phone');
return out;})()"""
METRICS_JS_LEGACY = "(function(){" + SEL + """
var vw=document.documentElement.clientWidth,de=document.documentElement,wide=[],st=0,sx=0,bi=0;
document.querySelectorAll('body *').forEach(function(e){var r=e.getBoundingClientRect();
if(r.width>vw+2&&wide.length<8&&getComputedStyle(e).position!=='fixed')wide.push(sel(e)+' ('+Math.round(r.width)+'px)');
if((e.tagName==='A'||e.tagName==='BUTTON')&&r.width>0&&(r.width<40||r.height<40))st++;
if(e.childNodes.length&&e.childNodes[0].nodeType===3&&e.textContent.trim().length>2){var fs=parseFloat(getComputedStyle(e).fontSize);if(fs<12)sx++;}});
document.querySelectorAll('img').forEach(function(i){if(i.complete&&i.naturalWidth===0)bi++;});
return {vw:vw,sw:de.scrollWidth,sh:de.scrollHeight,wide:wide,smallTargets:st,smallText:sx,brokenImages:bi,title:document.title,
 textChars:(document.body?document.body.innerText.length:0),elements:document.getElementsByTagName('*').length};})()"""
AUDIT_JS_LEGACY = "(function(){" + SEL + """
var out=[];function add(c,m,e){if(out.length<60)out.push({cat:c,msg:m,sel:e?sel(e):''});}
function lum(c){var m=c.match(/\\d+(\\.\\d+)?/g);if(!m)return null;var a=m.slice(0,3).map(function(v){v=v/255;return v<=0.03928?v/12.92:Math.pow((v+0.055)/1.055,2.4);});return 0.2126*a[0]+0.7152*a[1]+0.0722*a[2];}
function bg(e){while(e){var cs=getComputedStyle(e);var b=cs.backgroundColor;if(b&&b!=='transparent'&&!/rgba\\(.*,\\s*0\\)/.test(b))return b;e=e.parentElement;}return 'rgb(255,255,255)';}
if(!document.title)add('seo','Missing <title>');
if(!document.querySelector('meta[name=description]'))add('seo','Missing <meta name="description">');
if(!document.querySelector('meta[name=viewport]'))add('seo','Missing viewport meta tag (page will not scale on phones)');
if(!document.documentElement.lang)add('accessibility','Missing lang attribute on <html>');
var h1=document.querySelectorAll('h1').length;if(h1===0)add('seo','No <h1> heading');if(h1>1)add('seo',h1+' <h1> headings; use one');
var last=0;document.querySelectorAll('h1,h2,h3,h4,h5,h6').forEach(function(h){var l=+h.tagName[1];if(last&&l>last+1)add('accessibility','Heading level skips from h'+last+' to h'+l,h);last=l;});
document.querySelectorAll('img').forEach(function(i){if(!i.hasAttribute('alt'))add('accessibility','Image without alt text',i);if(i.naturalWidth>2*i.clientWidth&&i.naturalWidth>1200)add('performance','Image much larger than shown ('+i.naturalWidth+'px wide)',i);if(!i.loading)add('performance','Image without loading="lazy"',i);});
document.querySelectorAll('input,select,textarea').forEach(function(f){if(f.type==='hidden')return;var id=f.id;if(!(id&&document.querySelector('label[for="'+id+'"]'))&&!f.getAttribute('aria-label')&&!f.closest('label'))add('accessibility','Form field without a label',f);});
document.querySelectorAll('a,button').forEach(function(b){if(!b.textContent.trim()&&!b.getAttribute('aria-label')&&!b.querySelector('img[alt]'))add('accessibility','Link or button with no text',b);var r=b.getBoundingClientRect();if(r.width>0&&(r.width<40||r.height<40))add('accessibility','Tap target smaller than 40px',b);});
var checked=0;document.querySelectorAll('p,span,a,li,h1,h2,h3,h4,button,label,td').forEach(function(e){if(checked>200||!e.textContent.trim())return;checked++;var s=getComputedStyle(e),f=lum(s.color),b=lum(bg(e));if(f===null||b===null)return;var r=(Math.max(f,b)+0.05)/(Math.min(f,b)+0.05),large=parseFloat(s.fontSize)>=24;if(r<(large?3:4.5))add('accessibility','Low contrast text ('+r.toFixed(1)+':1)',e);});
document.querySelectorAll('head script[src]:not([defer]):not([async])').forEach(function(s){add('performance','Render-blocking script in <head>: '+s.getAttribute('src'));});
var n=document.getElementsByTagName('*').length;if(n>1500)add('performance','Large DOM ('+n+' elements)');
if(document.documentElement.scrollWidth>document.documentElement.clientWidth+1)add('mobile','Page scrolls sideways on a phone');
return out;})()"""
import os
if os.environ.get("ORO_EVAL_AUDIT") == "legacy":
    METRICS_JS, AUDIT_JS = METRICS_JS_LEGACY, AUDIT_JS_LEGACY


def open_page(p, url, width, height, mobile):
    browser = p.chromium.launch(args=["--no-sandbox", "--disable-dev-shm-usage", "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--ignore-gpu-blocklist"])
    ctx = browser.new_context(viewport={"width": width, "height": height}, device_scale_factor=2 if mobile else 1,
                              is_mobile=mobile, has_touch=mobile)
    page = ctx.new_page()
    log = {"console": [], "pageErrors": [], "failed": [], "api": []}
    page.on("console", lambda m: log["console"].append(m.text[:300]) if m.type == "error" else None)
    page.on("pageerror", lambda e: log["pageErrors"].append(str(e)[:300]))
    page.on("requestfailed", lambda r: log["failed"].append(r.url[:200]))
    def on_response(r):
        if "/api/" in r.url:
            log["api"].append({"m": r.request.method, "u": r.url.split("/api/", 1)[1][:80], "s": r.status})
        elif r.status >= 400:
            log["failed"].append(f"{r.status} {r.url[:200]}")
    page.on("response", on_response)
    try:
        page.goto(url, wait_until="load", timeout=20000)
    except Exception as e:
        log["pageErrors"].append(f"load: {str(e)[:200]}")
    try:
        page.wait_for_load_state("networkidle", timeout=6000)
    except Exception:
        pass
    page.wait_for_timeout(900)
    return browser, page, log


def shot(url, w, h, out, full):
    mobile = w < 700
    with sync_playwright() as p:
        browser, page, log = open_page(p, url, w, h, mobile)
        try:
            m = page.evaluate(METRICS_JS)
        except Exception as e:
            m = {"error": str(e)[:200]}
        if out != "-":
            kind = {"type": "jpeg", "quality": 72} if out.endswith(".jpg") else {}
            if full:
                height = min(int(m.get("sh") or h), 3600 if mobile else 3000)
                page.set_viewport_size({"width": w, "height": max(h, height)})
                page.wait_for_timeout(400)
                page.screenshot(path=out, clip={"x": 0, "y": 0, "width": w, "height": max(h, height)}, **kind)
            else:
                page.screenshot(path=out, **kind)
        browser.close()
        print(json.dumps({"metrics": m, **log}))


def audit(url):
    with sync_playwright() as p:
        browser, page, log = open_page(p, url, 390, 844, True)
        try:
            issues = page.evaluate(AUDIT_JS)
        except Exception as e:
            issues = [{"cat": "error", "msg": "audit failed: " + str(e)[:200], "sel": ""}]
        browser.close()
        if os.environ.get("ORO_EVAL_AUDIT") != "legacy":
            # As the app: a desktop pass adds accessibility issues the phone pass did not see.
            browser, page, _ = open_page(p, url, 1280, 800, False)
            try:
                seen = {(i["msg"], i["sel"]) for i in issues}
                for i in page.evaluate(AUDIT_JS):
                    if i.get("cat") == "accessibility" and (i["msg"], i["sel"]) not in seen:
                        issues.append({**i, "msg": i["msg"] + " (desktop)"})
            except Exception:
                pass
            browser.close()
        print(json.dumps({"issues": issues[:60], **log}))


def interact(url, text):
    """Add one item through the UI the way a person would, then reload: does it show, does it persist?"""
    with sync_playwright() as p:
        browser, page, log = open_page(p, url, 390, 844, True)
        steps = []
        ADD = ("add", "new", "create", "+")
        SUBMIT = ("add", "save", "create", "submit", "book", "done", "confirm")
        def visible(sel):
            out = []
            for e in page.query_selector_all(sel):
                try:
                    if e.is_visible(): out.append(e)
                except Exception:
                    pass
            return out
        def label(b):
            try:
                return ((b.inner_text() or "") + " " + (b.get_attribute("aria-label") or "") + " " + (b.get_attribute("value") or "")).strip().lower()
            except Exception:
                return ""
        def inputs():
            return visible("input:not([type=hidden]):not([type=checkbox]):not([type=radio]):not([type=submit]):not([type=button]):not([type=search]), textarea")
        opened = None
        if not inputs():
            for b in visible("button, a[role=button], [role=button]"):
                t = label(b)
                if any(k in t for k in ADD):
                    try:
                        b.click(); opened = t; steps.append("opened: " + t[:40]); page.wait_for_timeout(800)
                    except Exception:
                        pass
                    break
        ins = inputs()
        if ins:
            first = ins[0]
            first.fill(text); steps.append("filled")
            for extra in ins[1:5]:
                try:
                    if not extra.input_value():
                        typ = (extra.get_attribute("type") or "text").lower()
                        extra.fill({"number": "3", "email": "eval@example.com", "date": "2026-10-23", "time": "09:30",
                                    "tel": "9800000000", "url": "https://example.com", "password": "Eval12345!"}.get(typ, "Eval"))
                except Exception:
                    pass
            for sel_el in visible("select"):
                try:
                    if len(sel_el.query_selector_all("option")) > 1 and not sel_el.input_value(): sel_el.select_option(index=1)
                except Exception:
                    pass
            submitted = False
            try:
                has_form = first.evaluate("e => !!e.form")
                if has_form:
                    first.evaluate("e => e.form.requestSubmit ? e.form.requestSubmit() : e.form.submit()")
                    submitted = True; steps.append("form.requestSubmit")
            except Exception as e:
                steps.append("submit error: " + str(e)[:80])
            if not submitted:
                for b in visible("button, input[type=submit], [role=button]"):
                    t = label(b)
                    if t and t != opened and any(k in t for k in SUBMIT):
                        b.click(); submitted = True; steps.append("clicked: " + t[:40]); break
            if not submitted:
                first.press("Enter"); steps.append("pressed Enter")
            page.wait_for_timeout(1800)
        body = page.inner_text("body") if page.query_selector("body") else ""
        shown = text in body
        writes = [a for a in log["api"] if a["m"] in ("POST", "PUT", "PATCH")]
        page.reload(wait_until="load")
        try:
            page.wait_for_load_state("networkidle", timeout=6000)
        except Exception:
            pass
        page.wait_for_timeout(1500)
        body2 = page.inner_text("body") if page.query_selector("body") else ""
        browser.close()
        print(json.dumps({"tried": bool(ins), "steps": steps, "shownAfterSubmit": shown, "shownAfterReload": text in body2,
                          "writes": writes, **log}))


def play(url):
    """Poke an interactive page for a few seconds; report errors and whether anything moved."""
    with sync_playwright() as p:
        browser, page, log = open_page(p, url, 390, 844, True)
        before = page.screenshot()
        for b in page.query_selector_all("button"):
            try:
                t = (b.inner_text() or "").strip().lower()
                if b.is_visible() and any(k in t for k in ("start", "play", "begin", "tap")):
                    b.click(); break
            except Exception:
                pass
        page.mouse.click(195, 500)
        for k in ["ArrowUp", "ArrowRight", "ArrowDown", "ArrowLeft", "Space"]:
            page.keyboard.press(k); page.wait_for_timeout(350)
        page.wait_for_timeout(1500)
        after = page.screenshot()
        canvas = page.query_selector("canvas") is not None
        browser.close()
        print(json.dumps({"changed": before != after, "canvas": canvas, **log}))


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "shot":
        shot(sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), sys.argv[5], sys.argv[6] == "1")
    elif cmd == "audit":
        audit(sys.argv[2])
    elif cmd == "interact":
        interact(sys.argv[2], sys.argv[3])
    elif cmd == "play":
        play(sys.argv[2])
