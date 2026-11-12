// Traitement d'une publication d'update (SRV-4B, ticket docs/tickets/notification-update-membres.md §5, §7, §10) : qui notifier, une seule fois,
// au plus une fois par auteur, par Miff et par heure. Fonction SANS lien avec le declencheur (notifierUpdate.ts) : Firestore, FCM, horloge et
// attente sont INJECTES, ce qui permet de la tester a l'emulateur avec un faux client FCM, sans compte ni reseau.
//
// Ordre voulu (chaque etape peut s'arreter sans rien ecrire) :
//   1. garde-fous de l'evenement (photo recente, identifiants sains)                                  0 lecture
//   2. PRE-CONTROLE de notifState/{miffId} : deja vu ? plafond horaire ? -> arret, 1 lecture seule      1 lecture
//   3. la Miff existe encore (miffs/{miffId}, 1 lecture) : sinon motif miff_absente, aucun envoi
//   3 bis. fiches de membres (au plus 30) -> auteur partageant, destinataires partageant (20 au plus)      1 lecture par fiche
//   4. SESSION ACTIVE de chaque destinataire, puis get DIRECT de devices/{session.deviceId}                2 lectures par destinataire (1 sans session)
//      -> envoi UNIQUEMENT a l'appareil de la session ; pas de session ou pas de document = pas d'envoi
//   5. reservation transactionnelle dans notifState/{miffId} (verification finale, meme verdict)        1 lecture, 1 ecriture SEULEMENT si on envoie
//   6. envoi FCM par appareil (2 nouvelles tentatives au plus), purge des jetons que FCM dit inconnus
// Budget (Miff de 4 membres, 3 destinataires, 1 appareil chacun) : 1 + 1 + 4 + 3 x 2 + 1 = 13 lectures, 1 ecriture. Update plafonnee ou rejouee : 1 lecture, 0 ecriture.
// "Au plus une fois" : la reservation (etape 5) PRECEDE l'envoi ; un plantage entre les deux, ou une panne de FCM apres la reservation, perd la notification
// et consomme le plafond de l'heure pour cet auteur (le Feed la montre quand meme) ; la fonction n'en double jamais.
// Journaux : uniquement des nombres et des mots fixes (journal.ts), jamais d'identifiant, de prenom ni de jeton.
import { FieldPath, Timestamp, type DocumentReference, type Firestore } from "firebase-admin/firestore";
import type { Message } from "firebase-admin/messaging";
import { journaliser, type ChampsJournal } from "./journal.js";
import {
  AGE_MAX_EVENEMENT_MS,
  DELAIS_REPRISE_MS,
  DUREE_ETAT_MS,
  MAX_DESTINATAIRES,
  MAX_FICHES,
  PLAFOND_FENETRE_MS,
  categoriser,
  codeDeErreur,
  codeNumerique,
  construireMessage,
  estNuit,
  prenomAffichable,
  type Plateforme,
} from "./notifRegles.js";

export interface ClientFcm {
  /** Envoie un message a UN appareil. Rejette avec une erreur portant `code` (ex. "messaging/registration-token-not-registered"). */
  envoyer(message: Message): Promise<void>;
}

export interface Dependances {
  db: Firestore;
  fcm: ClientFcm;
  maintenant: () => Date;
  attendre: (ms: number) => Promise<void>;
}

export interface Evenement {
  miffId: string;
  auteurUid: string;
  /** `createdAt` de photos/{uid} (heure du serveur imposee par les regles Firestore). */
  createdAt: unknown;
}

export type MotifArret = "date_absente" | "trop_ancien" | "identifiant_invalide" | "auteur_absent" | "auteur_sans_partage" | "prenom_vide" | "aucun_destinataire" | "deja_vu" | "plafond" | "miff_absente";

export interface Bilan {
  /** Present si rien n'a ete envoye faute de raison d'envoyer ; absent si au moins une reservation a eu lieu. */
  motif?: MotifArret;
  /** Appareils examines (documents `devices` lus). */
  examines: number;
  envoyes: number;
  /** Appareils ou destinataires ecartes, par raison (sans_partage, sans_session, sans_appareil, notification_coupee, au_dela_limite, jeton_illisible). */
  ecartes: Record<string, number>;
  purges: number;
  /** Erreurs d'envoi par code numerique (notifRegles.codeNumerique). */
  erreursFcm: Record<number, number>;
  lectures: number;
  ecritures: number;
  /** Une exception non prevue a interrompu le traitement (details volontairement non conserves). */
  erreur?: boolean;
}

const IDENTIFIANT = /^[A-Za-z0-9_-]{1,128}$/;

interface Cible {
  uid: string;
  token: string;
  plateforme: Plateforme;
  fuseau: unknown;
  ref: DocumentReference;
  version: Timestamp;
}

const nanos = (t: Timestamp): bigint => BigInt(t.seconds) * 1_000_000_000n + BigInt(t.nanoseconds);

function enTimestamp(valeur: unknown): Timestamp | null {
  if (valeur instanceof Timestamp) return valeur;
  const v = valeur as { seconds?: unknown; nanoseconds?: unknown } | null;
  if (v && typeof v.seconds === "number" && typeof v.nanoseconds === "number") return new Timestamp(v.seconds, v.nanoseconds);
  return null;
}

interface SessionActive {
  deviceId?: unknown;
}

/**
 * Verdict de notifState/{miffId} pour une publication de l'auteur : "deja_vu" (livraison en double, ou evenement plus ancien que le dernier traite),
 * "plafond" (moins d'une heure depuis la derniere notification ENVOYEE pour cet auteur dans cette Miff) ou "reserve" (on peut envoyer).
 * Utilise deux fois : en pre-controle (une simple lecture, pour ne pas lire fiches et appareils d'une update deja ecartee) et dans la transaction finale.
 */
function verdictEtat(etat: { get(chemin: FieldPath): unknown }, auteurUid: string, createdAt: Timestamp): "deja_vu" | "plafond" | "reserve" {
  const vu = enTimestamp(etat.get(new FieldPath("authors", auteurUid, "lastSeenCreatedAt")));
  const notifie = enTimestamp(etat.get(new FieldPath("authors", auteurUid, "lastNotifiedAt")));
  if (vu && nanos(createdAt) <= nanos(vu)) return "deja_vu";
  if (notifie && createdAt.toMillis() - notifie.toMillis() < PLAFOND_FENETRE_MS) return "plafond";
  return "reserve";
}

function ecarter(bilan: Bilan, raison: string, n = 1): void {
  bilan.ecartes[raison] = (bilan.ecartes[raison] ?? 0) + n;
}

export async function traiterPhotoPubliee(dep: Dependances, evenement: Evenement): Promise<Bilan> {
  const debut = dep.maintenant().getTime();
  const bilan: Bilan = { examines: 0, envoyes: 0, ecartes: {}, purges: 0, erreursFcm: {}, lectures: 0, ecritures: 0 };
  try {
    await executer(dep, evenement, bilan);
  } catch {
    // Exception non prevue (Firestore indisponible, bogue...) : on ne journalise AUCUN detail (le message d'une exception peut contenir un chemin, donc un
    // identifiant) et on ne relance pas (retry desactive : relancer ne ferait que reproduire la meme pile, brute, dans les journaux).
    bilan.erreur = true;
  } finally {
    journaliserBilan(bilan, dep.maintenant().getTime() - debut);
  }
  return bilan;
}

async function executer(dep: Dependances, evenement: Evenement, bilan: Bilan): Promise<void> {
  const { db, maintenant } = dep;
  const { miffId, auteurUid } = evenement;
  const arreter = (motif: MotifArret): void => {
    bilan.motif = motif;
  };

  // --- 1. Garde-fous de l'evenement -----------------------------------------------------------------------------------------------
  if (!IDENTIFIANT.test(miffId) || !IDENTIFIANT.test(auteurUid)) return arreter("identifiant_invalide");
  const createdAt = enTimestamp(evenement.createdAt);
  if (!createdAt) return arreter("date_absente");
  const maintenantMs = maintenant().getTime();
  if (maintenantMs - createdAt.toMillis() > AGE_MAX_EVENEMENT_MS) return arreter("trop_ancien");

  // --- 2. Pre-controle : deja vu ? plafond horaire ? (1 lecture ; evite de lire fiches et appareils pour une update deja ecartee) ---------------
  const refEtat = db.doc(`notifState/${miffId}`);
  const etatInitial = await refEtat.get();
  bilan.lectures += 1;
  const verdictInitial = verdictEtat(etatInitial, auteurUid, createdAt);
  if (verdictInitial !== "reserve") return arreter(verdictInitial);

  // --- 3. La Miff existe-t-elle encore ? (1 lecture, seuls les droits du createur sont demandes) -------------------------------------------------
  // Les sous-collections d'une Miff supprimee peuvent subsister (suppression non recursive) : sans Miff, aucune notification, meme si des fiches restent.
  const [miff] = await db.getAll(db.doc(`miffs/${miffId}`), { fieldMask: ["ownerUid"] });
  bilan.lectures += 1;
  if (!miff || !miff.exists) return arreter("miff_absente");

  // --- 4. Fiches de membres : l'auteur et les destinataires ---------------------------------------------------------------------
  // Seuls prenom et "partage actif" sont demandes. Si l'auteur n'est pas dans les 30 premieres fiches (Miff hors norme), une lecture de plus.
  const fiches = await db.collection(`miffs/${miffId}/members`).select("firstName", "sharing.enabled").limit(MAX_FICHES).get();
  bilan.lectures += Math.max(1, fiches.size);
  let fichesLues = fiches.docs.map((d) => ({ uid: d.id, data: d.data() as { firstName?: unknown; sharing?: { enabled?: unknown } } }));
  if (!fichesLues.some((f) => f.uid === auteurUid)) {
    const ficheAuteur = await db.doc(`miffs/${miffId}/members/${auteurUid}`).get();
    bilan.lectures += 1;
    if (!ficheAuteur.exists) return arreter("auteur_absent");
    fichesLues = [...fichesLues, { uid: auteurUid, data: ficheAuteur.data() as { firstName?: unknown; sharing?: { enabled?: unknown } } }];
  }
  const auteur = fichesLues.find((f) => f.uid === auteurUid);
  if (auteur?.data.sharing?.enabled !== true) return arreter("auteur_sans_partage");
  const prenom = prenomAffichable(auteur.data.firstName);
  if (prenom === "") return arreter("prenom_vide");

  let destinataires = fichesLues.filter((f) => f.uid !== auteurUid && IDENTIFIANT.test(f.uid));
  const sansPartage = destinataires.filter((f) => f.data.sharing?.enabled !== true).length;
  if (sansPartage > 0) ecarter(bilan, "sans_partage", sansPartage);
  destinataires = destinataires.filter((f) => f.data.sharing?.enabled === true);
  if (destinataires.length > MAX_DESTINATAIRES) {
    ecarter(bilan, "au_dela_limite", destinataires.length - MAX_DESTINATAIRES);
    destinataires = destinataires.slice(0, MAX_DESTINATAIRES);
  }

  // --- 5. Session active puis appareil de la session (contrat docs/dev/jetons-appareil-clients.md §7) ----------------------------------------------
  // Pour chaque destinataire : (a) users/{uid}/session/active ; absente = AUCUN envoi a ce compte ; (b) lecture DIRECTE (get) de users/{uid}/devices/{session.deviceId},
  // jamais une liste : les jetons d'autres identifiants (appareil remplace, deconnexion hors connexion restee sans suppression) ne sont ni lus ni utilises.
  // Raison : un jeton reste valide chez FCM meme si son document n'a pas pu etre supprime ; un autre compte qui s'ouvre ensuite sur le meme telephone recevrait
  // sinon le prenom d'un membre d'un cercle qui n'est pas le sien. Document absent = aucun envoi. 1 lecture sans session, 2 lectures avec.
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
    const d = appareil.data() as { token?: unknown; platform?: unknown; tz?: unknown; notify?: { updates?: unknown } };
    if (d.notify?.updates !== true) {
      ecarter(bilan, "notification_coupee");
    } else if (typeof d.token !== "string" || d.token.length === 0 || d.token.length > 4096 || (d.platform !== "android" && d.platform !== "ios")) {
      ecarter(bilan, "jeton_illisible");
    } else {
      cibles.push({ uid, token: d.token, plateforme: d.platform, fuseau: d.tz, ref: appareil.ref, version: appareil.updateTime as Timestamp });
    }
  }
  if (cibles.length === 0) return arreter("aucun_destinataire");

  // --- 6. Reservation : verification finale dans une transaction sur notifState/{miffId} (deux executions simultanees : une seule passe) -------
  const issue = await db.runTransaction(async (tx) => {
    const verdict = verdictEtat(await tx.get(refEtat), auteurUid, createdAt);
    if (verdict !== "reserve") return verdict;
    tx.set(
      refEtat,
      { authors: { [auteurUid]: { lastSeenCreatedAt: createdAt, lastNotifiedAt: createdAt } }, expireAt: Timestamp.fromMillis(maintenantMs + DUREE_ETAT_MS) },
      { merge: true },
    );
    return verdict;
  });
  bilan.lectures += 1;
  if (issue !== "reserve") return arreter(issue);
  bilan.ecritures += 1;

  // --- 7. Envoi : un message par appareil ---------------------------------------------------------------------------------------
  const instant = maintenant();
  await Promise.all(
    cibles.map(async (cible) => {
      const message = construireMessage({
        token: cible.token,
        plateforme: cible.plateforme,
        miffId,
        auteurUid,
        prenom,
        nuit: estNuit(cible.fuseau, instant),
        maintenant: instant,
      });
      await envoyerAvecReprises(dep, cible, message, bilan);
    }),
  );
}

async function envoyerAvecReprises(dep: Dependances, cible: Cible, message: Message, bilan: Bilan): Promise<void> {
  for (let tentative = 0; ; tentative++) {
    try {
      await dep.fcm.envoyer(message);
      bilan.envoyes += 1;
      return;
    } catch (erreur) {
      const code = codeDeErreur(erreur);
      const categorie = categoriser(code);
      if (categorie === "passager" && tentative < DELAIS_REPRISE_MS.length) {
        await dep.attendre(DELAIS_REPRISE_MS[tentative] as number);
        continue;
      }
      const numero = codeNumerique(code);
      bilan.erreursFcm[numero] = (bilan.erreursFcm[numero] ?? 0) + 1;
      if (categorie === "jeton_invalide") await purger(cible, bilan);
      return;
    }
  }
}

/** Supprime le document de l'appareil SEULEMENT s'il n'a pas change depuis la lecture (un jeton renouvele entre-temps est conserve). */
async function purger(cible: Cible, bilan: Bilan): Promise<void> {
  try {
    await cible.ref.delete({ lastUpdateTime: cible.version });
    bilan.purges += 1;
    bilan.ecritures += 1;
  } catch {
    // Document deja supprime ou renouvele : rien a faire.
  }
}

function journaliserBilan(bilan: Bilan, dureeMs: number): void {
  if (bilan.erreur) {
    journaliser("warn", "notif.update.erreur", { resultat: "erreur", lectures: bilan.lectures, ecritures: bilan.ecritures, dureeMs });
    return;
  }
  if (bilan.motif) {
    const champs: ChampsJournal = { resultat: "refuse", motif: bilan.motif, lectures: bilan.lectures, ecritures: bilan.ecritures, dureeMs };
    journaliser("info", "notif.update.ignore", champs);
  } else {
    journaliser("info", "notif.update.bilan", {
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
  for (const [raison, nombre] of Object.entries(bilan.ecartes)) {
    journaliser("info", `notif.update.ecart.${raison.replace(/_/g, "-")}`, { nombre });
  }
  for (const [code, nombre] of Object.entries(bilan.erreursFcm)) {
    journaliser("warn", "notif.update.fcm-erreur", { resultat: "erreur", code: Number(code), nombre });
  }
}
