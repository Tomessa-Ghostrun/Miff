// Écrans 01 à 07 : formulaire « Nouveau lieu / Modifier le lieu », suggestions, Miffs, états, suppression.
import { page, board, header, sec, fold, icon, nameField, miffField, addressField, suggestions, map, slider, typeCards, catList, btn, notice, statusBar, CATS } from "./lib.mjs";

const ADDR = "17 Avenue d'Italie, 75013 Paris, France";
const SUGG = [
  { t: "<b>17 Av</b>enue d'Italie", s: "75013 Paris, France", press: true },
  { t: "<b>17 Av</b>enue d'Ivry", s: "75013 Paris, France" },
  { t: "<b>17 Av</b>enue de l'Italie", s: "94310 Orly, France" },
  { t: "<b>17 Av</b>enue Italie", s: "69003 Lyon, France" },
  { t: "<b>17 Av</b>. d'Italia", s: "Lausanne, Suisse" },
];

/** Construit le formulaire complet (hauteur libre). */
export function formHtml(o = {}) {
  const {
    mode = "new", name = "", nameState = "", nameMsg = "", miffs = ["Famille Mfouka"], miffsOpen = false, miffRo = false,
    addr = {}, r = 100, type = "trust", premium = true, category = "home", save = false, busy = false, notices = "", showFold = true,
    miffHint = true, disabledAll = false,
  } = o;
  const chosen = !!addr.chosen;
  const nameBlock = nameField({ value: name, state: nameState, msg: nameMsg });
  const miffBlock = miffField({ chips: miffs, open: miffsOpen, ro: miffRo }) + (miffHint ? `<div class="hint">${icon("eye")}<span>${miffRo ? "Les membres de cette Miff verront ce lieu : nom, adresse, carte et rayon." : "Les membres de ces Miffs verront ce lieu : nom, adresse, carte et rayon."}</span></div>` : "");
  const addrBlock = addressField(addr) + (addr.below || "");
  const perim = (chosen || addr.mapAlways ? map({ r, type, unavailable: addr.mapUnavailable }) : `<div class="map empty">${icon("pin")}<div>Choisis une adresse pour voir ton lieu sur la carte.</div></div>`) + slider(r, { off: !chosen }) +
    (r < 100 && chosen ? `<div class="hint">${icon("info")}<span>En dessous de 100 m, la détection peut manquer un passage.</span></div>` : "");
  const title = mode === "edit" ? "Modifier le lieu" : "Nouveau lieu";
  const label = busy ? `<span class="spin"></span>On enregistre ton lieu…` : "Enregistrer";
  const foot = `<div class="footer"><div class="btn-stack">${notices}${btn(label, "primary", save || busy ? "" : "disabled")}${mode === "edit" ? btn("Supprimer ce lieu", "sensitive") : ""}</div></div>`;
  const body = `<div class="frame tall" style="${disabledAll ? "" : ""}">${header(title)}<div class="content" ${busy ? 'style="opacity:.98"' : ""}>
    ${sec("pencil", "Nom", "Choisis un nom pour retrouver l'endroit facilement", nameBlock)}
    ${sec("group", miffRo ? "Miff" : "Miffs", miffRo ? "Ce lieu sera attaché à ta Miff." : "Choisis les Miffs attachées à ce lieu. Tu ne peux choisir que les Miffs que tu as toi-même créées.", miffBlock)}
    ${sec("pin", "Adresse", "Entre l'adresse exacte de ce lieu, puis choisis-la dans la liste.", addrBlock)}
    ${sec("radius", "Périmètre", "Définis le rayon autour du lieu", perim)}
    ${sec("eye", "Type", "Quel type de zone définis-tu ?", typeCards({ sel: type, premium }))}
    ${sec("grid", "Catégorie", "Sélectionne la catégorie qui convient", catList(category))}
  </div>${foot}${showFold ? fold() : ""}</div>`;
  return body;
}

export const screens = [];

// 01 — vide
screens.push({ file: "01-nouveau-lieu-vide", title: "Nouveau lieu — vide", html: () => page("Nouveau lieu — vide", formHtml({ name: "", miffs: ["Famille Mfouka"], addr: {}, save: false })) });

// 02 — suggestions (clavier ouvert, formulaire défilé)
screens.push({
  file: "02-nouveau-lieu-suggestions", title: "Nouveau lieu — suggestions d'adresse",
  html: () => {
    const f = `<div class="frame">${header("Nouveau lieu")}
      <div class="content" style="padding-top:20px;margin-top:-250px">
        <div style="height:250px"></div>
        ${sec("pin", "Adresse", "Entre l'adresse exacte de ce lieu, puis choisis-la dans la liste.", addressField({ typed: "17 av", state: "focus" }) + suggestions(SUGG))}
      </div><div class="kbd">clavier du système (non dessiné)</div></div>`;
    return page("Nouveau lieu — suggestions d'adresse", f);
  },
});

// 03 — rempli
screens.push({
  file: "03-nouveau-lieu-rempli", title: "Nouveau lieu — rempli",
  html: () => page("Nouveau lieu — rempli", formHtml({ name: "Maison du père de Nathan", miffs: ["Famille Mfouka", "Les tuches"], addr: { chosen: true, value: ADDR }, r: 150, save: true, category: "home" })),
});

// 04 — états du formulaire (planche)
screens.push({
  file: "04-formulaire-etats", title: "Formulaire — états et erreurs",
  html: () => {
    const frag = (inner) => `<div class="frame fragment"><div class="content" style="padding-bottom:12px">${inner}</div></div>`;
    const nameSec = (n) => sec("pencil", "Nom", "Choisis un nom pour retrouver l'endroit facilement", n);
    const addrSec = (inner) => sec("pin", "Adresse", "Entre l'adresse exacte de ce lieu, puis choisis-la dans la liste.", inner);
    const slots = [
      ["A. Nom trop court", "Au clic sur « Enregistrer » avec 1 seul caractère. Le bouton était actif (1 caractère + adresse choisie) ; rien n'est écrit.",
        frag(nameSec(nameField({ value: "M", state: "error" })) + `<div class="footer" style="padding:0">${btn("Enregistrer", "primary")}</div>`)],
      ["B. Limite de 50 caractères", "La saisie s'arrête à 50 ; message du ticket Notion, repris tel quel.",
        frag(nameSec(nameField({ value: "Maison de vacances de la famille de Nathan au bord", state: "focus", msg: "limit" })))],
      ["C. Adresse : recherche en cours", "Dès 3 caractères, après 300 ms sans frappe. Lignes squelettes, la saisie reste possible.",
        frag(addrSec(addressField({ typed: "17 av", state: "focus" }) + `<div class="sugg">${[0, 1, 2].map(() => `<div class="row"><div class="mini-pin" style="background:rgba(217,217,217,.6)"></div><div style="flex:1"><div class="skel" style="width:70%"></div><div class="skel" style="width:40%;margin-top:6px"></div></div></div>`).join("")}</div>`))],
      ["D. Adresse : aucun résultat", "Message du ticket (§11).",
        frag(addrSec(addressField({ typed: "17 avnue ditalee xx", state: "focus" }) + `<div class="sugg"><div class="msg-row">${icon("search")}<div>On ne trouve pas cette adresse. Essaie avec moins de détails.</div></div></div>`))],
      ["E. Adresse : service indisponible", "Message du ticket (§11) + « Réessayer ». Sans suggestions, aucun lieu ne peut être créé : l'état doit rester calme et réessayable.",
        frag(addrSec(addressField({ typed: "17 avenue d'it", state: "focus" }) + `<div class="sugg"><div class="msg-row">${icon("cloudoff")}<div>La recherche d'adresse n'est pas disponible pour le moment. Réessaie dans un instant.<div class="link" style="margin-top:6px">Réessayer</div></div></div></div>`))],
      ["F. Adresse saisie mais pas choisie", "La personne a tapé toute l'adresse sans toucher une suggestion : le bouton reste inactif ; indication discrète à la sortie du champ (pas de rouge).",
        frag(addrSec(addressField({ typed: "17 Avenue d'Italie, 75013 Paris" }) + `<div class="hint">${icon("info")}<span>Choisis ton adresse dans la liste pour continuer.</span></div>`) + `<div class="footer" style="padding:0">${btn("Enregistrer", "primary", "disabled")}</div>`)],
      ["G. Rayon sous 100 m", "Note discrète sous le curseur (décision du PM). Le curseur reste libre de 50 à 800 m, par pas de 50.",
        frag(sec("radius", "Périmètre", "Définis le rayon autour du lieu", map({ r: 50 }) + slider(50) + `<div class="hint">${icon("info")}<span>En dessous de 100 m, la détection peut manquer un passage.</span></div>`))],
      ["H. Enregistrement en cours", "Un appui = une opération ; champs inactifs ; rien n'est affiché comme réussi avant l'accusé du serveur.",
        frag(`<div style="opacity:.5">${nameSec(nameField({ value: "Maison du père de Nathan" }))}</div><div class="footer" style="padding:0">${btn('<span class="spin"></span>On enregistre ton lieu…', "primary")}</div>`)],
      ["I. Hors connexion", "Rien n'est mis en file : création impossible hors ligne (texte du ticket Miff §5.10).",
        frag(`<div class="btn-stack">${notice("offline", "Pas de connexion pour le moment. Réessaie dès que le réseau est revenu.")}${btn("Enregistrer", "primary")}</div>`)],
      ["J. Erreur à l'enregistrement", "Texte du ticket Miff §5.10 ; le bouton reste actif.",
        frag(`<div class="btn-stack">${notice("error", "Ça n'a pas fonctionné. Réessaie dans un instant.")}${btn("Enregistrer", "primary")}</div>`)],
      ["K. Une Miff choisie n'existe plus", "Cas limite 14 du ticket : le lot est refusé en entier. Texte proposé, à valider.",
        frag(`<div class="btn-stack">${notice("error", "Une des Miffs choisies n'est plus disponible. Retire-la de la liste et réessaie.")}${btn("Enregistrer", "primary")}</div>`)],
      ["L. Lieu enregistré", "Retour à la liste des lieux, avec le texte honnête sur la propagation (§6.4). Aucune coche de succès : l'écran se referme sur la liste.",
        frag(`<div class="notice info">${icon("check")}<div>Le lieu « Maison du père de Nathan » est enregistré. Tes proches le prendront en compte dès qu'ils ouvriront Miff, ou dans les prochaines heures.</div></div>`)],
    ];
    return page("Formulaire — états et erreurs", `<div style="width:1304px">${board(slots)}</div>`);
  },
});

// 05 — choix de plusieurs Miffs (planche)
screens.push({
  file: "05-choix-miffs", title: "Choix de plusieurs Miffs",
  html: () => {
    const row = (n, sub, on, dis = false, tag = "") => `<div class="kv" style="${dis ? "opacity:.45" : ""}"><div class="radio ${on ? "on" : ""}" style="border-radius:6px">${on ? icon("check", "") : ""}</div><div style="flex:1"><div class="v">${n}</div>${sub ? `<div class="k">${sub}</div>` : ""}</div>${tag}</div>`;
    const sheet = (rows, extra = "", cta = btn("Terminé", "primary")) => `<div style="position:absolute;left:0;right:0;bottom:0;z-index:30;background:var(--surface);border-radius:16px 16px 0 0;box-shadow:0 -4px 12px rgba(0,0,0,.2);padding:10px 0 28px"><div style="width:40px;height:4px;border-radius:2px;background:var(--border-subtle);margin:0 auto 12px"></div><div style="padding:0 16px 8px"><div class="sect-title">Miffs de ce lieu</div><div class="sm">Tu vois ici les Miffs que tu as créées.</div></div>${rows}${extra}<div style="padding:12px 16px 0">${cta}</div></div>`;
    const tagDef = `<span class="plus-tag" style="background:var(--safe-tint);color:#000">Dernière rejointe</span>`;
    const base = (chips, rows, extra, cta) => `<div class="frame" style="height:844px">${formHtml({ name: "Maison du père de Nathan", miffs: chips, miffsOpen: true, addr: { chosen: true, value: ADDR }, showFold: false }).replace('<div class="frame tall"', '<div class="frame-inner" style="height:844px;overflow:hidden"').replace(/<\/div>$/, "</div>")}<div class="veil"></div>${sheet(rows, extra, cta)}</div>`;
    const slots = [
      ["A. Sélecteur ouvert (par défaut)", "La dernière Miff rejointe, si la personne l'a créée, est cochée d'office ; sinon la première Miff créée. Plusieurs choix possibles.",
        base(["Famille Mfouka"], row("Famille Mfouka", "5 membres", true, false, tagDef) + row("Les tuches", "3 membres", false) + row("Copains du foot", "8 membres", false))],
      ["B. Plusieurs Miffs cochées", "Les chips du champ se mettent à jour à chaque coche. Compteur discret « 2 sur 5 » dès qu'on dépasse 1.",
        base(["Famille Mfouka", "Les tuches"], row("Famille Mfouka", "5 membres", true, false, tagDef) + row("Les tuches", "3 membres", true) + row("Copains du foot", "8 membres", false), `<div class="sm" style="padding:6px 16px 0">2 Miffs sur 5 au maximum</div>`)],
      ["C. Limite de 5 atteinte", "Les Miffs non cochées s'estompent ; message factuel, pas d'erreur rouge.",
        base(["Famille Mfouka", "Les tuches", "Copains du foot", "Travail", "Cousins"], row("Famille Mfouka", "", true) + row("Les tuches", "", true) + row("Copains du foot", "", true) + row("Travail", "", true) + row("Cousins", "", true) + row("Voisins", "", false, true), `<div class="sm" style="padding:6px 16px 0">Un lieu peut être attaché à 5 Miffs au maximum.</div>`)],
      ["D. Aucune Miff cochée", "« Terminé » inactif : un lieu appartient à au moins une Miff.",
        base([], row("Famille Mfouka", "5 membres", false) + row("Les tuches", "3 membres", false) + row("Copains du foot", "8 membres", false), `<div class="sm" style="padding:6px 16px 0">Choisis au moins une Miff.</div>`, btn("Terminé", "primary", "disabled"))],
      ["E. Une seule Miff créée : pas de sélecteur", "Pas de liste à ouvrir : la Miff est affichée, non modifiable, avec la phrase « qui voit quoi ». Cas de tout compte gratuit (une seule Miff).",
        `<div class="frame fragment"><div class="content" style="padding-bottom:12px">${sec("group", "Miff", "Ce lieu sera attaché à ta Miff.", miffField({ chips: ["Famille Mfouka"], ro: true }) + `<div class="hint">${icon("eye")}<span>Les membres de cette Miff verront ce lieu : nom, adresse, carte et rayon.</span></div>`)}</div></div>`],
    ];
    return page("Choix de plusieurs Miffs", `<div style="width:1304px">${board(slots)}</div>`);
  },
});

// 06 — modifier
screens.push({
  file: "06-modifier-lieu", title: "Modifier le lieu",
  html: () => page("Modifier le lieu", formHtml({ mode: "edit", name: "Maison de Papa", miffs: ["Famille Martin"], addr: { chosen: true, value: "26 rue du Volnay, 21000 Dijon, France" }, r: 150, save: false, category: "home" })),
});

// 07 — confirmation de suppression (planche : défaut, en cours, hors connexion, lieu dans 2 Miffs)
screens.push({
  file: "07-confirmation-suppression", title: "Confirmation de suppression",
  html: () => {
    const bg = `<div class="frame" style="position:absolute;inset:0;filter:blur(5px)">${formHtml({ mode: "edit", name: "Maison de Papa", miffs: ["Famille Martin"], addr: { chosen: true, value: "26 rue du Volnay, 21000 Dijon, France" }, r: 150, showFold: false }).replace('<div class="frame tall"', '<div class="frame-inner"')}</div>`;
    const dlg = (txt, primary, extra = "", cancelOff = false) => `<div class="frame" style="height:844px">${bg}<div class="veil"></div><div class="dialog"><h3>Supprimer ce lieu ?</h3><p>${txt}</p>${extra}${primary}<div class="cancel" style="${cancelOff ? "opacity:.4" : ""}">Annuler</div></div></div>`;
    const t1 = "Ce lieu sera définitivement supprimé pour toi et pour tous les membres des Miffs associées.";
    const slots = [
      ["A. Confirmation (lieu dans 1 Miff)", "Texte de la maquette du PM, au tutoiement. Ton « Sensitive » du composant Dialog : contour rouge, pas de fond rouge. « Annuler » est le premier élément au focus.",
        dlg(t1, btn("Supprimer ce lieu", "sensitive"))],
      ["B. Lieu attaché à plusieurs Miffs", "La liste des Miffs concernées rend visible l'effet « dans toutes ses Miffs » (décision Q4).",
        dlg("Ce lieu sera définitivement supprimé pour toi et pour tous les membres de ces Miffs :", btn("Supprimer ce lieu", "sensitive"), `<div class="dlist"><div>${icon("group")}Famille Martin</div><div>${icon("group")}Les tuches</div></div>`)],
      ["C. Suppression en cours", "Bouton inactif avec indicateur, « Annuler » inactif le temps de l'opération (un appui = une opération).",
        dlg(t1, btn('<span class="spin" style="border-color:rgba(214,7,0,.25);border-top-color:#d60700"></span>Suppression…', "sensitive"), "", true)],
      ["D. Hors connexion", "Notice dans le dialogue ; le bouton reste actif ; rien n'est mis en file.",
        dlg(t1, btn("Supprimer ce lieu", "sensitive"), notice("offline", "Pas de connexion pour le moment. Réessaie dès que le réseau est revenu."))],
    ];
    return page("Confirmation de suppression", `<div style="width:1304px">${board(slots)}</div>`);
  },
});
