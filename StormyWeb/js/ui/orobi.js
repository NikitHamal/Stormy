// Orobi, Oro's mascot: the brand-locked geometry from core/designsystem (Orobi.kt).
const G = {
  viewBox: '200 400 1200 820',
  body: 'M810,710C760,635 650,503 520,503C370,503 256,660 256,810C256,990 380,1148 510,1148C640,1148 740,1050 810,933C880,1040 980,1148 1100,1148C1250,1148 1345,1020 1345,880C1345,760 1290,650 1190,590C1205,570 1200,520 1160,495C1140,478 1115,472 1085,472C1060,472 1040,480 1030,487L995,520C910,560 850,640 810,710ZM743,825C690,745 600,631 520,631C435,631 377,730 377,835C377,940 440,1017 520,1017C600,1017 690,905 743,825ZM880,817C940,720 1000,650 1030,632C1065,645 1110,650 1160,630C1215,690 1241,770 1241,835C1241,940 1180,1017 1115,1017C1030,1017 940,940 880,817Z',
  markL: 'M553,400L553,503C515,555 487,600 477,637L470,660L420,760L390,930C400,1010 440,1090 505,1148L505,1200L317,1200L317,925Q317,850 256,815L256,803Q317,770 317,690L317,400Z',
  markR: 'M1020,400L1020,470C1070,520 1105,580 1127,642L1150,670L1190,950L1205,965C1195,1050 1160,1110 1097,1148L1097,1200L1285,1200L1285,935Q1285,850 1340,820L1340,800Q1285,770 1285,680L1285,400Z',
  loader: 'M810 822C880 700 980 568 1105 568C1230 568 1295 700 1295 830C1295 960 1220 1082 1105 1082C980 1082 880 940 810 822C740 700 640 567 520 567C400 567 317 700 317 825C317 950 400 1082 520 1082C640 1082 740 940 810 822Z',
  eyes: [[717, 634], [885, 634]],
};
let n = 0;
/** onLight: ink body for light backgrounds; otherwise peach (OrobiColors.forBackground). */
export function orobi(width = 120, { skin = 'var(--orobi-skin)', eye = 'var(--orobi-eye)' } = {}) {
  const id = 'ob' + n++;
  return `<svg class="orobi" viewBox="${G.viewBox}" width="${width}" height="${width * 820 / 1200}" aria-hidden="true">
    <defs><clipPath id="${id}"><path d="${G.body}" clip-rule="evenodd"/></clipPath></defs>
    <path d="${G.body}" fill-rule="evenodd" fill="${skin}"/>
    <g clip-path="url(#${id})"><path d="${G.markL}" fill="#F800F8"/><path d="${G.markR}" fill="#F800F8"/></g>
    ${G.eyes.map(([x, y]) => `<ellipse cx="${x}" cy="${y}" rx="11.5" ry="14.5" fill="${eye}"/><circle cx="${x + 3.4}" cy="${y - 5.2}" r="3.6" fill="#fff"/>`).join('')}</svg>`;
}
/** The infinity loader (OrobiWorking). */
export const loader = (w = 26) => `<svg class="loader" viewBox="200 500 1200 650" width="${w}" height="${w * 650 / 1200}"><path d="${G.loader}" fill="none" stroke="#F800F8" stroke-width="90" stroke-linecap="round" pathLength="100"/></svg>`;
