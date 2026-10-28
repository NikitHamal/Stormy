package com.oro.app.browser

/**
 * The JavaScript the engine runs inside pages: the document-start runtime
 * (`window.__oro`), the snapshot that becomes a [PageState], the action
 * primitives, and the readability-style extraction.
 *
 * Written for the WebView's Chromium with no dependencies and no ES template
 * literals (the strings pass through Kotlin), defensive about the hostile
 * cases real pages bring: shadow roots we cannot see, frameworks that need
 * the native value setter (React), synthetic-event-only handlers, giant DOMs
 * (element and text caps), and human-check widgets that must be detected but
 * never touched.
 *
 * Every entry point returns a small JSON object `{ok, ...}` so Kotlin never
 * parses prose.
 */
object BrowserScripts {

    /** Installed at document start in the main frame (and same-origin frames). */
    val RUNTIME_JS: String = """
(function(){
  if (window.__oro) return;
  var O = {};
  window.__oro = O;
  O.oidSeq = 0;
  O.docs = [document];

  O.byId = function(oid){
    for (var d = 0; d < O.docs.length; d++) {
      try {
        var el = O.docs[d].querySelector('[data-oid="' + oid + '"]');
        if (el) return el;
      } catch (e) {}
    }
    return null;
  };

  O.shown = function(el){
    if (!el || !el.getBoundingClientRect) return false;
    if (el.checkVisibility) { try { if (!el.checkVisibility({opacityProperty:true, visibilityProperty:true})) return false; } catch(e){} }
    var r = el.getBoundingClientRect();
    if (r.width <= 0 || r.height <= 0) return false;
    var s = window.getComputedStyle(el);
    return s.visibility !== 'hidden' && s.display !== 'none';
  };

  O.center = function(el){
    var r = el.getBoundingClientRect();
    return { x: r.left + r.width / 2, y: r.top + r.height / 2, r: r };
  };

  O.txt = function(s, n){ s = (s || '').replace(/\s+/g, ' ').trim(); return s.length > n ? s.slice(0, n - 1) + '…' : s; };

  // ------------------------------------------------------------- actions

  O.click = function(oid){
    var el = O.byId(oid);
    if (!el) return JSON.stringify({ok:false, why:'gone'});
    try { el.scrollIntoView({block:'center', inline:'center'}); } catch(e){}
    var c = O.center(el);
    var opts = {bubbles:true, cancelable:true, view:window, clientX:c.x, clientY:c.y, button:0};
    var top = null;
    try { top = document.elementFromPoint(c.x, c.y); } catch(e){}
    var target = top || el;
    try {
      target.dispatchEvent(new PointerEvent('pointerdown', opts));
      target.dispatchEvent(new MouseEvent('mousedown', opts));
      target.dispatchEvent(new PointerEvent('pointerup', opts));
      target.dispatchEvent(new MouseEvent('mouseup', opts));
    } catch(e){}
    var activated = false;
    try { if (typeof el.click === 'function') { el.click(); activated = true; } } catch(e){}
    if (!activated) { try { el.dispatchEvent(new MouseEvent('click', opts)); } catch(e2){} }
    return JSON.stringify({ok:true, tag:el.tagName.toLowerCase(), label:O.txt(el.textContent || el.getAttribute('aria-label') || '', 40)});
  };

  O.nativeSet = function(el, value){
    var proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
    var d = Object.getOwnPropertyDescriptor(proto, 'value');
    if (d && d.set) d.set.call(el, value); else el.value = value;
    el.dispatchEvent(new Event('input', {bubbles:true}));
    el.dispatchEvent(new Event('change', {bubbles:true}));
  };

  O.type = function(oid, text, append){
    var el = oid ? O.byId(oid) : (document.activeElement && document.activeElement !== document.body ? document.activeElement : null);
    if (!el) return JSON.stringify({ok:false, why:'gone'});
    try { el.scrollIntoView({block:'center', inline:'center'}); } catch(e){}
    try { el.focus({preventScroll:true}); } catch(e){}
    var pw = (el.type === 'password');
    if (el.isContentEditable) {
      if (!append) { try { document.execCommand('selectAll', false, null); } catch(e){} }
      try { document.execCommand('insertText', false, text); } catch(e){}
      el.dispatchEvent(new InputEvent('input', {bubbles:true, data:text, inputType:'insertText'}));
    } else if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') {
      O.nativeSet(el, append ? (el.value + text) : text);
    } else {
      // A rich editor on a div (ProseMirror, Slate): insertText after focus.
      try { document.execCommand('insertText', false, text); } catch(e){}
    }
    var now = pw ? '(hidden)' : O.txt(el.value !== undefined && el.value !== null ? String(el.value) : el.textContent, 60);
    return JSON.stringify({ok:true, tag:el.tagName.toLowerCase(), value:now});
  };

  O.select = function(oid, wanted){
    var el = O.byId(oid);
    if (!el) return JSON.stringify({ok:false, why:'gone'});
    if (el.tagName !== 'SELECT') return JSON.stringify({ok:false, why:'not a <select>; open it with click and pick the option element'});
    var w = String(wanted || '').trim().toLowerCase();
    var pick = null;
    for (var i = 0; i < el.options.length; i++) {
      var o = el.options[i];
      var t = (o.textContent || '').replace(/\s+/g,' ').trim().toLowerCase();
      var v = (o.value || '').toLowerCase();
      if (t === w || v === w) { pick = o; break; }
      if (!pick && (t.indexOf(w) >= 0 || w.indexOf(t) >= 0) && t.length > 0) pick = o;
    }
    if (!pick) {
      var names = [];
      for (var j = 0; j < Math.min(el.options.length, 12); j++) names.push((el.options[j].textContent||'').trim());
      return JSON.stringify({ok:false, why:'no option like that', options:names});
    }
    el.value = pick.value;
    el.dispatchEvent(new Event('input', {bubbles:true}));
    el.dispatchEvent(new Event('change', {bubbles:true}));
    return JSON.stringify({ok:true, chose:(pick.textContent||'').trim()});
  };

  O.check = function(oid, want){
    var el = O.byId(oid);
    if (!el) return JSON.stringify({ok:false, why:'gone'});
    var role = (el.getAttribute('role') || '').toLowerCase();
    var isBox = el.type === 'checkbox' || el.type === 'radio' || role === 'checkbox' || role === 'radio' || role === 'switch';
    if (!isBox) return JSON.stringify({ok:false, why:'not a checkbox/radio/switch'});
    var now = (el.type === 'checkbox' || el.type === 'radio') ? !!el.checked : (el.getAttribute('aria-checked') === 'true');
    if (now === want) return JSON.stringify({ok:true, already:true, checked:now});
    if (el.type === 'radio' && !want) return JSON.stringify({ok:false, why:'a radio cannot be unchecked; pick another option'});
    try { el.click(); } catch(e){ return JSON.stringify({ok:false, why:'click failed'}); }
    var after = (el.type === 'checkbox' || el.type === 'radio') ? !!el.checked : (el.getAttribute('aria-checked') === 'true');
    return JSON.stringify({ok:after === want, checked:after});
  };

  O.key = function(key, oid){
    var el = oid ? O.byId(oid) : (document.activeElement || document.body);
    var k = String(key || '').toLowerCase();
    var map = {
      enter:{key:'Enter',code:'Enter',keyCode:13}, tab:{key:'Tab',code:'Tab',keyCode:9},
      escape:{key:'Escape',code:'Escape',keyCode:27}, esc:{key:'Escape',code:'Escape',keyCode:27},
      backspace:{key:'Backspace',code:'Backspace',keyCode:8}, 'delete':{key:'Delete',code:'Delete',keyCode:46},
      up:{key:'ArrowUp',code:'ArrowUp',keyCode:38}, down:{key:'ArrowDown',code:'ArrowDown',keyCode:40},
      left:{key:'ArrowLeft',code:'ArrowLeft',keyCode:37}, right:{key:'ArrowRight',code:'ArrowRight',keyCode:39},
      home:{key:'Home',code:'Home',keyCode:36}, end:{key:'End',code:'End',keyCode:35},
      pageup:{key:'PageUp',code:'PageUp',keyCode:33}, pagedown:{key:'PageDown',code:'PageDown',keyCode:34}
    };
    var m = map[k];
    if (!m && k.length === 1) m = {key:k, code:'Key' + k.toUpperCase(), keyCode:k.toUpperCase().charCodeAt(0)};
    if (!m) return JSON.stringify({ok:false, why:'unknown key: ' + k});
    var init = {bubbles:true, cancelable:true, view:window, key:m.key, code:m.code, keyCode:m.keyCode, which:m.keyCode};
    el.dispatchEvent(new KeyboardEvent('keydown', init));
    el.dispatchEvent(new KeyboardEvent('keypress', init));
    el.dispatchEvent(new KeyboardEvent('keyup', init));
    if (m.key === 'Enter') {
      var form = el.closest ? el.closest('form') : null;
      try {
        if (form && form.requestSubmit) form.requestSubmit();
        else if (form) form.dispatchEvent(new Event('submit', {bubbles:true, cancelable:true}));
      } catch(e){}
    }
    return JSON.stringify({ok:true, key:m.key});
  };

  O.hover = function(oid){
    var el = O.byId(oid);
    if (!el) return JSON.stringify({ok:false, why:'gone'});
    try { el.scrollIntoView({block:'center'}); } catch(e){}
    var c = O.center(el);
    var o = {bubbles:true, cancelable:true, view:window, clientX:c.x, clientY:c.y};
    try {
      el.dispatchEvent(new PointerEvent('pointerover', o));
      el.dispatchEvent(new PointerEvent('pointerenter', Object.assign({}, o, {bubbles:false})));
      el.dispatchEvent(new MouseEvent('mouseover', o));
      el.dispatchEvent(new MouseEvent('mouseenter', Object.assign({}, o, {bubbles:false})));
      el.dispatchEvent(new MouseEvent('mousemove', o));
    } catch(e){}
    return JSON.stringify({ok:true});
  };

  O.scroll = function(dir, oid, amount){
    var a = Math.max(0.1, Math.min(1.5, amount || 0.7));
    var el = oid ? O.byId(oid) : null;
    var dx = 0, dy = 0;
    var vh = el ? el.clientHeight : window.innerHeight;
    var vw = el ? el.clientWidth : window.innerWidth;
    if (dir === 'down') dy = vh * a; else if (dir === 'up') dy = -vh * a;
    else if (dir === 'right') dx = vw * a; else if (dir === 'left') dx = -vw * a;
    if (el) el.scrollBy({left:dx, top:dy}); else window.scrollBy({left:dx, top:dy});
    return JSON.stringify({ok:true, scrollY:Math.round(window.scrollY), pageHeight:Math.max(document.documentElement.scrollHeight, document.body ? document.body.scrollHeight : 0)});
  };

  O.scrollToText = function(text){
    var want = String(text || '').trim().toLowerCase();
    if (!want) return JSON.stringify({ok:false, why:'empty text'});
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
    var node, found = null, count = 0;
    while ((node = walker.nextNode())) {
      var v = node.nodeValue;
      if (!v) continue;
      if (v.toLowerCase().indexOf(want) >= 0) {
        count++;
        var p = node.parentElement;
        if (p && O.shown(p) && !found) found = p;
      }
    }
    if (!found) return JSON.stringify({ok:false, why:'not found', count:0});
    try { found.scrollIntoView({block:'center'}); } catch(e){}
    return JSON.stringify({ok:true, count:count, tag:found.tagName.toLowerCase(), text:O.txt(found.textContent, 60)});
  };

  O.textVisible = function(text){
    var t = String(text || '');
    var body = document.body;
    return JSON.stringify({ok:true, found: !!body && (body.textContent || '').indexOf(t) >= 0});
  };

  O.hash = function(){
    var n = document.getElementsByTagName('*').length;
    var t = document.body ? (document.body.textContent || '').length : 0;
    var y = Math.round(window.scrollY / 24);
    return JSON.stringify({ok:true, h:(n * 31 + t * 7 + y * 3 + location.href.length) | 0});
  };

  O.evalIn = function(js){
    try {
      var r = window.eval(js);
      var s = (r === undefined) ? 'undefined' : (typeof r === 'object' ? JSON.stringify(r) : String(r));
      return JSON.stringify({ok:true, result:s.slice(0, 8000)});
    } catch (e) {
      return JSON.stringify({ok:false, why:String(e && e.message || e).slice(0, 300)});
    }
  };

  // ---------------------------------------------------------- extraction

  O.skipNode = function(n){
    var tag = n.tagName ? n.tagName.toLowerCase() : '';
    return tag === 'script' || tag === 'style' || tag === 'noscript' || tag === 'svg' || tag === 'template' ||
      (n.getAttribute && (n.getAttribute('aria-hidden') === 'true'));
  };

  O.markdown = function(root, max){
    var out = [];
    var used = 0;
    function esc(s){ return (s || '').replace(/\s+/g, ' ').trim(); }
    function push(s){ if (used < max) { out.push(s); used += s.length; } }
    function inline(n){
      var s = '';
      for (var i = 0; i < n.childNodes.length && s.length < 4000; i++) {
        var c = n.childNodes[i];
        if (c.nodeType === 3) s += c.nodeValue;
        else if (c.nodeType === 1 && !O.skipNode(c)) {
          var tag = c.tagName.toLowerCase();
          if (tag === 'a' && c.href) { var t = esc(c.textContent); s += '[' + (t || c.href) + '](' + c.href + ')'; }
          else if (tag === 'img') { var a = esc(c.getAttribute('alt')); if (a) s += '[img: ' + a + ']'; }
          else if (tag === 'br') s += '\n';
          else s += inline(c);
        }
      }
      return s;
    }
    function walk(n, listDepth){
      if (used >= max) return;
      for (var i = 0; i < n.childNodes.length && used < max; i++) {
        var c = n.childNodes[i];
        if (c.nodeType === 3) { var t = esc(c.nodeValue); if (t) push(t); continue; }
        if (c.nodeType !== 1 || O.skipNode(c)) continue;
        var tag = c.tagName.toLowerCase();
        if (/^h[1-6]$/.test(tag)) { push('\n' + '######'.slice(0, +tag[1]) + ' ' + esc(c.textContent) + '\n'); }
        else if (tag === 'p') { push(inline(c) + '\n'); }
        else if (tag === 'li') { push('  '.repeat(listDepth) + '- ' + inline(c)); }
        else if (tag === 'ul' || tag === 'ol') { walk(c, listDepth + 1); }
        else if (tag === 'pre') { push('```\n' + (c.textContent || '').slice(0, 2000) + '\n```'); }
        else if (tag === 'blockquote') { push('> ' + esc(c.textContent)); }
        else if (tag === 'table') {
          var rows = c.querySelectorAll('tr');
          for (var r = 0; r < Math.min(rows.length, 60) && used < max; r++) {
            var cells = rows[r].querySelectorAll('th,td');
            var line = [];
            for (var k = 0; k < cells.length; k++) line.push(esc(cells[k].textContent).slice(0, 80));
            push('| ' + line.join(' | ') + ' |');
          }
        }
        else if (tag === 'br') push('');
        else if (tag === 'a') { var at = esc(c.textContent); if (at && c.href) push('[' + at + '](' + c.href + ')'); else walk(c, listDepth); }
        else if (tag === 'button' || tag === 'input' || tag === 'select' || tag === 'textarea') { /* the element list covers controls */ }
        else walk(c, listDepth);
      }
    }
    walk(root, 0);
    return out.join('\n').replace(/\n{3,}/g, '\n\n').trim().slice(0, max);
  };

  O.mainRoot = function(sel){
    if (sel) { try { var q = document.querySelector(sel); if (q) return q; } catch(e){} }
    return document.querySelector('main') || document.querySelector('article') ||
      document.querySelector('[role=main]') || document.body;
  };

  O.extract = function(sel, fieldsJson, limit){
    var fields = [];
    try { fields = JSON.parse(fieldsJson); } catch(e) { return JSON.stringify({ok:false, why:'fields is not JSON'}); }
    var rows;
    try { rows = document.querySelectorAll(sel || 'table tr'); } catch(e) { return JSON.stringify({ok:false, why:'bad selector'}); }
    var out = [];
    for (var i = 0; i < rows.length && out.length < limit; i++) {
      var r = rows[i], o = {};
      for (var f = 0; f < fields.length; f++) {
        var fd = fields[f] || {};
        var node = null;
        try { node = fd.selector ? r.querySelector(fd.selector) : r; } catch(e) { node = null; }
        if (!node) { o[fd.name || ('f' + f)] = null; continue; }
        o[fd.name || ('f' + f)] = fd.attr ? String(node.getAttribute(fd.attr) || '') : O.txt(node.textContent, 300);
      }
      out.push(o);
    }
    return JSON.stringify({ok:true, count: rows.length, rows: out});
  };

  O.read = function(mode, sel, max){
    if (mode === 'html') {
      var src = null;
      try { src = sel ? document.querySelector(sel) : document.documentElement; } catch(e) { src = null; }
      if (!src) return JSON.stringify({ok:true, text:''});
      return JSON.stringify({ok:true, text:String(src.outerHTML || '').slice(0, max)});
    }
    var root = O.mainRoot(sel);
    if (mode === 'links') {
      var seen = {}, lines = [], as = document.querySelectorAll('a[href]');
      for (var i = 0; i < as.length && lines.length < 120; i++) {
        var a = as[i];
        var t = O.txt(a.textContent, 60) || O.txt(a.getAttribute('aria-label') || '', 60);
        if (!t || seen[a.href]) continue;
        seen[a.href] = 1;
        lines.push('- ' + t + ' -> ' + a.href);
      }
      return JSON.stringify({ok:true, text:lines.join('\n').slice(0, max)});
    }
    if (mode === 'text') {
      var s = ((root.textContent || '').replace(/[ \t]+/g, ' ').replace(/\n{3,}/g, '\n\n')).trim().slice(0, max);
      return JSON.stringify({ok:true, text:s});
    }
    return JSON.stringify({ok:true, text:O.markdown(root, max)});
  };

  // ------------------------------------------------------------ snapshot

  O.detectCheck = function(doc){
    try {
      var frames = doc.querySelectorAll('iframe');
      for (var i = 0; i < frames.length; i++) {
        var src = (frames[i].src || '').toLowerCase();
        if (src.indexOf('recaptcha') >= 0 || src.indexOf('/recaptcha/') >= 0) return 'recaptcha';
        if (src.indexOf('hcaptcha') >= 0) return 'hcaptcha';
        if (src.indexOf('challenges.cloudflare.com') >= 0 || src.indexOf('turnstile') >= 0) return 'turnstile';
        if (src.indexOf('arkoselabs') >= 0 || src.indexOf('funcaptcha') >= 0) return 'arkose';
        if (src.indexOf('geetest') >= 0) return 'text';
      }
      if (doc.querySelector('.g-recaptcha, [data-recaptcha], .grecaptcha-badge')) return 'recaptcha';
      if (doc.querySelector('.h-captcha, [data-hcaptcha]')) return 'hcaptcha';
      if (doc.querySelector('.cf-turnstile, [data-sitekey][class*=turnstile]')) return 'turnstile';
      var head = doc.body ? (doc.body.textContent || '').slice(0, 3000).toLowerCase() : '';
      if (head.indexOf("i'm not a robot") >= 0 || head.indexOf('verify you are human') >= 0 ||
          head.indexOf('confirm you are not a robot') >= 0 || head.indexOf('press and hold') >= 0 ||
          head.indexOf('are you a robot') >= 0) return 'text';
      // A solved reCAPTCHA often leaves a checked state or replaces the widget;
      // an invisible (score-based) reCAPTCHA has only the badge: not a check the
      // user passes, so ignore the badge alone.
    } catch (e) {}
    return null;
  };

  O.roleOf = function(el){
    var tag = el.tagName.toLowerCase();
    var role = (el.getAttribute('role') || '').toLowerCase();
    if (role) {
      if (role === 'button') return 'button';
      if (role === 'link') return 'link';
      if (role === 'checkbox') return 'checkbox';
      if (role === 'radio') return 'radio';
      if (role === 'switch') return 'switch';
      if (role === 'combobox' || role === 'listbox') return 'combobox';
      if (role === 'textbox' || role === 'searchbox') return 'textbox';
      if (role === 'tab') return 'tab';
      if (role === 'menuitem' || role === 'menuitemcheckbox' || role === 'menuitemradio') return 'menuitem';
      if (role === 'slider') return 'slider';
      if (role === 'option') return 'option';
      if (role === 'spinbutton') return 'textbox';
      return role.slice(0, 20);
    }
    if (tag === 'a') return el.hasAttribute('href') ? 'link' : 'link';
    if (tag === 'button') return 'button';
    if (tag === 'select') return 'combobox';
    if (tag === 'textarea') return 'textbox';
    if (tag === 'summary') return 'disclosure';
    if (tag === 'label') return 'label';
    if (tag === 'input') {
      var t = (el.type || 'text').toLowerCase();
      if (t === 'checkbox') return 'checkbox';
      if (t === 'radio') return 'radio';
      if (t === 'submit' || t === 'button' || t === 'reset' || t === 'image') return 'button';
      if (t === 'file') return 'file';
      if (t === 'range') return 'slider';
      if (t === 'password') return 'textbox';
      return 'textbox';
    }
    if (el.isContentEditable) return 'textbox';
    return 'area';
  };

  O.labelOf = function(el, role){
    var l = el.getAttribute('aria-label');
    if (l && l.trim()) return O.txt(l, 60);
    var labelledby = el.getAttribute('aria-labelledby');
    if (labelledby) {
      var parts = [];
      labelledby.split(/\s+/).forEach(function(id){ var n = document.getElementById(id); if (n) parts.push(n.textContent); });
      if (parts.length) return O.txt(parts.join(' '), 60);
    }
    if (el.id) {
      try { var lab = document.querySelector('label[for="' + (window.CSS && CSS.escape ? CSS.escape(el.id) : el.id) + '"]'); if (lab && lab.textContent.trim()) return O.txt(lab.textContent, 60); } catch(e){}
    }
    var wrap = el.closest ? el.closest('label') : null;
    if (wrap && wrap.textContent.trim()) return O.txt(wrap.textContent, 60);
    var tag = el.tagName.toLowerCase();
    if (tag === 'img' || tag === 'input') {
      var alt = el.getAttribute('alt'); if (alt && alt.trim()) return O.txt(alt, 60);
    }
    if (tag === 'input' || tag === 'textarea') {
      var ph = el.getAttribute('placeholder'); if (ph && ph.trim()) return O.txt(ph, 60);
      var nm = el.getAttribute('name'); if (nm) return O.txt(nm.replace(/[_-]+/g, ' '), 60);
      if (el.id) return O.txt(el.id.replace(/[_-]+/g, ' '), 60);
    }
    if (tag === 'select') {
      var opt = el.options && el.options[el.selectedIndex];
      return '';
    }
    var inner = el.textContent;
    if (inner && inner.trim()) return O.txt(inner, 60);
    var title = el.getAttribute('title'); if (title) return O.txt(title, 60);
    return '';
  };

  O.selectorOf = function(el){
    try {
      if (el.id) return '#' + el.id;
      var parts = [];
      var n = el;
      for (var depth = 0; n && n.nodeType === 1 && depth < 3; depth++) {
        var part = n.tagName.toLowerCase();
        if (n.classList && n.classList.length) part += '.' + Array.prototype.slice.call(n.classList, 0, 2).join('.');
        if (n.parentElement) {
          var same = Array.prototype.filter.call(n.parentElement.children, function(c){ return c.tagName === n.tagName; });
          if (same.length > 1) part += ':nth-of-type(' + (same.indexOf(n) + 1) + ')';
        }
        parts.unshift(part);
        if (n.id) { parts.unshift('#' + n.id); break; }
        n = n.parentElement;
      }
      return parts.join(' > ').slice(0, 160);
    } catch (e) { return ''; }
  };

  O.interactive = function(el){
    if (el.disabled) return false;
    var tag = el.tagName.toLowerCase();
    if (tag === 'a') return el.hasAttribute('href') || el.hasAttribute('onclick') || (el.getAttribute('role')||'') !== '';
    if (tag === 'input') return (el.type || 'text') !== 'hidden';
    if (tag === 'button' || tag === 'select' || tag === 'textarea' || tag === 'summary' || tag === 'label') return true;
    var role = (el.getAttribute('role') || '').toLowerCase();
    if (['button','link','checkbox','radio','switch','combobox','listbox','textbox','tab','menuitem','option','slider','searchbox','spinbutton','disclosure'].indexOf(role) >= 0) return true;
    if (el.hasAttribute('onclick')) return true;
    if (el.isContentEditable) return true;
    var ti = el.getAttribute('tabindex');
    if (ti !== null && ti !== '-1') return true;
    var s = window.getComputedStyle(el);
    if (s.cursor === 'pointer') return true;
    return false;
  };

  O.isScroller = function(el){
    try {
      var s = window.getComputedStyle(el);
      var oy = s.overflowY, ox = s.overflowX;
      var vert = (oy === 'auto' || oy === 'scroll') && el.scrollHeight > el.clientHeight + 8;
      var horz = (ox === 'auto' || ox === 'scroll') && el.scrollWidth > el.clientWidth + 8;
      return vert || horz ? (vert ? 'v' : 'h') : null;
    } catch (e) { return null; }
  };

  O.snapshot = function(maxElements, maxText){
    O.oidSeq = 0;
    O.docs = [document];
    var frames = [];
    var els = [];
    var INTER = 'a,button,input,select,textarea,summary,label,[role],[onclick],[contenteditable],[tabindex]';
    var vh = window.innerHeight || 800;
    var vw = window.innerWidth || 400;

    function collect(doc, ox, oy, frameId){
      var list;
      try { list = doc.querySelectorAll(INTER); } catch (e) { return; }
      for (var i = 0; i < list.length; i++) {
        var el = list[i];
        if (!O.shown(el) || !O.interactive(el)) continue;
        var r;
        try { r = el.getBoundingClientRect(); } catch (e) { continue; }
        if (r.width < 2 || r.height < 2) continue;
        var role = O.roleOf(el);
        var tag = el.tagName.toLowerCase();
        var pw = tag === 'input' && (el.type === 'password');
        var val = null, hasValue = false;
        if (tag === 'input' || tag === 'textarea') {
          hasValue = !!el.value;
          val = pw ? null : String(el.value || '').slice(0, 60);
        } else if (tag === 'select') {
          var o = el.options && el.options[el.selectedIndex];
          val = o ? (o.textContent || '').trim().slice(0, 60) : null;
        } else if (el.isContentEditable) {
          val = (el.textContent || '').slice(0, 60);
        }
        var oid = el.getAttribute('data-oid');
        if (!oid) { oid = 'o' + (++O.oidSeq) + 'f' + frameId; try { el.setAttribute('data-oid', oid); } catch(e){} }
        var chk = null;
        if (el.type === 'checkbox' || el.type === 'radio') chk = !!el.checked;
        else {
          var rr = (el.getAttribute('role') || '').toLowerCase();
          if (rr === 'checkbox' || rr === 'radio' || rr === 'switch') chk = el.getAttribute('aria-checked') === 'true';
        }
        var exp = null;
        if (el.hasAttribute && el.hasAttribute('aria-expanded')) exp = el.getAttribute('aria-expanded') === 'true';
        els.push({
          i: 0, r: role, t: O.labelOf(el, role),
          x: Math.round(r.left + ox), y: Math.round(r.top + oy),
          w: Math.round(r.width), h: Math.round(r.height),
          oid: oid, sel: O.selectorOf(el), f: frameId,
          ed: (tag === 'input' && !pw && (el.type||'text') !== 'checkbox' && (el.type||'text') !== 'radio' && (el.type||'text') !== 'file' && (el.type||'text') !== 'range' && (el.type||'text') !== 'submit' && (el.type||'text') !== 'button') || tag === 'textarea' || !!el.isContentEditable || ['textbox','searchbox','spinbutton'].indexOf((el.getAttribute('role')||'').toLowerCase()) >= 0,
          pw: pw, hasValue: hasValue, val: val, dis: !!el.disabled || el.getAttribute('aria-disabled') === 'true',
          chk: chk, exp: exp,
          href: tag === 'a' && el.href ? String(el.href).slice(0, 140) : null,
          opt: tag === 'select' ? el.options.length : 0,
          selected: tag === 'select' ? val : null,
          itype: tag === 'input' ? (el.type || 'text') : null,
          multi: tag === 'textarea' || !!el.isContentEditable,
          vp: r.top >= -4 && r.bottom <= vh + 4 && r.left >= -4 && r.right <= vw + 4
        });
      }
      // scroll containers
      try {
        var scrollers = doc.querySelectorAll('div,section,ul,main,aside');
        var sc = 0;
        for (var j = 0; j < scrollers.length && sc < 6; j++) {
          var s2 = scrollers[j];
          var kind = O.isScroller(s2);
          if (!kind) continue;
          var r2; try { r2 = s2.getBoundingClientRect(); } catch(e){ continue; }
          if (r2.width < 40 || r2.height < 40) continue;
          var oid2 = s2.getAttribute('data-oid');
          if (!oid2) { oid2 = 'o' + (++O.oidSeq) + 'f' + frameId; try { s2.setAttribute('data-oid', oid2); } catch(e){} }
          var vert = kind === 'v';
          els.push({
            i: 0, r: 'scroller', t: O.txt(s2.getAttribute('aria-label') || s2.className || '', 40),
            x: Math.round(r2.left + ox), y: Math.round(r2.top + oy), w: Math.round(r2.width), h: Math.round(r2.height),
            oid: oid2, sel: O.selectorOf(s2), f: frameId,
            scrBack: vert ? s2.scrollTop > 4 : s2.scrollLeft > 4,
            scrFwd: vert ? s2.scrollTop + s2.clientHeight < s2.scrollHeight - 4 : s2.scrollLeft + s2.clientWidth < s2.scrollWidth - 4,
            hScroll: !vert, vp: r2.top >= -4 && r2.bottom <= vh + 4
          });
          sc++;
        }
      } catch (e) {}
      // frames
      try {
        var ifr = doc.querySelectorAll('iframe');
        for (var k = 0; k < ifr.length; k++) {
          var f = ifr[k];
          var fr; try { fr = f.getBoundingClientRect(); } catch(e){ continue; }
          var src = f.src || '';
          var cd = null;
          try { cd = f.contentDocument; } catch (e) { cd = null; }
          if (cd) {
            var fid = frames.length + 1;
            frames.push({id:fid, src:src.slice(0, 160), crossOrigin:false, control:true, humanCheck:!!O.detectCheck(cd)});
            O.docs.push(cd);
            collect(cd, ox + fr.left, oy + fr.top, fid);
          } else if (src) {
            var lower = src.toLowerCase();
            var isCheck = /recaptcha|hcaptcha|turnstile|arkoselabs|funcaptcha|geetest|challenge/.test(lower);
            frames.push({id:frames.length + 1, src:src.slice(0, 160), crossOrigin:true, control:false, humanCheck:isCheck});
          }
        }
      } catch (e) {}
    }

    var mainCheck = O.detectCheck(document);
    collect(document, 0, 0, 0);

    // Viewport-first ordering, DOM order inside each group, then number 1..N.
    els.sort(function(a, b){ return (b.vp ? 1 : 0) - (a.vp ? 1 : 0) || (a.y - b.y) || (a.x - b.x); });
    var total = els.length;
    if (total > maxElements) els = els.slice(0, maxElements);
    for (var n = 0; n < els.length; n++) { els[n].i = n + 1; delete els[n].vp; }

    var text = O.markdown(O.mainRoot(null), maxText);
    var de = document.documentElement;
    return JSON.stringify({
      ok: true,
      url: location.href, title: document.title || '',
      scrollY: Math.round(window.scrollY),
      pageHeight: Math.max(de ? de.scrollHeight : 0, document.body ? document.body.scrollHeight : 0),
      viewportW: Math.round(window.innerWidth), viewportH: Math.round(window.innerHeight),
      loading: document.readyState !== 'complete',
      elements: els, moreElements: Math.max(0, total - els.length),
      frames: frames,
      forms: document.forms ? document.forms.length : 0,
      humanCheck: mainCheck ? {kind: mainCheck, passed: false} : (function(){
        for (var q = 0; q < frames.length; q++) if (frames[q].humanCheck) return {kind:'text', passed:false};
        return null;
      })(),
      text: text
    });
  };
})();
""".trimIndent()

    // ---------------------------------------------------------- call shapes

    fun snapshotJs(maxElements: Int = 220, maxText: Int = 4_000): String =
        "window.__oro ? window.__oro.snapshot($maxElements, $maxText) : JSON.stringify({ok:false, why:'runtime missing'})"

    fun clickJs(oid: String): String = "window.__oro.click(${q(oid)})"
    fun typeJs(oid: String?, text: String, append: Boolean): String =
        "window.__oro.type(${oid?.let { q(it) } ?: "null"}, ${q(text)}, $append)"
    fun selectJs(oid: String, option: String): String = "window.__oro.select(${q(oid)}, ${q(option)})"
    fun checkJs(oid: String, want: Boolean): String = "window.__oro.check(${q(oid)}, $want)"
    fun keyJs(key: String, oid: String?): String = "window.__oro.key(${q(key)}, ${oid?.let { q(it) } ?: "null"})"
    fun hoverJs(oid: String): String = "window.__oro.hover(${q(oid)})"
    fun scrollJs(direction: String, oid: String?, amount: Float): String =
        "window.__oro.scroll(${q(direction)}, ${oid?.let { q(it) } ?: "null"}, $amount)"
    fun scrollToTextJs(text: String): String = "window.__oro.scrollToText(${q(text)})"
    fun textVisibleJs(text: String): String = "window.__oro.textVisible(${q(text)})"
    fun hashJs(): String = "window.__oro.hash()"
    fun evalJs(js: String): String = "window.__oro.evalIn(${q(js)})"
    fun readJs(mode: String, selector: String?, maxChars: Int): String =
        "window.__oro.read(${q(mode)}, ${selector?.let { q(it) } ?: "null"}, $maxChars)"

    fun extractJs(selector: String?, fieldsJson: String, limit: Int): String =
        "window.__oro.extract(${selector?.let { q(it) } ?: "null"}, ${q(fieldsJson)}, $limit)"

    /** A JavaScript string literal, safely quoted (org.json's quoter). */
    fun q(s: String): String = org.json.JSONObject.quote(s)
}
