// Home (HomeScreen.kt): wordmark bar, search, project cards with chat counts, speed-dial FAB.
import { h, ic, esc, actionSheet, formSheet, snackbar, field } from '../ui/kit.js';
import { state } from '../data.js';
import { navigate } from '../app.js';

export default {
  render() {
    const el = h(`<div>
      <div class="topbar"><span class="title wordmark">Oro</span>
        <button class="iconbtn" data-search>${ic('search')}</button><button class="iconbtn" data-settings>${ic('settings')}</button></div>
      <div class="search">${ic('search')}<span class="t-body-l">Search projects</span></div>
      <div class="scroll"><div class="section-label t-label-s-e muted">Projects</div><div class="list" data-list></div><div style="height:96px"></div></div>
      <button class="fab" data-new>${ic('add')}<span>New project</span></button></div>`);
    const list = el.querySelector('[data-list]');
    const draw = () => {
      list.innerHTML = state.projects.slice().sort((a, b) => b.pinned - a.pinned).map((p) => `
        <div class="card tap project" data-p="${p.id}">
          <span class="avatar ready shape-${p.shape}" style="width:40px;height:40px">${esc(p.name[0])}</span>
          <span class="grow"><div class="t-title-m one">${esc(p.name)}</div><div class="t-body-s muted">${p.when} · ${p.chats} chat${p.chats === 1 ? '' : 's'}</div></span>
          ${p.pinned ? ic('push_pin', 's fill muted') : ''}<button class="iconbtn dim" data-more="${p.id}">${ic('more_vert')}</button></div>`).join('');
      list.querySelectorAll('[data-p]').forEach((c) => (c.onclick = (e) => { if (!e.target.closest('[data-more]')) navigate('editor', { id: c.dataset.p }); }));
      list.querySelectorAll('[data-more]').forEach((b) => (b.onclick = () => {
        const p = state.projects.find((x) => x.id === b.dataset.more);
        actionSheet({ title: p.name, subtitle: p.id === 'p2' ? 'https://mara-ilves.netlify.app' : null, actions: [
          { label: p.pinned ? 'Unpin' : 'Pin to top', icon: 'push_pin', description: 'Keep it above the rest of the list', onClick: () => { p.pinned = !p.pinned; draw(); } },
          { label: 'Rename', icon: 'edit' },
          { label: 'Share as zip', icon: 'share', description: 'Every file, ready to open anywhere', onClick: () => snackbar('Sharing ' + p.name + '.zip') },
          ...(p.id === 'p2' ? [{ label: 'Share live link', icon: 'link' }] : []),
          { label: 'Delete project', icon: 'delete', destructive: true },
        ] });
      }));
    };
    el.querySelector('[data-new]').onclick = () => formSheet({ title: 'New project', icon: 'add', confirm: 'Create',
      body: field('Name', '', { id: 'name' }) + field('Description (optional)', '', { id: 'd' }),
      onConfirm: (s) => { const name = s.querySelector('[data-f=name]').value || 'Untitled'; state.projects.unshift({ id: 'p' + Date.now(), name, when: 'Now', chats: 0, pinned: false, shape: 2 }); draw(); } });
    el.querySelector('[data-settings]').onclick = () => navigate('settings');
    draw();
    return el;
  },
};
