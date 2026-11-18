// Traitement d'une alerte « Je suis en danger » (AD-B2, ticket docs/tickets/alerte-danger.md §10.3, §11) : qui notifier, une seule fois, avec un plafond
// SILENCIEUX. Fonction SANS lien avec le declencheur (notifierAlerte.ts) : Firestore, FCM, horloge et attente sont INJECTES (meme contrat que
// notifTraitement.ts : testee a l'emulateur avec un faux client FCM, sans compte ni reseau).
//
// Differences voulues avec notifierUpdate (decisions du PM du 2026-11-18) :
//   - TOUS les membres de la Miff d'origine qui ont un appareil de session sont prevenus, MEME sans partage de position (D16) ; l'auteur ne l'est jamais ;
//   - l'interrupteur « Quand un proche donne des nouvelles » (notify.updates) n'agit PAS (un reglage de confort ne coupe pas une alerte) : seuls l'absence de
//     jeton (interrupteur general, desinstallation) ou de session ecartent un destinataire ;
//   - aucune heure calme, aucun plafond horaire des updates ; PLAFOND TECHNIQUE silencieux a la place : 10 debuts par auteur, par Miff et par 24 h, 1 par minute ;
//   - DEBUT et FIN MANUELLE seulement (le declencheur ne transmet jamais une expiration) ; une fin n'est notifiee que si le DEBUT de cette alerte l'a ete.
//
// Ordre (chaque etape peut s'arreter sans rien ecrire) :
//   1. garde-fous de l'evenement (identifiants sains, date presente et recente)                              0 lecture
//   2. PRE-CONTROLE de notifState/{miffId}_alertes : deja vu ? plafond ? debut non notifie ? -> arret, 1 lecture seule
//   3. la Miff existe encore (1 lecture)
//   4. fiches de membres (40 au plus) -> auteur (prenom), destinataires (39 au plus)                         1 lecture par fiche
//   5. SESSION ACTIVE de chaque destinataire, puis get DIRECT de devices/{session.deviceId}                 2 lectures par destinataire (1 sans session)
//   6. reservation transactionnelle (verification finale, meme verdict)                                     1 lecture, 1 ecriture SEULEMENT si on envoie
//   7. envoi FCM par appareil (2 nouvelles tentatives au plus), purge des jetons que FCM dit inconnus
// Budget (Miff de 4 membres, 3 destinataires, 1 appareil chacun) : 1 + 1 + 4 + 3 x 2 + 1 = 13 lectures, 1 ecriture. Evenement ecarte : 1 lecture, 0 ecriture.
// "Au plus une fois" : la reservation (etape 6) PRECEDE l'envoi ; un plantage entre les deux perd la notification (l'alerte reste visible dans le Feed) et ne
// la double jamais. Journaux : uniquement des nombres et des mots fixes (journal.ts), jamais d'identifiant, de prenom ni de jeton.
import { FieldPath, Timestamp } from "firebase-admin/firestore";
import { journaliser, type ChampsJournal } from "./journal.js";
import {
  ecarter,
  enTimestamp,
  envoyerAvecReprises,
  nanos,
  type Bilan,
  type Cible,
  type Dependances,
} from "./notifTraitement.js";
import { prenomAffichable } from "./notifRegles.js";
import {
  AGE_MAX_EVENEMENT_MS,
  DUREE_ETAT_ALERTE_MS,
  FENETRE_PLAFOND_MS,
  INTERVALLE_MIN_DEBUTS_MS,
  MAX_DESTINATAIRES_ALERTE,
  MAX_FICHES_ALERTE,
  PLAFOND_DEBUTS,
  construireMessageAlerte,
  type Phase,
} from "./alerteRegles.js";

export type { Dependances } from "./notifTraitement.js";

export interface EvenementAlerte {
  miffId: string;
  auteurUid: string;
  phase: Phase;
  /** `startedAt` du document d'alerte (heure du serveur imposee par les regles) : identite du DEBUT, inchangee a la fin. */
  startedAt: unknown;
  /** `endedAt` (heure du serveur) : seulement pour une fin. */
  endedAt?: unknown;
}

export type MotifArretAlerte =
  | "date_absente"
  | "trop_ancien"
  | "identifiant_invalide"
  | "auteur_absent"
  | "prenom_vide"
  | "aucun_destinataire"
  | "deja_vu"
  | "plafond"
  | "debut_non_notifie"
  | "miff_absente";

export interface BilanAlerte extends Omit<Bilan, "motif"> {
  phase: Phase;
  /** Fin notifiee : duree de l'alerte en MINUTES entieres (nombre seul, sans identifiant) : sert au signal « fausses alertes » (docs/dev/surveillance-quotas.md). */
  dureeAlerteMin?: number;
  /** Present si rien n'a ete envoye faute de raison d'envoyer ; absent si au moins une reservation a eu lieu. */
  motif?: MotifArretAlerte;
}

const IDENTIFIANT = /^[A-Za-z0-9_-]{1,128}$/;

/** Document d'etat de la fonction d'alerte (SERVEUR SEUL, aucune regle Firestore : refuse par defaut). Distinct de notifState/{miffId} pour sa duree de vie (9 j). */
export const cheminEtatAlerte = (miffId: string): string => `notifState/${miffId}_alertes`;

interface SessionActive {
  deviceId?: unknown;
}

type Etat = { get(chemin: FieldPath): unknown };
type Verdict = "reserve" | "deja_vu" | "plafond" | "debut_non_notifie";

const lire = (etat: Etat, auteurUid: string, champ: string): unknown => etat.get(new FieldPath("alerts", auteurUid, champ));

/** Liste d'horodatages valides (les valeurs d'un autre type sont ignorees : un document d'etat abime ne bloque jamais une alerte). */
function horodatages(valeur: unknown): Timestamp[] {
  return Array.isArray(valeur) ? valeur.map(enTimestamp).filter((t): t is Timestamp => t !== null) : [];
}

/**
 * Verdict d'un DEBUT : "deja_vu" (livraison en double, ou debut plus ancien que le dernier traite), "plafond" (10 debuts notifies dans les 24 h qui precedent,
 * ou moins d'une minute depuis le dernier) ou "reserve". Identite du debut = startedAt (heure du serveur, unique), PAS seq : une alerte supprimee puis recreee
 * repart a seq 1 mais avec un autre startedAt.
 */
function verdictDebut(etat: Etat, auteurUid: string, startedAt: Timestamp): Verdict {
  const vu = enTimestamp(lire(etat, auteurUid, "lastSeenStartedAt"));
  if (vu && nanos(startedAt) <= nanos(vu)) return "deja_vu";
  const debuts = horodatages(lire(etat, auteurUid, "starts"));
  const dansLaJournee = debuts.filter((t) => startedAt.toMillis() - t.toMillis() < FENETRE_PLAFOND_MS);
  if (dansLaJournee.length >= PLAFOND_DEBUTS) return "plafond";
  const dernier = debuts.reduce((max, t) => Math.max(max, t.toMillis()), Number.NEGATIVE_INFINITY);
  if (startedAt.toMillis() - dernier < INTERVALLE_MIN_DEBUTS_MS) return "plafond";
  return "reserve";
}

/**
 * Verdict d'une FIN : le DEBUT de cette meme alerte (meme startedAt) doit avoir ete notifie (sinon "debut_non_notifie" : plafond silencieux, evenement trop
 * ancien, fonction non deployee a ce moment : aucun message « a mis fin » pour une alerte dont personne n'a recu le debut), et une seule fin par debut.
 */
function verdictFin(etat: Etat, auteurUid: string, startedAt: Timestamp): Verdict {
  const debut = enTimestamp(lire(etat, auteurUid, "lastSeenStartedAt"));
  if (!debut || nanos(debut) !== nanos(startedAt)) return "debut_non_notifie";
  const fin = enTimestamp(lire(etat, auteurUid, "lastEndedStartedAt"));
  if (fin && nanos(fin) === nanos(startedAt)) return "deja_vu";
  return "reserve";
}

const verdictDe = (phase: Phase, etat: Etat, auteurUid: string, startedAt: Timestamp): Verdict =>
  phase === "debut" ? verdictDebut(etat, auteurUid, startedAt) : verdictFin(etat, auteurUid, startedAt);

export async function traiterAlerte(dep: Dependances, evenement: EvenementAlerte): Promise<BilanAlerte> {
  const debut = dep.maintenant().getTime();
  const bilan: BilanAlerte = { phase: evenement.phase, examines: 0, envoyes: 0, ecartes: {}, purges: 0, erreursFcm: {}, lectures: 0, ecritures: 0 };
  try {
    await executer(dep, evenement, bilan);
  } catch {
    // Exception non prevue (Firestore indisponible, bogue...) : aucun detail journalise (le message d'une exception peut contenir un chemin, donc un
    // identifiant) et pas de relance (retry desactive). L'alerte reste visible dans le Feed.
    bilan.erreur = true;
  } finally {
    journaliserBilan(bilan, dep.maintenant().getTime() - debut);
  }
  return bilan;
}

async function executer(dep: Dependances, evenement: EvenementAlerte, bilan: BilanAlerte): Promise<void> {
  const { db, maintenant } = dep;
  const { miffId, auteurUid, phase } = evenement;
  const arreter = (motif: MotifArretAlerte): void => {
    bilan.motif = motif;
  };

  // --- 1. Garde-fous de l'evenement -----------------------------------------------------------------------------------------------
  if (!IDENTIFIANT.test(miffId) || !IDENTIFIANT.test(auteurUid)) return arreter("identifiant_invalide");
  const startedAt = enTimestamp(evenement.startedAt);
  const instantEvenement = phase === "debut" ? startedAt : enTimestamp(evenement.endedAt);
  if (!startedAt || !instantEvenement) return arreter("date_absente");
  const maintenantMs = maintenant().getTime();
  if (maintenantMs - instantEvenement.toMillis() > AGE_MAX_EVENEMENT_MS) return arreter("trop_ancien");

  // --- 2. Pre-controle : deja vu ? plafond ? debut notifie ? (1 lecture) ------------------------------------------------------------------------
  const refEtat = db.doc(cheminEtatAlerte(miffId));
  const etatInitial = await refEtat.get();
  bilan.lectures += 1;
  const verdictInitial = verdictDe(phase, etatInitial, auteurUid, startedAt);
  if (verdictInitial !== "reserve") return arreter(verdictInitial);

  // --- 3. La Miff existe-t-elle encore ? (1 lecture, seuls les droits du createur sont demandes) -------------------------------------------------
  const [miff] = await db.getAll(db.doc(`miffs/${miffId}`), { fieldMask: ["ownerUid"] });
  bilan.lectures += 1;
  if (!miff || !miff.exists) return arreter("miff_absente");

  // --- 4. Fiches de membres : l'auteur et les destinataires ---------------------------------------------------------------------
  // Seul le prenom est demande (le partage de position n'est PAS lu : il n'entre pas dans la decision, D16).
  const fiches = await db.collection(`miffs/${miffId}/members`).select("firstName").limit(MAX_FICHES_ALERTE).get();
  bilan.lectures += Math.max(1, fiches.size);
  let fichesLues = fiches.docs.map((d) => ({ uid: d.id, data: d.data() as { firstName?: unknown } }));
  if (!fichesLues.some((f) => f.uid === auteurUid)) {
    const ficheAuteur = await db.doc(`miffs/${miffId}/members/${auteurUid}`).get();
    bilan.lectures += 1;
    if (!ficheAuteur.exists) return arreter("auteur_absent");
    fichesLues = [...fichesLues, { uid: auteurUid, data: ficheAuteur.data() as { firstName?: unknown } }];
  }
  const auteur = fichesLues.find((f) => f.uid === auteurUid);
  const prenom = prenomAffichable(auteur?.data.firstName);
  if (prenom === "") return arreter("prenom_vide");

  let destinataires = fichesLues.filter((f) => f.uid !== auteurUid && IDENTIFIANT.test(f.uid));
  if (destinataires.length > MAX_DESTINATAIRES_ALERTE) {
    ecarter(bilan, "au_dela_limite", destinataires.length - MAX_DESTINATAIRES_ALERTE);
    destinataires = destinataires.slice(0, MAX_DESTINATAIRES_ALERTE);
  }

  // --- 5. Session active puis appareil de la session (contrat docs/dev/jetons-appareil-clients.md §7) ----------------------------------------------
  // Meme garde que notifierUpdate : un jeton n'est utilise que s'il est celui de l'appareil de la SESSION ACTIVE du destinataire (jamais une liste). Le reglage
  // `notify.updates` n'est PAS consulte (D16 / ticket §11.1).
  const cibles: Cible[] = [];
  const lots = await Promise.all(
    destinataires.map(async (d) => {
      const s = await db.doc(`users/${d.uid}/session/active`).get();
      bilan.lectures += 1;
      const deviceId = s.exists ? (s.data() as SessionActive).deviceId : undefined;
      if (typeof deviceId !== "string" || !IDENTIFIANT.test(deviceId)) return { uid: d.uid, appareil: null, sansSession: true };
      const appareil = await db.doc(`users/${d.uid}/devices/${deviceId}`).get();
      bilan.lectures += 1;
      return { uid: d.uid, appareil, sansSession: false };
    }),
  );
  for (const { uid, appareil, sansSession } of lots) {
    if (sansSession) {
      ecarter(bilan, "sans_session");
      continue;
    }
    if (!appareil || !appareil.exists) {
      ecarter(bilan, "sans_appareil");
      continue;
    }
    bilan.examines += 1;
    const d = appareil.data() as { token?: unknown; platform?: unknown };
    if (typeof d.token !== "string" || d.token.length === 0 || d.token.length > 4096 || (d.platform !== "android" && d.platform !== "ios")) {
      ecarter(bilan, "jeton_illisible");
    } else {
      cibles.push({ uid, token: d.token, plateforme: d.platform, fuseau: undefined, ref: appareil.ref, version: appareil.updateTime as Timestamp });
    }
  }
  if (cibles.length === 0) return arreter("aucun_destinataire");

  // --- 6. Reservation : verification finale dans une transaction sur l'etat (deux executions simultanees : une seule passe) ---------------------
  const issue = await db.runTransaction(async (tx) => {
    const etat = await tx.get(refEtat);
    const verdict = verdictDe(phase, etat, auteurUid, startedAt);
    if (verdict !== "reserve") return verdict;
    if (phase === "debut") {
      const recents = horodatages(lire(etat, auteurUid, "starts")).filter((t) => startedAt.toMillis() - t.toMillis() < FENETRE_PLAFOND_MS);
      const starts = [...recents, startedAt].slice(-PLAFOND_DEBUTS);
      const expireActuel = enTimestamp(etat.get(new FieldPath("expireAt")));
      const expireAt = Timestamp.fromMillis(Math.max(maintenantMs + DUREE_ETAT_ALERTE_MS, expireActuel?.toMillis() ?? 0));
      tx.set(refEtat, { alerts: { [auteurUid]: { lastSeenStartedAt: startedAt, starts } }, expireAt }, { merge: true });
    } else {
      // La fin ne rallonge JAMAIS la duree de vie de l'etat (fixee a la reservation du debut) : elle note seulement qu'une fin a ete notifiee pour ce debut.
      tx.set(refEtat, { alerts: { [auteurUid]: { lastEndedStartedAt: startedAt } } }, { merge: true });
    }
    return verdict;
  });
  bilan.lectures += 1;
  if (issue !== "reserve") return arreter(issue);
  bilan.ecritures += 1;
  if (phase === "fin" && enTimestamp(evenement.endedAt)) bilan.dureeAlerteMin = Math.max(0, Math.round((instantEvenement.toMillis() - startedAt.toMillis()) / 60_000));

  // --- 7. Envoi : un message par appareil ---------------------------------------------------------------------------------------
  const instant = maintenant();
  await Promise.all(
    cibles.map(async (cible) => {
      const message = construireMessageAlerte({
        token: cible.token,
        plateforme: cible.plateforme,
        miffId,
        auteurUid,
        prenom,
        phase,
        maintenant: instant,
      });
      await envoyerAvecReprises(dep, cible, message, bilan);
    }),
  );
}

function journaliserBilan(bilan: BilanAlerte, dureeMs: number): void {
  const base = `notif.alerte.${bilan.phase}`;
  if (bilan.erreur) {
    journaliser("warn", `${base}.erreur`, { resultat: "erreur", lectures: bilan.lectures, ecritures: bilan.ecritures, dureeMs });
    return;
  }
  if (bilan.motif) {
    const champs: ChampsJournal = { resultat: "refuse", motif: bilan.motif, lectures: bilan.lectures, ecritures: bilan.ecritures, dureeMs };
    journaliser("info", `${base}.ignore`, champs);
  } else {
    journaliser("info", `${base}.bilan`, {
      resultat: "ok",
      examines: bilan.examines,
      envoyes: bilan.envoyes,
      ecartes: Object.values(bilan.ecartes).reduce((a, b) => a + b, 0),
      purges: bilan.purges,
      lectures: bilan.lectures,
      ecritures: bilan.ecritures,
      dureeMs,
    });
  }
  if (bilan.dureeAlerteMin !== undefined) journaliser("info", "notif.alerte.fin.duree", { nombre: bilan.dureeAlerteMin });
  for (const [raison, nombre] of Object.entries(bilan.ecartes)) {
    journaliser("info", `notif.alerte.ecart.${raison.replace(/_/g, "-")}`, { nombre });
  }
  for (const [code, nombre] of Object.entries(bilan.erreursFcm)) {
    journaliser("warn", "notif.alerte.fcm-erreur", { resultat: "erreur", code: Number(code), nombre });
  }
}
